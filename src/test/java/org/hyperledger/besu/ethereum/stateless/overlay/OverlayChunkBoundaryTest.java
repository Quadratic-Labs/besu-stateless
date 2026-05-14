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

/** Chunk cadence and boundary behaviour of the background migration. */
public class OverlayChunkBoundaryTest {

  private static final Bytes CONTRACT = Bytes.fromHexString("0x" + "bb".repeat(20));
  private static final Bytes CODE = Bytes.repeat((byte) 0x01, 100); // 4 chunks

  private OverlayTransitionFixture fixture;

  @BeforeEach
  public void setUp() {
    fixture = new OverlayTransitionFixture();
    // several accounts so the enumeration has multiple stems
    for (int i = 1; i <= 5; i++) {
      final Bytes address = Bytes.fromHexString("0x" + String.format("%02x", i).repeat(20));
      final int index = i;
      fixture.preFork(w -> w.putAccount(address, index, UInt256.valueOf(index * 100L)));
    }
    fixture.preFork(
        w -> {
          w.putAccount(CONTRACT, 1, UInt256.ONE);
          w.putCode(CONTRACT, CODE);
          w.putStorage(CONTRACT, UInt256.valueOf(5), UInt256.valueOf(55));
          w.putStorage(CONTRACT, UInt256.valueOf(5000), UInt256.valueOf(66));
        });
  }

  @Test
  public void testOneStemPerBlock() {
    fixture.startTransitionAuto(0, 1, 0);
    long total = fixture.source.totalUnits();
    long migrated = 0;
    while (fixture.nextBlock < fixture.config.targetBlock()) {
      BlockCommitResult result = fixture.runEmptyBlock();
      assertThat(result.stemsMigrated()).isLessThanOrEqualTo(1);
      migrated += result.stemsMigrated();
    }
    assertThat(migrated).isEqualTo(total);
    fixture.runToCutover();
    assertThat(fixture.overlay.getBinTrieRootHash()).isEqualTo(fixture.referenceRoot());
  }

  @Test
  public void testOneShotMigration() {
    fixture.startTransitionAuto(0, 10_000, 0);
    BlockCommitResult first = fixture.runEmptyBlock();
    assertThat(first.stemsMigrated()).isEqualTo(fixture.source.totalUnits());
    assertThat(first.migrationComplete()).isTrue();
    fixture.runToCutover();
    assertThat(fixture.overlay.getBinTrieRootHash()).isEqualTo(fixture.referenceRoot());
  }

  @Test
  public void testLastChunkSmallerThanCadence() {
    fixture.startTransitionAuto(0, 4, 0);
    long total = fixture.source.totalUnits();
    assertThat(total % 4).isNotEqualTo(0L); // ensure the boundary case is exercised
    BlockCommitResult last = null;
    long migrated = 0;
    while (fixture.nextBlock < fixture.config.targetBlock()) {
      last = fixture.runEmptyBlock();
      migrated += last.stemsMigrated();
    }
    assertThat(migrated).isEqualTo(total);
    assertThat(last.stemsMigrated()).isEqualTo((int) (total % 4));
  }

  @Test
  public void testSkippedStemsStillConsumeQuota() {
    fixture.startTransitionAuto(0, 1, 5);
    long total = fixture.source.totalUnits();
    // delete the contract in the first block: all its enumerated stems now resolve to nothing
    fixture.runBlock(
        w -> w.removeAccount(CONTRACT, List.of(UInt256.valueOf(5), UInt256.valueOf(5000)), CODE));
    long migrated = 1; // first block already migrated one stem
    while (!fixture.overlay.getMigrationProgress().complete()) {
      migrated += fixture.runEmptyBlock().stemsMigrated();
    }
    // every unit consumed quota even though the contract's units produced no leaves
    assertThat(migrated).isEqualTo(total);
    assertThat(fixture.overlay.getMigrationProgress().frontier()).isEqualTo(total);
    fixture.runToCutover();
    assertThat(fixture.overlay.getBinTrieRootHash()).isEqualTo(fixture.referenceRoot());
  }

  @Test
  public void testAccountSplitAcrossChunksWithInterleavedWrite() {
    // K=1: the contract's header stem, storage stem and code-chunk stems are migrated in
    // different blocks, with a write to the same account in between
    fixture.startTransitionAuto(0, 1, 4);
    fixture.runEmptyBlock();
    fixture.runBlock(
        w -> {
          w.putAccount(CONTRACT, 2, UInt256.valueOf(123));
          w.putStorage(CONTRACT, UInt256.valueOf(5), UInt256.valueOf(555));
        });
    fixture.runBlock(w -> w.putStorage(CONTRACT, UInt256.valueOf(5000), UInt256.ZERO));
    fixture.runToCutover();
    assertThat(fixture.overlay.getBinTrieRootHash()).isEqualTo(fixture.referenceRoot());
  }
}
