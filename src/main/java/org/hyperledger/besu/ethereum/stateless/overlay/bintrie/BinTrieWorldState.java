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
package org.hyperledger.besu.ethereum.stateless.overlay.bintrie;

import org.hyperledger.besu.ethereum.stateless.bintrie.BinTrie;
import org.hyperledger.besu.ethereum.stateless.bintrie.BytesBitSequence;
import org.hyperledger.besu.ethereum.stateless.bintrie.BytesBitSequenceFactory;
import org.hyperledger.besu.ethereum.stateless.bintrie.StoredBinTrie;
import org.hyperledger.besu.ethereum.stateless.bintrie.adapter.TrieKeyFactory;
import org.hyperledger.besu.ethereum.stateless.bintrie.adapter.TrieKeyUtils;
import org.hyperledger.besu.ethereum.stateless.bintrie.factory.StoredNodeFactory;
import org.hyperledger.besu.ethereum.stateless.bintrie.hasher.StemHasher;
import org.hyperledger.besu.ethereum.stateless.bintrie.util.SuffixTreeDecoder;
import org.hyperledger.besu.ethereum.stateless.bintrie.util.SuffixTreeEncoder;
import org.hyperledger.besu.ethereum.stateless.overlay.AccountState;
import org.hyperledger.besu.ethereum.stateless.overlay.WorldStateReader;
import org.hyperledger.besu.ethereum.stateless.overlay.WorldStateWriter;
import org.hyperledger.besu.ethereum.stateless.overlay.mpt.CodeStore;
import org.hyperledger.besu.ethereum.stateless.overlay.mpt.Keccak;
import org.hyperledger.besu.ethereum.stateless.overlay.mpt.MptAccount;
import org.hyperledger.besu.ethereum.trie.NodeLoader;
import org.hyperledger.besu.ethereum.trie.NodeUpdater;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * Semantic Ethereum world state backed by a binary trie, mapping accounts, storage and code onto
 * EIP-7864-style trie keys via {@link TrieKeyFactory}.
 *
 * <p>Account header fields live in the basic-data leaf (suffix 0) and code-hash leaf (suffix 1) of
 * the account's header stem; storage slots and code chunks land in suffixes and stems given by the
 * key derivation in {@link TrieKeyUtils}.
 *
 * <p>Zero storage values are removed rather than stored, so the trie content — and therefore the
 * root — is a pure function of the semantic state.
 */
public class BinTrieWorldState implements WorldStateReader, WorldStateWriter {

  private static final Bytes VERSION_ZERO = Bytes.of(0);

  private final BinTrie<BytesBitSequence, Bytes32> trie;
  private final TrieKeyFactory keyFactory;
  private final CodeStore codeStore;

  /**
   * Creates a world state over the given binary trie.
   *
   * @param trie The backing binary trie.
   * @param keyFactory The trie key derivation to use.
   * @param codeStore The content-addressed code store, used to serve {@link #getCode}.
   */
  public BinTrieWorldState(
      final BinTrie<BytesBitSequence, Bytes32> trie,
      final TrieKeyFactory keyFactory,
      final CodeStore codeStore) {
    this.trie = trie;
    this.keyFactory = keyFactory;
    this.codeStore = codeStore;
  }

  /**
   * Creates a world state over a stored binary trie whose root is recovered from the given node
   * store.
   *
   * @param nodeLoader The location-addressed binary trie node store.
   * @param codeStore The content-addressed code store.
   * @return The world state.
   */
  public static BinTrieWorldState stored(final NodeLoader nodeLoader, final CodeStore codeStore) {
    final StoredNodeFactory<BytesBitSequence, Bytes32> nodeFactory =
        new StoredNodeFactory<>(
            nodeLoader, new BytesBitSequenceFactory(), value -> (Bytes32) value);
    return new BinTrieWorldState(
        new StoredBinTrie<>(nodeFactory), new TrieKeyFactory(new StemHasher()), codeStore);
  }

