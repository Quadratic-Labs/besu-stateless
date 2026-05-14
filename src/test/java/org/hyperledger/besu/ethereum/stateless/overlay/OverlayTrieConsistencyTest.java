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

import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * One scripted scenario per transition edge case; each ends by asserting that the binary trie root
 * at cutover equals the ground-truth root built directly from the final semantic state.
 */
public class OverlayTrieConsistencyTest {

  private static final Bytes EOA = Bytes.fromHexString("0x" + "aa".repeat(20));
  private static final Bytes CONTRACT = Bytes.fromHexString("0x" + "bb".repeat(20));
  private static final Bytes NEW_ACCOUNT = Bytes.fromHexString("0x" + "cc".repeat(20));
  private static final Bytes CODE = Bytes.fromHexString("0x6001600101");
  private static final Bytes LONG_CODE = Bytes.repeat((byte) 0x01, 100); // 4 chunks
  private static final Bytes SHORT_CODE = Bytes.fromHexString("0x00"); // 1 chunk

  private OverlayTransitionFixture fixture;

  @BeforeEach
  public void setUp() {
    fixture = new OverlayTransitionFixture();
    fixture.preFork(w -> w.putAccount(EOA, 1, UInt256.valueOf(100)));
    fixture.preFork(
        w -> {
          w.putAccount(CONTRACT, 1, UInt256.valueOf(200));
          w.putCode(CONTRACT, CODE);
          w.putStorage(CONTRACT, UInt256.valueOf(3), UInt256.valueOf(33)); // header stem slot
          w.putStorage(CONTRACT, UInt256.valueOf(1000), UInt256.valueOf(44)); // own stem
        });
  }

  private void assertCutoverMatchesReference() {
    fixture.runToCutover();
    assertThat(fixture.overlay.getPhase()).isEqualTo(TransitionPhase.POST_TRANSITION);
    assertThat(fixture.overlay.getBinTrieRootHash()).isEqualTo(fixture.referenceRoot());
  }

  @Test
  public void testAccountModifiedBeforeItsStemIsMigrated() {
    fixture.startTransitionAuto(10, 1, 2);
    fixture.runBlock(w -> w.putAccount(EOA, 2, UInt256.valueOf(150)));
    assertCutoverMatchesReference();
  }

  @Test
  public void testAccountModifiedAfterFullMigration() {
    fixture.startTransitionAuto(10, 100, 3); // one-shot migration in the first block
    fixture.runEmptyBlock();
    fixture.runBlock(w -> w.putAccount(EOA, 5, UInt256.valueOf(700)));
    assertCutoverMatchesReference();
  }

  @Test
  public void testDeletedAccountIsNotResurrected() {
    fixture.startTransitionAuto(10, 1, 2);
    fixture.runBlock(
        w -> w.removeAccount(CONTRACT, List.of(UInt256.valueOf(3), UInt256.valueOf(1000)), CODE));
    assertCutoverMatchesReference();
  }

  @Test
  public void testDeletedStorageSlotIsNotResurrected() {
    fixture.startTransitionAuto(10, 1, 2);
    fixture.runBlock(w -> w.putStorage(CONTRACT, UInt256.valueOf(1000), UInt256.ZERO));
    assertCutoverMatchesReference();
  }

  @Test
  public void testAccountCreatedPostFork() {
    fixture.startTransitionAuto(10, 1, 3);
    fixture.runBlock(
        w -> {
          w.putAccount(NEW_ACCOUNT, 1, UInt256.valueOf(42));
          w.putCode(NEW_ACCOUNT, CODE);
          w.putStorage(NEW_ACCOUNT, UInt256.valueOf(2000), UInt256.valueOf(5));
        });
    assertCutoverMatchesReference();
  }

  @Test
  public void testDeleteThenRecreateBeforeMigration() {
    fixture.startTransitionAuto(10, 1, 3);
    fixture.runBlock(
        w -> w.removeAccount(CONTRACT, List.of(UInt256.valueOf(3), UInt256.valueOf(1000)), CODE));
    fixture.runBlock(
        w -> {
          w.putAccount(CONTRACT, 1, UInt256.valueOf(9));
          w.putCode(CONTRACT, SHORT_CODE);
        });
    assertCutoverMatchesReference();
  }

  @Test
  public void testCodeReplacedWithLongerCode() {
    fixture.startTransitionAuto(10, 1, 2);
    fixture.runBlock(w -> w.putCode(CONTRACT, LONG_CODE));
    assertCutoverMatchesReference();
  }

  @Test
  public void testCodeReplacedWithShorterCode() {
    fixture.preFork(w -> w.putCode(CONTRACT, LONG_CODE));
    fixture.startTransitionAuto(10, 1, 2);
    fixture.runBlock(w -> w.putCode(CONTRACT, SHORT_CODE));
    assertCutoverMatchesReference();
  }

  @Test
  public void testCodeReplacedTwicePostFork() {
    // long code double-written post-fork, then shortened: stale chunks must disappear
    fixture.startTransitionAuto(10, 1, 3);
    fixture.runBlock(w -> w.putCode(CONTRACT, LONG_CODE));
    fixture.runBlock(w -> w.putCode(CONTRACT, SHORT_CODE));
    assertCutoverMatchesReference();
  }

  @Test
  public void testSlotWrittenThenZeroedWithinWindow() {
    fixture.startTransitionAuto(10, 1, 3);
    fixture.runBlock(w -> w.putStorage(EOA, UInt256.valueOf(7), UInt256.valueOf(77)));
    fixture.runBlock(w -> w.putStorage(EOA, UInt256.valueOf(7), UInt256.ZERO));
    assertCutoverMatchesReference();
  }

  @Test
  public void testDeleteAfterMigrationRemovesFromBinTrie() {
    fixture.startTransitionAuto(10, 100, 3); // everything migrated in the first block
    fixture.runEmptyBlock();
    fixture.runBlock(
        w -> w.removeAccount(CONTRACT, List.of(UInt256.valueOf(3), UInt256.valueOf(1000)), CODE));
    assertCutoverMatchesReference();
  }

  @Test
  public void testPostCutoverWritesGoToBinTrieOnly() {
    fixture.startTransitionAuto(10, 1, 2);
    BlockCommitResult cutover = fixture.runToCutover();
    assertThat(cutover.phase()).isEqualTo(TransitionPhase.POST_TRANSITION);
    fixture.runBlock(w -> w.putAccount(EOA, 9, UInt256.valueOf(999)));
    assertThat(fixture.overlay.getMptRootHash()).isEqualTo(cutover.mptRootHash());
    assertThat(fixture.overlay.getBinTrieRootHash()).isEqualTo(fixture.referenceRoot());
    assertThat(fixture.overlay.getAccount(EOA).orElseThrow().nonce()).isEqualTo(9);
  }
}
