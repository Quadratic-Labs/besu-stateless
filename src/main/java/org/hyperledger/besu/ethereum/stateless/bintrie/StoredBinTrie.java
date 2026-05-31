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
package org.hyperledger.besu.ethereum.stateless.bintrie;

import org.hyperledger.besu.ethereum.stateless.bintrie.factory.NodeFactory;
import org.hyperledger.besu.ethereum.stateless.bintrie.visitor.CommitVisitor;
import org.hyperledger.besu.ethereum.trie.NodeUpdater;

/**
 * Implementation of a bintrie Trie with nodes saved in storage.
 *
 * @param <K> The type of keys in the bintrie Trie.
 * @param <V> The type of values in the bintrie Trie.
 */
public class StoredBinTrie<K extends BitSequence<K>, V> extends SimpleBinTrie<K, V> {
  /** NodeFactory that load nodes from storage */
  protected final NodeFactory<K, V> nodeFactory;

  /**
   * Create a trie.
   *
   * @param nodeFactory The {@link NodeFactory} to retrieve node.
   */
  public StoredBinTrie(final NodeFactory<K, V> nodeFactory) {
    super(nodeFactory.retrieveRoot());
    this.nodeFactory = nodeFactory;
  }

  /**
   * Creates the CommitVisitor used to persist the trie, using the same chunk stride as the
   * NodeFactory the trie is read with.
   *
   * @param nodeUpdater The node updater for storing the changes in the Bin Trie.
   * @return A CommitVisitor.
   */
  @Override
  protected CommitVisitor<K, V> createCommitVisitor(final NodeUpdater nodeUpdater) {
    return new CommitVisitor<K, V>(nodeUpdater, nodeFactory.getStride());
  }
}
