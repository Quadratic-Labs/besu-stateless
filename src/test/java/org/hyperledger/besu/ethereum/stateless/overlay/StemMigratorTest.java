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

import org.hyperledger.besu.ethereum.stateless.bintrie.adapter.TrieKeyFactory;
import org.hyperledger.besu.ethereum.stateless.bintrie.adapter.TrieKeyUtils;
import org.hyperledger.besu.ethereum.stateless.bintrie.hasher.StemHasher;
import org.hyperledger.besu.ethereum.stateless.overlay.bintrie.BinTrieWorldState;
import org.hyperledger.besu.ethereum.stateless.overlay.migration.LeafSource;
import org.hyperledger.besu.ethereum.stateless.overlay.migration.MigratedLeaf;
import org.hyperledger.besu.ethereum.stateless.overlay.migration.StemMigrationUnit;
import org.hyperledger.besu.ethereum.stateless.overlay.migration.StemMigrator;
import org.hyperledger.besu.ethereum.stateless.overlay.mpt.Keccak;
import org.hyperledger.besu.ethereum.stateless.overlay.mpt.MptWorldState;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class StemMigratorTest {

  private static final Bytes ADDRESS = Bytes.fromHexString("0x" + "11".repeat(20));
  private static final Bytes CODE = Bytes.fromHexString("0x6001600101"); // 5 bytes, 1 chunk
  // 64 bytes of plain opcodes -> 3 chunks
  private static final Bytes LONG_CODE = Bytes.repeat((byte) 0x01, 64);

  private TrieKeyFactory keyFactory;
  private StemMigrator migrator;
  private MptWorldState mpt;

  @BeforeEach
  public void setUp() {
    keyFactory = new TrieKeyFactory(new StemHasher());
    migrator = new StemMigrator(keyFactory);
    mpt = new MptWorldState(new NodeStoreMock(), new InMemoryCodeStore());
  }

  private StemMigrationUnit headerUnit(final List<LeafSource> members) {
    return new StemMigrationUnit(keyFactory.getHeaderStem(ADDRESS), members);
  }

  @Test
  public void testAbsentAccountProducesNoLeaves() {
    StemMigrationUnit unit = headerUnit(List.of(new LeafSource.AccountHeader(ADDRESS)));
    assertThat(migrator.migrate(unit, mpt)).isEmpty();
  }

  @Test
  public void testAccountHeaderResolvesCurrentValues() {
    mpt.putAccount(ADDRESS, 5, UInt256.valueOf(500));
    mpt.putCode(ADDRESS, CODE);

    StemMigrationUnit unit = headerUnit(List.of(new LeafSource.AccountHeader(ADDRESS)));
    List<MigratedLeaf> leaves = migrator.migrate(unit, mpt);

    assertThat(leaves).hasSize(2);
    assertThat(leaves.get(0).key()).isEqualTo(keyFactory.basicDataKey(ADDRESS));
    assertThat(leaves.get(0).value())
        .isEqualTo(BinTrieWorldState.encodeBasicData(5, UInt256.valueOf(500), CODE.size()));
    assertThat(leaves.get(1).key()).isEqualTo(keyFactory.codeHashKey(ADDRESS));
    assertThat(leaves.get(1).value()).isEqualTo(Keccak.keccak256(CODE));
  }

  @Test
  public void testAbsentSlotSkippedOtherMembersMigrated() {
    mpt.putAccount(ADDRESS, 0, UInt256.ONE);
    mpt.putStorage(ADDRESS, UInt256.valueOf(1), UInt256.valueOf(11));
    // slots 1 and 2 are header-stem slots (< 64); slot 2 was never written
    StemMigrationUnit unit =
        headerUnit(
            List.of(
                new LeafSource.AccountHeader(ADDRESS),
                new LeafSource.StorageSlot(ADDRESS, UInt256.valueOf(1)),
                new LeafSource.StorageSlot(ADDRESS, UInt256.valueOf(2))));

    List<MigratedLeaf> leaves = migrator.migrate(unit, mpt);
    assertThat(leaves).hasSize(3); // basicData + codeHash + slot 1
    assertThat(leaves.get(2).key()).isEqualTo(keyFactory.storageKey(ADDRESS, UInt256.valueOf(1)));
    assertThat(leaves.get(2).value()).isEqualTo(UInt256.valueOf(11).toBytes());
  }

  @Test
  public void testCodeChunkBeyondCurrentCodeSkipped() {
    mpt.putAccount(ADDRESS, 0, UInt256.ONE);
    mpt.putCode(ADDRESS, CODE); // 1 chunk: chunk id 2 does not exist any more

    StemMigrationUnit unit =
        new StemMigrationUnit(
            keyFactory.getCodeChunkStem(ADDRESS, UInt256.valueOf(2)),
            List.of(new LeafSource.CodeChunk(ADDRESS, UInt256.valueOf(2))));
    // chunk 2 is also in the header stem (chunk < 128), so build unit from its actual stem
    assertThat(migrator.migrate(unit, mpt)).isEmpty();
  }

  @Test
  public void testCodeChunkResolvesFromCurrentCode() {
    mpt.putAccount(ADDRESS, 0, UInt256.ONE);
    mpt.putCode(ADDRESS, LONG_CODE);

    StemMigrationUnit unit =
        new StemMigrationUnit(
            keyFactory.getCodeChunkStem(ADDRESS, UInt256.valueOf(1)),
            List.of(new LeafSource.CodeChunk(ADDRESS, UInt256.valueOf(1))));

    List<MigratedLeaf> leaves = migrator.migrate(unit, mpt);
    assertThat(leaves).hasSize(1);
    assertThat(leaves.get(0).value())
        .isEqualTo(TrieKeyUtils.chunkifyCode(LONG_CODE).get(1).toBytes());
  }

  @Test
  public void testEmptyCodeChunkSkipped() {
    mpt.putAccount(ADDRESS, 0, UInt256.ONE); // no code

    StemMigrationUnit unit =
        new StemMigrationUnit(
            keyFactory.getCodeChunkStem(ADDRESS, UInt256.ZERO),
            List.of(new LeafSource.CodeChunk(ADDRESS, UInt256.ZERO)));
    assertThat(migrator.migrate(unit, mpt)).isEmpty();
  }

  @Test
  public void testMainStorageSlotResolves() {
    mpt.putAccount(ADDRESS, 0, UInt256.ONE);
    UInt256 slot = UInt256.valueOf(1000); // above header storage: dedicated stem
    mpt.putStorage(ADDRESS, slot, UInt256.valueOf(77));

    StemMigrationUnit unit =
        new StemMigrationUnit(
            keyFactory.getStorageStem(ADDRESS, slot),
            List.of(new LeafSource.StorageSlot(ADDRESS, slot)));

    List<MigratedLeaf> leaves = migrator.migrate(unit, mpt);
    assertThat(leaves).hasSize(1);
    assertThat(leaves.get(0).key()).isEqualTo(keyFactory.storageKey(ADDRESS, slot));
  }

  @Test
  public void testMemberOfWrongStemThrows() {
    mpt.putAccount(ADDRESS, 0, UInt256.ONE);
    UInt256 slot = UInt256.valueOf(1000);
    StemMigrationUnit unit =
        headerUnit(List.of(new LeafSource.StorageSlot(ADDRESS, slot))); // wrong stem

    assertThatThrownBy(() -> migrator.migrate(unit, mpt))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
