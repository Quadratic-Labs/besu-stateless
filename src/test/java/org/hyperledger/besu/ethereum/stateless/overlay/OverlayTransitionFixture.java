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

import org.hyperledger.besu.ethereum.stateless.bintrie.adapter.TrieKeyFactory;
import org.hyperledger.besu.ethereum.stateless.bintrie.hasher.StemHasher;
import org.hyperledger.besu.ethereum.stateless.overlay.bintrie.BinTrieWorldState;
import org.hyperledger.besu.ethereum.stateless.overlay.mpt.MptWorldState;

import java.util.function.Consumer;

import org.apache.tuweni.bytes.Bytes32;

/**
 * Shared harness for transition tests: a semantic model (the oracle) and an overlay world state
 * receive the same operations; at cutover the binary trie root must equal the root of a fresh
 * binary trie built directly from the model.
 */
public class OverlayTransitionFixture {

  public final TrieKeyFactory keyFactory = new TrieKeyFactory(new StemHasher());
  public final NodeStoreMock mptStore = new NodeStoreMock();
  public final NodeStoreMock binStore = new NodeStoreMock();
  public final InMemoryCodeStore codeStore = new InMemoryCodeStore();
  public final SemanticStateModel model = new SemanticStateModel();
  public final InMemoryMigrationProgressStore progressStore = new InMemoryMigrationProgressStore();
  public MptWorldState mpt;
  public SemanticStateModel forkSnapshot;
  public MapBackedMigrationSource source;
  public TransitionConfig config;
  public OverlayWorldState overlay;
  public long nextBlock;

  public OverlayTransitionFixture() {
    mpt = new MptWorldState(mptStore, codeStore);
  }

  /** Applies a pre-fork operation to both the MPT and the model. */
  public void preFork(Consumer<WorldStateWriter> op) {
    op.accept(mpt);
    op.accept(model);
  }

  /**
   * Freezes the fork-block snapshot, builds the migration source from it and creates the overlay.
   * The transition runs from {@code startBlock} to {@code targetBlock} migrating {@code
   * stemsPerBlock} stems per block.
   */
  public void startTransition(long startBlock, long targetBlock, int stemsPerBlock) {
    mpt.commit(mptStore);
    forkSnapshot = model.copy();
    source = new MapBackedMigrationSource(forkSnapshot, keyFactory);
    config = new TransitionConfig(startBlock, targetBlock, stemsPerBlock);
    overlay =
        new OverlayWorldState(
            config, mpt, BinTrieWorldState.stored(binStore, codeStore), source, progressStore);
    nextBlock = startBlock;
  }

  /**
   * Starts the transition with a target block chosen so that the migration finishes with {@code
   * spareBlocks} transition blocks to spare.
   */
  public void startTransitionAuto(long startBlock, int stemsPerBlock, int spareBlocks) {
    mpt.commit(mptStore);
    forkSnapshot = model.copy();
    source = new MapBackedMigrationSource(forkSnapshot, keyFactory);
    long blocksNeeded = (source.totalUnits() + stemsPerBlock - 1) / stemsPerBlock;
    config =
        new TransitionConfig(
            startBlock, startBlock + Math.max(1, blocksNeeded + spareBlocks), stemsPerBlock);
    overlay =
        new OverlayWorldState(
            config, mpt, BinTrieWorldState.stored(binStore, codeStore), source, progressStore);
    nextBlock = startBlock;
  }

  /** Runs the next block, applying the given operations to both the overlay and the model. */
  public BlockCommitResult runBlock(Consumer<WorldStateWriter> ops) {
    overlay.beginBlock(nextBlock++);
    ops.accept(overlay);
    ops.accept(model);
    return overlay.endBlock(mptStore, binStore);
  }

  /** Runs the next block with no state changes. */
  public BlockCommitResult runEmptyBlock() {
    return runBlock(w -> {});
  }

  /** Runs empty blocks up to and including the cutover block and returns its result. */
  public BlockCommitResult runToCutover() {
    BlockCommitResult result = null;
    while (nextBlock <= config.targetBlock()) {
      result = runEmptyBlock();
    }
    return result;
  }

  /** The ground-truth binary trie root of the current model state. */
  public Bytes32 referenceRoot() {
    return ReferenceBinTrieBuilder.rootOf(model, keyFactory);
  }

  /**
   * Simulates a client restart: rebuilds the MPT, binary trie and overlay purely from the committed
   * node stores, the fork snapshot and the persisted migration progress. Only valid between blocks
   * (after an {@code endBlock}).
   */
  public void restartOverlay() {
    mpt = new MptWorldState(mptStore, mpt.getRootHash(), codeStore);
    source = new MapBackedMigrationSource(forkSnapshot, keyFactory);
    overlay =
        new OverlayWorldState(
            config, mpt, BinTrieWorldState.stored(binStore, codeStore), source, progressStore);
  }
}
