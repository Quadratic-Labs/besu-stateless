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
package org.hyperledger.besu.ethereum.stateless.bintrie.visitor;

import org.hyperledger.besu.ethereum.stateless.bintrie.BitSequence;
import org.hyperledger.besu.ethereum.stateless.bintrie.node.InternalNode;
import org.hyperledger.besu.ethereum.stateless.bintrie.node.LeafNode;
import org.hyperledger.besu.ethereum.stateless.bintrie.node.Node;
import org.hyperledger.besu.ethereum.stateless.bintrie.node.NullLeafNode;
import org.hyperledger.besu.ethereum.stateless.bintrie.node.NullNode;
import org.hyperledger.besu.ethereum.stateless.bintrie.node.StemNode;

/**
 * Defines a visitor interface for nodes in the State Trie.
 *
 * @param <K> The type of node's location.
 * @param <V> The type of value associated with nodes.
 */
public interface NodeVisitor<K extends BitSequence<K>, V> {

  /**
   * Visits an internal node.
   *
   * @param internalNode The internal node to visit.
   * @return The result of visiting the internal node.
   */
  default Node<K, V> visit(InternalNode<K, V> internalNode) {
    return internalNode;
  }

  /**
   * Visits a stem node.
   *
   * @param stemNode The stem node to visit.
   * @return The result of visiting the branch node.
   */
  default Node<K, V> visit(StemNode<K, V> stemNode) {
    return stemNode;
  }

  /**
   * Visits a leaf node.
   *
   * @param leafNode The leaf node to visit.
   * @return The result of visiting the leaf node.
   */
  default Node<K, V> visit(LeafNode<K, V> leafNode) {
    return leafNode;
  }

  /**
   * Visits a null node.
   *
   * @param nullNode The null node to visit.
   * @return The result of visiting the null node.
   */
  default Node<K, V> visit(NullNode<K, V> nullNode) {
    return nullNode;
  }

  /**
   * Visits a null leaf node.
   *
   * @param nullLeafNode The null node to visit.
   * @return The result of visiting the null node.
   */
  default Node<K, V> visit(NullLeafNode<K, V> nullLeafNode) {
    return nullLeafNode;
  }
}
