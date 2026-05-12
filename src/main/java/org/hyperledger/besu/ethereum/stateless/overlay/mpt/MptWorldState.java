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
package org.hyperledger.besu.ethereum.stateless.overlay.mpt;

import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.stateless.overlay.AccountState;
import org.hyperledger.besu.ethereum.stateless.overlay.WorldStateReader;
import org.hyperledger.besu.ethereum.stateless.overlay.WorldStateWriter;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.NodeLoader;
import org.hyperledger.besu.ethereum.trie.NodeUpdater;
import org.hyperledger.besu.ethereum.trie.patricia.StoredMerklePatriciaTrie;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * Minimal two-layer Merkle Patricia world state: an account trie keyed by {@code
 * keccak256(address)} holding RLP-encoded accounts, plus one storage trie per account keyed by
 * {@code keccak256(slot)} holding RLP-scalar-encoded values.
 *
 * <p>All tries share a single hash-addressed node store (forest style). Contract code is held in a
 * separate content-addressed {@link CodeStore}.
 */
public class MptWorldState implements WorldStateReader, WorldStateWriter {

  private final NodeLoader nodeLoader;
  private final CodeStore codeStore;
  private final MerkleTrie<Bytes32, Bytes> accountTrie;
  private final Map<Bytes, MerkleTrie<Bytes32, Bytes>> storageTries = new HashMap<>();
  private final Set<Bytes> dirtyStorage = new HashSet<>();

  /**
   * Creates an empty world state.
   *
   * @param nodeLoader The hash-addressed node store shared by the account and storage tries.
   * @param codeStore The content-addressed code store.
   */
  public MptWorldState(final NodeLoader nodeLoader, final CodeStore codeStore) {
    this.nodeLoader = nodeLoader;
    this.codeStore = codeStore;
    this.accountTrie =
        new StoredMerklePatriciaTrie<>(nodeLoader, Function.identity(), Function.identity());
  }

  /**
   * Loads an existing world state from its root hash.
   *
   * @param nodeLoader The hash-addressed node store shared by the account and storage tries.
   * @param rootHash The account trie root hash to load.
   * @param codeStore The content-addressed code store.
   */
  public MptWorldState(
      final NodeLoader nodeLoader, final Bytes32 rootHash, final CodeStore codeStore) {
    this.nodeLoader = nodeLoader;
    this.codeStore = codeStore;
    this.accountTrie =
        new StoredMerklePatriciaTrie<>(
            nodeLoader, rootHash, Function.identity(), Function.identity());
  }

  /**
   * Retrieves the raw account record for an address.
   *
   * @param address The 20-byte account address.
   * @return The account if it exists; otherwise empty.
   */
  public Optional<MptAccount> getMptAccount(final Bytes address) {
    return accountTrie.get(Keccak.keccak256(address)).map(MptAccount::fromRlp);
  }

  /**
   * Retrieves the code store backing this world state.
   *
   * @return The code store.
   */
  public CodeStore getCodeStore() {
    return codeStore;
  }

  @Override
  public Optional<AccountState> getAccount(final Bytes address) {
    return getMptAccount(address)
        .map(
            account ->
                new AccountState(
                    account.nonce(), account.balance(), account.codeHash(), codeSize(account)));
  }

  @Override
  public Optional<UInt256> getStorage(final Bytes address, final UInt256 slot) {
    final Optional<MerkleTrie<Bytes32, Bytes>> storageTrie = storageTrie(address, false);
    if (storageTrie.isEmpty()) {
      return Optional.empty();
    }
    return storageTrie
        .get()
        .get(Keccak.keccak256(slot))
        .map(rlp -> new BytesValueRLPInput(rlp, false).readUInt256Scalar());
  }

  @Override
  public Optional<Bytes> getCode(final Bytes address) {
    return getMptAccount(address)
        .map(
            account -> {
              if (account.codeHash().equals(MptAccount.EMPTY_CODE_HASH)) {
                return Bytes.EMPTY;
              }
              return codeStore
                  .getCode(account.codeHash())
                  .orElseThrow(
                      () ->
                          new IllegalStateException("Missing code for hash " + account.codeHash()));
            });
  }