  /**
   * Retrieves the backing binary trie.
   *
   * @return The backing binary trie.
   */
  public BinTrie<BytesBitSequence, Bytes32> getTrie() {
    return trie;
  }

  /**
   * Retrieves the trie key factory used for key derivation.
   *
   * @return The trie key factory.
   */
  public TrieKeyFactory getKeyFactory() {
    return keyFactory;
  }

  @Override
  public Optional<AccountState> getAccount(final Bytes address) {
    return trie.get(keyFactory.basicDataKey(address))
        .map(
            basicData -> {
              final Bytes32 codeHash =
                  trie.get(keyFactory.codeHashKey(address)).orElse(MptAccount.EMPTY_CODE_HASH);
              return new AccountState(
                  SuffixTreeDecoder.decodeNonce(basicData),
                  SuffixTreeDecoder.decodeBalance(basicData),
                  codeHash,
                  SuffixTreeDecoder.decodeCodeSize(basicData));
            });
  }

  @Override
  public Optional<UInt256> getStorage(final Bytes address, final UInt256 slot) {
    return trie.get(keyFactory.storageKey(address, slot)).map(UInt256::fromBytes);
  }

  @Override
  public Optional<Bytes> getCode(final Bytes address) {
    return trie.get(keyFactory.codeHashKey(address))
        .map(
            codeHash -> {
              if (codeHash.equals(MptAccount.EMPTY_CODE_HASH)) {
                return Bytes.EMPTY;
              }
              return codeStore
                  .getCode(codeHash)
                  .orElseThrow(
                      () -> new IllegalStateException("Missing code for hash " + codeHash));
            });
  }

  @Override
  public void putAccount(final Bytes address, final long nonce, final UInt256 balance) {
    final BytesBitSequence basicDataKey = keyFactory.basicDataKey(address);
    final Optional<Bytes32> existing = trie.get(basicDataKey);
    final boolean isNew = existing.isEmpty();
    Bytes32 basicData = existing.orElse(Bytes32.ZERO);
    basicData = SuffixTreeEncoder.setVersionInValue(basicData, VERSION_ZERO);
    basicData = SuffixTreeEncoder.setNonceInValue(basicData, Bytes.ofUnsignedLong(nonce));
    basicData = SuffixTreeEncoder.setBalanceInValue(basicData, balance.slice(16, 16));
    trie.put(basicDataKey, basicData);
    if (isNew) {
      trie.put(keyFactory.codeHashKey(address), MptAccount.EMPTY_CODE_HASH);
    }
  }

  @Override
  public void putCode(final Bytes address, final Bytes code) {
    putCode(address, code, 0);
  }

  /**
   * Sets the code of an account, additionally removing stale chunks of a prior code known to the
   * caller.
   *
   * <p>The trie must stay a pure function of the semantic state, so replacing code with shorter
   * code must remove the now-stale high-index chunk leaves. The chunk count of the prior code is
   * recovered from the code size recorded in this trie's basic-data leaf — but during the
   * transition, chunk stems can be migrated before the account's header stem, in which case chunks
   * exist here while the basic-data leaf does not. The overlay therefore passes the chunk count of
   * the prior code as recorded by the canonical Merkle Patricia world state; the larger of the two
   * bounds every chunk this trie can hold.
   *
   * @param address The 20-byte account address.
   * @param code The new code bytes.
   * @param priorChunkCountHint The chunk count of the account's prior code as known by the caller,
   *     zero if unknown.
   */
  public void putCode(final Bytes address, final Bytes code, final int priorChunkCountHint) {
    // Lenient on a missing basic-data leaf: during the transition the account may exist in the
    // MPT but not have been migrated yet; the migration later resolves the full header from the
    // live MPT and overwrites these leaves.
    final BytesBitSequence basicDataKey = keyFactory.basicDataKey(address);
    final Bytes32 existingBasicData = trie.get(basicDataKey).orElse(Bytes32.ZERO);
    final Bytes32 codeHash = code.isEmpty() ? MptAccount.EMPTY_CODE_HASH : Keccak.keccak256(code);
    if (!code.isEmpty()) {
      codeStore.putCode(codeHash, code);
    }
    trie.put(
        basicDataKey,
        SuffixTreeEncoder.setCodeSizeInValue(existingBasicData, codeSizeBytes(code.size())));
    trie.put(keyFactory.codeHashKey(address), codeHash);
    final List<UInt256> chunks = TrieKeyUtils.chunkifyCode(code);
    for (int i = 0; i < chunks.size(); i++) {
      trie.put(keyFactory.codeChunkKey(address, UInt256.valueOf(i)), chunks.get(i).toBytes());
    }
    final int oldCodeSize = SuffixTreeDecoder.decodeCodeSize(existingBasicData);
    final int recordedChunkCount = oldCodeSize == 0 ? 0 : 1 + ((oldCodeSize - 1) / 31);
    final int oldChunkCount = Math.max(recordedChunkCount, priorChunkCountHint);
    for (int i = chunks.size(); i < oldChunkCount; i++) {
      trie.remove(keyFactory.codeChunkKey(address, UInt256.valueOf(i)));
    }
  }

