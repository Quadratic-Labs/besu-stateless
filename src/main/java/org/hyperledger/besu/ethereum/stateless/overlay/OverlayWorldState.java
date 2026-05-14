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

import org.hyperledger.besu.ethereum.stateless.bintrie.adapter.TrieKeyUtils;
import org.hyperledger.besu.ethereum.stateless.overlay.bintrie.BinTrieWorldState;
import org.hyperledger.besu.ethereum.stateless.overlay.migration.MigrationProgress;
import org.hyperledger.besu.ethereum.stateless.overlay.migration.MigrationProgressStore;
import org.hyperledger.besu.ethereum.stateless.overlay.migration.MigrationRunner;
import org.hyperledger.besu.ethereum.stateless.overlay.migration.MigrationSource;
import org.hyperledger.besu.ethereum.stateless.overlay.migration.StemMigrator;
import org.hyperledger.besu.ethereum.stateless.overlay.mpt.MptWorldState;
import org.hyperledger.besu.ethereum.trie.NodeUpdater;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * World state façade orchestrating the overlay transition from the Merkle Patricia Trie to the
 * binary trie.
 *
 * <p>Per-block protocol, all consensus-critical and driven by {@link TransitionConfig}:
 *
 * <ol>
 *   <li>{@link #beginBlock} resolves the phase and validates the cutover at the target block.
 *   <li>During the {@code TRANSITION} phase, semantic writes go to both tries (double-write) while
 *       reads are served exclusively by the Merkle Patricia Trie.
 *   <li>{@link #endBlock} migrates the next chunk of stem units (after the block's writes, so
 *       values resolve against the end-of-block state), commits both tries, and persists the
 *       migration progress.
 * </ol>
 */
public class OverlayWorldState implements WorldStateReader, WorldStateWriter {

  private final TransitionConfig config;
  private final MptWorldState mptWorldState;
  private final BinTrieWorldState binTrieWorldState;
  private final MigrationRunner migrationRunner;
  private final MigrationProgressStore progressStore;

  private long currentBlock = -1;
  private boolean blockOpen = false;
  private TransitionPhase phase = TransitionPhase.PRE_TRANSITION;

  /**
   * Creates the overlay. On a restart the migration resumes from the frontier found in the progress
   * store.
   *
   * @param config The protocol parameters of the transition.
   * @param mptWorldState The Merkle Patricia world state (canonical until the target block).
   * @param binTrieWorldState The binary trie world state built up during the transition.
   * @param migrationSource The stem enumeration of the fork-block state snapshot.
   * @param progressStore Persistence for the migration frontier.
   */
  public OverlayWorldState(
      final TransitionConfig config,
      final MptWorldState mptWorldState,
      final BinTrieWorldState binTrieWorldState,
      final MigrationSource migrationSource,
      final MigrationProgressStore progressStore) {
    this.config = config;
    this.mptWorldState = mptWorldState;
    this.binTrieWorldState = binTrieWorldState;
    this.progressStore = progressStore;
    final MigrationProgress progress = progressStore.load().orElse(MigrationProgress.zero());
    this.migrationRunner =
        new MigrationRunner(
            migrationSource,
            new StemMigrator(binTrieWorldState.getKeyFactory()),
            mptWorldState,
            binTrieWorldState,
            progress.frontier());
  }

  /**
   * Opens a block for processing. Within one overlay instance block numbers must be consecutive.
   *
   * @param blockNumber The block number.
   * @throws TransitionIncompleteException if the block is at or past the target block and the
   *     migration is not complete.
   */
  public void beginBlock(final long blockNumber) {
    if (blockOpen) {
      throw new IllegalStateException("Block " + currentBlock + " is still open");
    }
    if (currentBlock >= 0 && blockNumber != currentBlock + 1) {
      throw new IllegalStateException(
          "Non-consecutive block " + blockNumber + " after " + currentBlock);
    }
    final TransitionPhase blockPhase = config.phaseOf(blockNumber);
    if (blockPhase == TransitionPhase.POST_TRANSITION && !migrationRunner.isComplete()) {
      throw new TransitionIncompleteException(blockNumber, migrationRunner.remaining());
    }
    currentBlock = blockNumber;
    phase = blockPhase;
    blockOpen = true;
  }

  /**
   * Closes the current block: migrates the next chunk (TRANSITION phase only), commits the tries
   * and persists migration progress.
   *
   * @param mptNodeUpdater The store for Merkle Patricia trie nodes.
   * @param binNodeUpdater The store for binary trie nodes.
   * @return The commit outcome, including both root hashes.
   */
  public BlockCommitResult endBlock(
      final NodeUpdater mptNodeUpdater, final NodeUpdater binNodeUpdater) {
    if (!blockOpen) {
      throw new IllegalStateException("No block is open");
    }
    int stemsMigrated = 0;
    switch (phase) {
      case PRE_TRANSITION -> mptWorldState.commit(mptNodeUpdater);
      case TRANSITION -> {
        stemsMigrated = migrationRunner.runChunk(config.stemsPerBlock());
        mptWorldState.commit(mptNodeUpdater);
        binTrieWorldState.commit(binNodeUpdater);
        progressStore.save(migrationRunner.progress());
      }
      case POST_TRANSITION -> binTrieWorldState.commit(binNodeUpdater);
    }
    blockOpen = false;
    return new BlockCommitResult(
        currentBlock,
        phase,
        mptWorldState.getRootHash(),
        binTrieWorldState.getRootHash(),
        stemsMigrated,
        migrationRunner.isComplete());
  }

  /**
   * The phase of the block currently or most recently processed.
   *
   * @return The transition phase.
   */
  public TransitionPhase getPhase() {
    return phase;
  }

  /**
   * The Merkle Patricia account trie root hash (frozen from the target block on).
   *
   * @return The root hash.
   */
  public Bytes32 getMptRootHash() {
    return mptWorldState.getRootHash();
  }

  /**
   * The binary trie root hash.
   *
   * @return The root hash.
   */
  public Bytes32 getBinTrieRootHash() {
    return binTrieWorldState.getRootHash();
  }

  /**
   * The current migration progress.
   *
   * @return The progress (frontier and completion flag).
   */
  public MigrationProgress getMigrationProgress() {
    return migrationRunner.progress();
  }

  /**
   * The binary trie world state. After the cutover the client can drop the overlay and keep only
   * this.
   *
   * @return The binary trie world state.
   */
  public BinTrieWorldState asBinTrieWorldState() {
    return binTrieWorldState;
  }

  @Override
  public Optional<AccountState> getAccount(final Bytes address) {
    return reader().getAccount(address);
  }

  @Override
  public Optional<UInt256> getStorage(final Bytes address, final UInt256 slot) {
    return reader().getStorage(address, slot);
  }

  @Override
  public Optional<Bytes> getCode(final Bytes address) {
    return reader().getCode(address);
  }

  @Override
  public void putAccount(final Bytes address, final long nonce, final UInt256 balance) {
    if (phase != TransitionPhase.POST_TRANSITION) {
      mptWorldState.putAccount(address, nonce, balance);
    }
    if (phase != TransitionPhase.PRE_TRANSITION) {
      binTrieWorldState.putAccount(address, nonce, balance);
    }
  }

  @Override
  public void putCode(final Bytes address, final Bytes code) {
    if (phase == TransitionPhase.TRANSITION) {
      // The chunk set held by the binary trie (from double-writes and migrated chunk stems) is
      // always a subset of the prior code's chunks as recorded by the canonical MPT; pass its
      // chunk count so a shorter code removes every stale chunk leaf.
      final int priorChunkCount =
          TrieKeyUtils.getNbChunk(mptWorldState.getCode(address).orElse(Bytes.EMPTY));
      mptWorldState.putCode(address, code);
      binTrieWorldState.putCode(address, code, priorChunkCount);
      return;
    }
    if (phase == TransitionPhase.PRE_TRANSITION) {
      mptWorldState.putCode(address, code);
    } else {
      binTrieWorldState.putCode(address, code);
    }
  }

  @Override
  public void putStorage(final Bytes address, final UInt256 slot, final UInt256 value) {
    if (phase != TransitionPhase.POST_TRANSITION) {
      mptWorldState.putStorage(address, slot, value);
    }
    if (phase != TransitionPhase.PRE_TRANSITION) {
      binTrieWorldState.putStorage(address, slot, value);
    }
  }

  @Override
  public void removeAccount(
      final Bytes address, final List<UInt256> knownStorageKeys, final Bytes currentCode) {
    if (phase != TransitionPhase.POST_TRANSITION) {
      mptWorldState.removeAccount(address, knownStorageKeys, currentCode);
    }
    if (phase != TransitionPhase.PRE_TRANSITION) {
      binTrieWorldState.removeAccount(address, knownStorageKeys, currentCode);
    }
  }

  private WorldStateReader reader() {
    return phase == TransitionPhase.POST_TRANSITION ? binTrieWorldState : mptWorldState;
  }
}