  @Override
  public void putAccount(final Bytes address, final long nonce, final UInt256 balance) {
    final MptAccount account =
        getMptAccount(address)
            .map(existing -> existing.withNonceAndBalance(nonce, balance))
            .orElse(
                new MptAccount(
                    nonce, balance, MptAccount.EMPTY_TRIE_HASH, MptAccount.EMPTY_CODE_HASH));
    accountTrie.put(Keccak.keccak256(address), account.toRlp());
  }

  @Override
  public void putCode(final Bytes address, final Bytes code) {
    final MptAccount account =
        getMptAccount(address)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "putCode for missing account " + address + "; call putAccount first"));
    final Bytes32 codeHash = code.isEmpty() ? MptAccount.EMPTY_CODE_HASH : Keccak.keccak256(code);
    if (!code.isEmpty()) {
      codeStore.putCode(codeHash, code);
    }
    accountTrie.put(Keccak.keccak256(address), account.withCodeHash(codeHash).toRlp());
  }

  @Override
  public void putStorage(final Bytes address, final UInt256 slot, final UInt256 value) {
    if (getMptAccount(address).isEmpty()) {
      throw new IllegalStateException(
          "putStorage for missing account " + address + "; call putAccount first");
    }
    final MerkleTrie<Bytes32, Bytes> storageTrie = storageTrie(address, true).orElseThrow();
    if (value.isZero()) {
      storageTrie.remove(Keccak.keccak256(slot));
    } else {
      storageTrie.put(Keccak.keccak256(slot), RLP.encode(out -> out.writeUInt256Scalar(value)));
    }
    dirtyStorage.add(address);
  }

  @Override
  public void removeAccount(
      final Bytes address, final List<UInt256> knownStorageKeys, final Bytes currentCode) {
    accountTrie.remove(Keccak.keccak256(address));
    storageTries.remove(address);
    dirtyStorage.remove(address);
  }

  /**
   * Computes the world state root hash, folding pending storage roots into the account trie.
   *
   * @return The account trie root hash.
   */
  public Bytes32 getRootHash() {
    fold();
    return accountTrie.getRootHash();
  }

  /**
   * Commits all pending trie changes to the given node store.
   *
   * @param nodeUpdater The store to write trie nodes to.
   */
  public void commit(final NodeUpdater nodeUpdater) {
    fold();
    storageTries.values().forEach(trie -> trie.commit(nodeUpdater));
    accountTrie.commit(nodeUpdater);
  }

  private int codeSize(final MptAccount account) {
    if (account.codeHash().equals(MptAccount.EMPTY_CODE_HASH)) {
      return 0;
    }
    return codeStore
        .getCode(account.codeHash())
        .map(Bytes::size)
        .orElseThrow(
            () -> new IllegalStateException("Missing code for hash " + account.codeHash()));
  }

  /** Folds the root hashes of modified storage tries into their account records. */
  private void fold() {
    for (final Bytes address : dirtyStorage) {
      final MptAccount account =
          getMptAccount(address)
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Storage was modified for missing account " + address));
      final Bytes32 storageRoot = storageTries.get(address).getRootHash();
      accountTrie.put(Keccak.keccak256(address), account.withStorageRoot(storageRoot).toRlp());
    }
    dirtyStorage.clear();
  }

  private Optional<MerkleTrie<Bytes32, Bytes>> storageTrie(
      final Bytes address, final boolean createIfAbsent) {
    final MerkleTrie<Bytes32, Bytes> cached = storageTries.get(address);
    if (cached != null) {
      return Optional.of(cached);
    }
    final Optional<MptAccount> account = getMptAccount(address);
    if (account.isEmpty() && !createIfAbsent) {
      return Optional.empty();
    }
    final Bytes32 storageRoot =
        account.map(MptAccount::storageRoot).orElse(MptAccount.EMPTY_TRIE_HASH);
    final MerkleTrie<Bytes32, Bytes> trie;
    if (storageRoot.equals(MptAccount.EMPTY_TRIE_HASH)) {
      if (!createIfAbsent) {
        return Optional.empty();
      }
      trie = new StoredMerklePatriciaTrie<>(nodeLoader, Function.identity(), Function.identity());
    } else {
      trie =
          new StoredMerklePatriciaTrie<>(
              nodeLoader, storageRoot, Function.identity(), Function.identity());
    }
    storageTries.put(address, trie);
    return Optional.of(trie);
  }
}
