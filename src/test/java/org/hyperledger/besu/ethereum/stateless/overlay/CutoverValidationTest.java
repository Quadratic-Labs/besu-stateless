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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Cutover validation and block lifecycle guards of {@link OverlayWorldState}. */
public class CutoverValidationTest {

  private static final Bytes EOA = Bytes.fromHexString("0x" + "aa".repeat(20));

  private OverlayTransitionFixture fixture;

  @BeforeEach
  public void setUp() {
    fixture = new OverlayTransitionFixture();
    for (int i = 1; i <= 4; i++) {
      final Bytes address = Bytes.fromHexString("0x" + String.format("%02x", i).repeat(20));
      final int index = i;
      fixture.preFork(w -> w.putAccount(address, index, UInt256.valueOf(index)));
    }
  }

  @Test
  public void testIncompleteMigrationAtTargetThrows() {
    // 4 stems to migrate but only 2 transition blocks at 1 stem per block
    fixture.startTransition(0, 2, 1);
    fixture.runEmptyBlock();
    fixture.runEmptyBlock();
    assertThatThrownBy(() -> fixture.overlay.beginBlock(2))
        .isInstanceOf(TransitionIncompleteException.class)
        .hasMessageContaining("2 stem units remaining");
  }

  @Test
  public void testExactlyCompleteMigrationCutsOver() {
    fixture.startTransition(0, 4, 1); // 4 stems, 4 blocks, exact fit
    BlockCommitResult result = fixture.runToCutover();
    assertThat(result.phase()).isEqualTo(TransitionPhase.POST_TRANSITION);
    assertThat(result.migrationComplete()).isTrue();
    assertThat(fixture.overlay.getBinTrieRootHash()).isEqualTo(fixture.referenceRoot());
  }

  @Test
  public void testNonConsecutiveBlockThrows() {
    fixture.startTransitionAuto(0, 10, 2);
    fixture.runEmptyBlock();
    assertThatThrownBy(() -> fixture.overlay.beginBlock(5))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Non-consecutive");
  }

  @Test
  public void testBeginWhileBlockOpenThrows() {
    fixture.startTransitionAuto(0, 10, 2);
    fixture.overlay.beginBlock(0);
    assertThatThrownBy(() -> fixture.overlay.beginBlock(1))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("still open");
  }

  @Test
  public void testEndBlockWithoutBeginThrows() {
    fixture.startTransitionAuto(0, 10, 2);
    assertThatThrownBy(() -> fixture.overlay.endBlock(fixture.mptStore, fixture.binStore))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No block is open");
  }

  @Test
  public void testPreTransitionBlocksRouteToMptOnly() {
    OverlayTransitionFixture f = new OverlayTransitionFixture();
    f.preFork(w -> w.putAccount(EOA, 1, UInt256.ONE));
    f.startTransition(3, 10, 100);
    Bytes32 emptyBinRoot = f.overlay.getBinTrieRootHash();

    f.nextBlock = 0; // blocks 0..2 are before the start block
    BlockCommitResult result = f.runBlock(w -> w.putAccount(EOA, 2, UInt256.valueOf(20)));
    assertThat(result.phase()).isEqualTo(TransitionPhase.PRE_TRANSITION);
    assertThat(result.stemsMigrated()).isEqualTo(0);
    // the write reached the MPT but not the binary trie
    assertThat(f.mpt.getAccount(EOA).orElseThrow().nonce()).isEqualTo(2);
    assertThat(f.overlay.getBinTrieRootHash()).isEqualTo(emptyBinRoot);

    // entering the transition at block 3 starts double-writing
    f.runEmptyBlock(); // block 1
    f.runEmptyBlock(); // block 2
    BlockCommitResult transition = f.runBlock(w -> w.putAccount(EOA, 3, UInt256.valueOf(30)));
    assertThat(transition.phase()).isEqualTo(TransitionPhase.TRANSITION);
    assertThat(f.overlay.getBinTrieRootHash()).isNotEqualTo(emptyBinRoot);
  }
}
