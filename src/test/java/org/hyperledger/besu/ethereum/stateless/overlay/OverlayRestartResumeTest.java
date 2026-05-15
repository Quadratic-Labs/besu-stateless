/*
 * Copyright Hyperledger Besu Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */
package org.hyperledger.besu.ethereum.stateless.overlay;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.stateless.overlay.migration.MigrationProgress;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;

/** Restart and crash-replay behaviour of the transition. */
public class OverlayRestartResumeTest {

  private static final Bytes CONTRACT = Bytes.fromHexString("0x" + "bb".repeat(20));
  private static final Bytes CODE = Bytes.repeat((byte) 0x01, 100);

  private OverlayTransitionFixture newFixture() {
    OverlayTransitionFixture fixture = new OverlayTransitionFixture();
    for (int i = 1; i <= 6; i++) {
      final Bytes address = Bytes.fromHexString("0x" + String.format("%02x", i).repeat(20));
      final int index = i;
      fixture.preFork(w -> w.putAccount(address, index, UInt256.valueOf(index * 10L)));
    }
    fixture.preFork(
        w -> {
          w.putAccount(CONTRACT, 1, UInt256.ONE);
          w.putCode(CONTRACT, CODE);
          w.putStorage(CONTRACT, UInt256.valueOf(9), UInt256.valueOf(99));
          w.putStorage(CONTRACT, UInt256.valueOf(9000), UInt256.valueOf(88));
        });
    return fixture;
  }

  private static void midTransitionWrite(WorldStateWriter w) {
    w.putAccount(CONTRACT, 2, UInt256.valueOf(456));
  }

  @Test
  public void testRestartMidTransitionProducesSameRootsAsUninterruptedRun() {
    // uninterrupted reference run
    OverlayTransitionFixture uninterrupted = newFixture();
    uninterrupted.startTransitionAuto(0, 2, 2);
    uninterrupted.runBlock(OverlayRestartResumeTest::midTransitionWrite);
    BlockCommitResult reference = uninterrupted.runToCutover();

    // interrupted run: same workload, but rebuild everything from the stores halfway through
    OverlayTransitionFixture fixture = newFixture();
    fixture.startTransitionAuto(0, 2, 2);
    fixture.runBlock(OverlayRestartResumeTest::midTransitionWrite);
    fixture.runEmptyBlock();

    fixture.restartOverlay(); // rebuild MPT, bintrie and migration frontier from persistence

    BlockCommitResult result = fixture.runToCutover();
    assertThat(result.binTrieRootHash()).isEqualTo(reference.binTrieRootHash());
    assertThat(result.mptRootHash()).isEqualTo(reference.mptRootHash());
    assertThat(fixture.overlay.getBinTrieRootHash()).isEqualTo(fixture.referenceRoot());
  }

  @Test
  public void testCrashBeforeProgressSaveReplaysChunkIdempotently() {
    OverlayTransitionFixture fixture = newFixture();
    fixture.startTransitionAuto(0, 2, 3);

    // run two blocks normally
    fixture.runBlock(OverlayRestartResumeTest::midTransitionWrite);
    MigrationProgress progressBeforeCrashBlock =
        fixture.progressStore.load().orElse(MigrationProgress.zero());
    BlockCommitResult crashBlock = fixture.runEmptyBlock();

    // simulate a crash after the tries were committed but before the progress was saved:
    // roll the progress store back to the pre-block value and restart
    fixture.progressStore.save(progressBeforeCrashBlock);
    fixture.restartOverlay();
    fixture.nextBlock = crashBlock.blockNumber(); // the crashed block is re-processed

    BlockCommitResult replayed = fixture.runEmptyBlock();
    assertThat(replayed.binTrieRootHash()).isEqualTo(crashBlock.binTrieRootHash());
    assertThat(replayed.mptRootHash()).isEqualTo(crashBlock.mptRootHash());

    // and the rest of the transition still converges to the reference root
    fixture.runToCutover();
    assertThat(fixture.overlay.getBinTrieRootHash()).isEqualTo(fixture.referenceRoot());
  }

  @Test
  public void testRestartAfterCutover() {
    OverlayTransitionFixture fixture = newFixture();
    fixture.startTransitionAuto(0, 3, 2);
    fixture.runBlock(OverlayRestartResumeTest::midTransitionWrite);
    BlockCommitResult cutover = fixture.runToCutover();
    Bytes32 binRoot = cutover.binTrieRootHash();

    fixture.restartOverlay();
    fixture.overlay.beginBlock(fixture.nextBlock); // POST phase: must not throw
    fixture.overlay.putAccount(CONTRACT, 3, UInt256.valueOf(789));
    fixture.model.putAccount(CONTRACT, 3, UInt256.valueOf(789));
    fixture.overlay.endBlock(fixture.mptStore, fixture.binStore);

    assertThat(fixture.overlay.getPhase()).isEqualTo(TransitionPhase.POST_TRANSITION);
    assertThat(fixture.overlay.getBinTrieRootHash()).isNotEqualTo(binRoot);
    assertThat(fixture.overlay.getBinTrieRootHash()).isEqualTo(fixture.referenceRoot());
  }
}
