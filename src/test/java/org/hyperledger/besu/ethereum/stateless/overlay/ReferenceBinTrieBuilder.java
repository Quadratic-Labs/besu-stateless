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
import org.hyperledger.besu.ethereum.stateless.bintrie.SimpleBinTrie;
import org.hyperledger.besu.ethereum.stateless.bintrie.adapter.TrieKeyFactory;
import org.hyperledger.besu.ethereum.stateless.bintrie.adapter.TrieKeyUtils;
import org.hyperledger.besu.ethereum.stateless.overlay.bintrie.BinTrieWorldState;
import org.hyperledger.besu.ethereum.stateless.overlay.mpt.Keccak;
import org.hyperledger.besu.ethereum.stateless.overlay.mpt.MptAccount;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * Builds a fresh binary trie directly from a {@link SemanticStateModel}, bypassing MPT, overlay and
 * migration entirely. Its root is the ground truth every transition test compares against.
 */
public class ReferenceBinTrieBuilder {

  private ReferenceBinTrieBuilder() {}

  /** Builds the binary trie of the given semantic state and returns its root hash. */
  public static Bytes32 rootOf(SemanticStateModel model, TrieKeyFactory keyFactory) {
    SimpleBinTrie<BytesBitSequence, Bytes32> trie = new SimpleBinTrie<>();
    model.accounts.forEach(
        (address, account) -> {
          Bytes code = model.codeOf(address);
          Bytes32 codeHash = code.isEmpty() ? MptAccount.EMPTY_CODE_HASH : Keccak.keccak256(code);
          trie.put(
              keyFactory.basicDataKey(address),
              BinTrieWorldState.encodeBasicData(account.nonce(), account.balance(), code.size()));
          trie.put(keyFactory.codeHashKey(address), codeHash);
          List<UInt256> chunks = TrieKeyUtils.chunkifyCode(code);
          for (int i = 0; i < chunks.size(); i++) {
            trie.put(keyFactory.codeChunkKey(address, UInt256.valueOf(i)), chunks.get(i).toBytes());
          }
          model
              .storageOf(address)
              .forEach(
                  (slot, value) -> trie.put(keyFactory.storageKey(address, slot), value.toBytes()));
        });
    return trie.getRootHash();
  }
}
