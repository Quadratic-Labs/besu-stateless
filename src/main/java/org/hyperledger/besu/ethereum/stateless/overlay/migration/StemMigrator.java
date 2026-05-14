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
package org.hyperledger.besu.ethereum.stateless.overlay.migration;

import org.hyperledger.besu.ethereum.stateless.bintrie.BytesBitSequence;
import org.hyperledger.besu.ethereum.stateless.bintrie.adapter.TrieKeyFactory;
import org.hyperledger.besu.ethereum.stateless.bintrie.adapter.TrieKeyUtils;
import org.hyperledger.besu.ethereum.stateless.overlay.AccountState;
import org.hyperledger.besu.ethereum.stateless.overlay.WorldStateReader;
import org.hyperledger.besu.ethereum.stateless.overlay.bintrie.BinTrieWorldState;
import org.hyperledger.besu.ethereum.stateless.overlay.mpt.MptAccount;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * Resolves the current values of a {@link StemMigrationUnit} against the live Merkle Patricia world
 * state and encodes them as binary trie leaves.
 *
 * <p>Resolution rules: values are always read from the current world state, never from the snapshot
 * that produced the enumeration; entries that no longer exist (deleted account, cleared slot, code
 * chunk beyond the current code length) are skipped, never deleted — deletions reach the binary
 * trie exclusively through double-writes.
 */
public class StemMigrator {

  private final TrieKeyFactory keyFactory;
  private final Map<Bytes32, List<UInt256>> chunkedCodeByHash = new HashMap<>();

  /**
   * Creates a migrator using the given key derivation.
   *
   * @param keyFactory The binary trie key factory.
   */
  public StemMigrator(final TrieKeyFactory keyFactory) {
    this.keyFactory = keyFactory;
  }

  /**
   * Resolves and encodes all leaves of a stem unit.
   *
   * @param unit The stem unit to migrate.
   * @param worldState The live world state to resolve current values from.
   * @return The leaves to insert into the binary trie; skipped members produce no leaf.
   */
  public List<MigratedLeaf> migrate(
      final StemMigrationUnit unit, final WorldStateReader worldState) {
    final List<MigratedLeaf> leaves = new ArrayList<>();
    for (final LeafSource member : unit.members()) {
      switch (member) {
        case LeafSource.AccountHeader header -> migrateAccountHeader(
            unit, header, worldState, leaves);
        case LeafSource.StorageSlot slot -> migrateStorageSlot(unit, slot, worldState, leaves);
        case LeafSource.CodeChunk chunk -> migrateCodeChunk(unit, chunk, worldState, leaves);
      }
    }
    return leaves;
  }

  private void migrateAccountHeader(
      final StemMigrationUnit unit,
      final LeafSource.AccountHeader member,
      final WorldStateReader worldState,
      final List<MigratedLeaf> leaves) {
    checkStem(unit, keyFactory.getHeaderStem(member.address()), member);
    final Optional<AccountState> account = worldState.getAccount(member.address());
    if (account.isEmpty()) {
      return; // account deleted since the snapshot: nothing to migrate
    }
    leaves.add(
        new MigratedLeaf(
            keyFactory.basicDataKey(member.address()),
            BinTrieWorldState.encodeBasicData(
                account.get().nonce(), account.get().balance(), account.get().codeSize())));
    leaves.add(
        new MigratedLeaf(keyFactory.codeHashKey(member.address()), account.get().codeHash()));
  }

  private void migrateStorageSlot(
      final StemMigrationUnit unit,
      final LeafSource.StorageSlot member,
      final WorldStateReader worldState,
      final List<MigratedLeaf> leaves) {
    checkStem(unit, keyFactory.getStorageStem(member.address(), member.slot()), member);
    worldState
        .getStorage(member.address(), member.slot())
        .ifPresent(
            value ->
                leaves.add(
                    new MigratedLeaf(
                        keyFactory.storageKey(member.address(), member.slot()), value.toBytes())));
  }

  private void migrateCodeChunk(
      final StemMigrationUnit unit,
      final LeafSource.CodeChunk member,
      final WorldStateReader worldState,
      final List<MigratedLeaf> leaves) {
    checkStem(unit, keyFactory.getCodeChunkStem(member.address(), member.chunkId()), member);
    final Optional<AccountState> account = worldState.getAccount(member.address());
    if (account.isEmpty() || account.get().codeHash().equals(MptAccount.EMPTY_CODE_HASH)) {
      return; // account or code gone since the snapshot
    }
    final List<UInt256> chunks =
        chunkedCodeByHash.computeIfAbsent(
            account.get().codeHash(),
            hash ->
                TrieKeyUtils.chunkifyCode(
                    worldState.getCode(member.address()).orElse(Bytes.EMPTY)));
    final int chunkId = member.chunkId().intValue();
    if (chunkId >= chunks.size()) {
      return; // code shrank since the snapshot: the chunk no longer exists
    }
    leaves.add(
        new MigratedLeaf(
            keyFactory.codeChunkKey(member.address(), member.chunkId()),
            chunks.get(chunkId).toBytes()));
  }

  private void checkStem(
      final StemMigrationUnit unit, final BytesBitSequence derivedStem, final LeafSource member) {
    if (!derivedStem.equals(unit.stem())) {
      throw new IllegalArgumentException(
          "Migration member " + member + " does not belong to stem " + unit.stem().toHexString());
    }
  }
}