  @Override
  public void putStorage(final Bytes address, final UInt256 slot, final UInt256 value) {
    final BytesBitSequence key = keyFactory.storageKey(address, slot);
    if (value.isZero()) {
      trie.remove(key);
    } else {
      trie.put(key, value.toBytes());
    }
  }

  @Override
  public void removeAccount(
      final Bytes address, final List<UInt256> knownStorageKeys, final Bytes currentCode) {
    trie.remove(keyFactory.basicDataKey(address));
    trie.remove(keyFactory.codeHashKey(address));
    for (final UInt256 slot : knownStorageKeys) {
      trie.remove(keyFactory.storageKey(address, slot));
    }
    final int chunkCount = TrieKeyUtils.getNbChunk(currentCode);
    for (int i = 0; i < chunkCount; i++) {
      trie.remove(keyFactory.codeChunkKey(address, UInt256.valueOf(i)));
    }
  }

  /**
   * Writes a raw trie key/value pair, bypassing semantic mapping. Used by the migration to insert
   * leaves it has already resolved and encoded.
   *
   * @param key The full trie key (stem plus suffix).
   * @param value The 32-byte leaf value.
   */
  public void putRaw(final BytesBitSequence key, final Bytes32 value) {
    trie.put(key, value);
  }

  /**
   * Computes the binary trie root hash.
   *
   * @return The root hash.
   */
  public Bytes32 getRootHash() {
    return trie.getRootHash();
  }

  /**
   * Commits all pending trie changes to the given node store.
   *
   * @param nodeUpdater The store to write trie nodes to.
   */
  public void commit(final NodeUpdater nodeUpdater) {
    trie.commit(nodeUpdater);
  }

  /**
   * Encodes a code size as the 3-byte big-endian field of the basic-data leaf.
   *
   * @param codeSize The code size in bytes.
   * @return The 3-byte encoding.
   */
  public static Bytes codeSizeBytes(final int codeSize) {
    return UInt256.valueOf(codeSize).slice(29, 3);
  }

  /**
   * Encodes a complete basic-data leaf (version 0, code size, nonce, balance).
   *
   * @param nonce The account nonce.
   * @param balance The account balance in wei; must fit in 16 bytes.
   * @param codeSize The account's code size in bytes.
   * @return The 32-byte basic-data leaf value.
   */
  public static Bytes32 encodeBasicData(
      final long nonce, final UInt256 balance, final int codeSize) {
    Bytes32 basicData = Bytes32.ZERO;
    basicData = SuffixTreeEncoder.setVersionInValue(basicData, VERSION_ZERO);
    basicData = SuffixTreeEncoder.setCodeSizeInValue(basicData, codeSizeBytes(codeSize));
    basicData = SuffixTreeEncoder.setNonceInValue(basicData, Bytes.ofUnsignedLong(nonce));
    basicData = SuffixTreeEncoder.setBalanceInValue(basicData, balance.slice(16, 16));
    return basicData;
  }
}
