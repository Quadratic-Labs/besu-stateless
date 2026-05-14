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

import org.hyperledger.besu.ethereum.stateless.bintrie.BytesBitSequence;
import org.hyperledger.besu.ethereum.stateless.bintrie.adapter.TrieKeyFactory;
import org.hyperledger.besu.ethereum.stateless.bintrie.adapter.TrieKeyUtils;
import org.hyperledger.besu.ethereum.stateless.overlay.migration.LeafSource;
import org.hyperledger.besu.ethereum.stateless.overlay.migration.MigrationSource;
import org.hyperledger.besu.ethereum.stateless.overlay.migration.StemMigrationUnit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * Test implementation of {@link MigrationSource}: enumerates all stems of a fork-block {@link
 * SemanticStateModel} snapshot in canonical order (stems ascending, members by ascending suffix),
 * exactly as a production source built from a preimage snapshot would.
 */
public class MapBackedMigrationSource implements MigrationSource {

  private record Member(int suffix, LeafSource source) {}

  private final List<StemMigrationUnit> units = new ArrayList<>();
  private int position = 0;

  public MapBackedMigrationSource(SemanticStateModel snapshot, TrieKeyFactory keyFactory) {
    TreeMap<BytesBitSequence, List<Member>> byStem = new TreeMap<>();
    snapshot.accounts.forEach(
        (address, account) -> {
          // account header: basic-data (suffix 0) and code-hash (suffix 1) leaves
          byStem
              .computeIfAbsent(keyFactory.getHeaderStem(address), s -> new ArrayList<>())
              .add(new Member(0, new LeafSource.AccountHeader(address)));
          // storage slots
          snapshot
              .storageOf(address)
              .keySet()
              .forEach(
                  slot -> {
                    int suffix = Byte.toUnsignedInt(TrieKeyUtils.getStorageKeySuffix(slot).get(0));
                    byStem
                        .computeIfAbsent(
                            keyFactory.getStorageStem(address, slot), s -> new ArrayList<>())
                        .add(new Member(suffix, new LeafSource.StorageSlot(address, slot)));
                  });
          // code chunks
          Bytes code = snapshot.codeOf(address);
          int chunkCount = TrieKeyUtils.getNbChunk(code);
          for (int i = 0; i < chunkCount; i++) {
            UInt256 chunkId = UInt256.valueOf(i);
            int suffix = Byte.toUnsignedInt(TrieKeyUtils.getCodeChunkKeySuffix(chunkId).get(0));
            byStem
                .computeIfAbsent(
                    keyFactory.getCodeChunkStem(address, chunkId), s -> new ArrayList<>())
                .add(new Member(suffix, new LeafSource.CodeChunk(address, chunkId)));
          }
        });
    byStem.forEach(
        (stem, members) -> {
          members.sort(Comparator.comparingInt(Member::suffix));
          units.add(new StemMigrationUnit(stem, members.stream().map(Member::source).toList()));
        });
  }

  /** The total number of stem units in the enumeration. */
  public long totalUnits() {
    return units.size();
  }

  @Override
  public boolean hasNext() {
    return position < units.size();
  }

  @Override
  public StemMigrationUnit next() {
    return units.get(position++);
  }

  @Override
  public void seek(long frontier) {
    position = Math.toIntExact(frontier);
  }

  @Override
  public long remaining() {
    return (long) units.size() - position;
  }
}
