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
 * Class representing a visitor for traversing nodes in a Trie tree to find a node based on a path.
 *
 * @param <K> The type of node's location.
 * @param <V> The type of node values.
 */
public class GetVisitor<K extends BitSequence<K>, V> implements NodeVisitor<K, V> {
  public final BitSequence<K> path;
  private int depth;

  public GetVisitor(final BitSequence<K> path) {
    if (path == null) {
      throw new IllegalArgumentException("GetVisitor's path cannot be null");
    }
    if (path.length() > Node.KEY_SIZE) {
      throw new IllegalArgumentException(
          String.format("GetVisitor's path's size cannot be more than %s", Node.KEY_SIZE));
    }
    this.path = path;
    this.depth = -1;
  }

  public int getDepth() {
    return depth;
  }

  /**
   * Visits a internalNode to determine the node matching a given path.
   *
   * @param internalNode The internalNode being visited.
   * @return The matching node or NULL_NODE_RESULT if not found.
   */
  @Override
  public Node<K, V> visit(InternalNode<K, V> internalNode) {
    depth++;
    Node<K, V> child = path.get(depth) ? internalNode.right : internalNode.left;
    return child.accept(this);
  }

  /**
   * Visits a stemNode to determine the node matching a given path.
   *
   * @param stemNode The stemNode being visited.
   * @return The matching node or NULL_NODE_RESULT if not found.
   */
  @Override
  public Node<K, V> visit(StemNode<K, V> stemNode) {
    depth++;
    final BitSequence<K> prefix = path.commonPrefix(stemNode.stem);
    if (prefix.length() < stemNode.stem.length()) {
      return NullNode.nullNode();
    }
    int suffix = path.slice(Node.STEM_SIZE).toInt();
    return stemNode.child(suffix).accept(this);
  }

  /**
   * Visits a LeafNode to determine the matching node based on a given path.
   *
   * @param leafNode The NullNode being visited.
   * @return The NULL_NODE_RESULT since NullNode represents a missing node on the path.
   */
  @Override
  public Node<K, V> visit(LeafNode<K, V> leafNode) {
    depth++;
    return leafNode;
  }

  /**
   * Visits a NullNode to determine the matching node based on a given path.
   *
   * @param nullNode The NullNode being visited.
   * @return The NULL_NODE_RESULT since NullNode represents a missing node on the path.
   */
  @Override
  public Node<K, V> visit(NullNode<K, V> nullNode) {
    depth++;
    return nullNode;
  }

  /**
   * Visits a NullLeafNode to determine the matching node based on a given path.
   *
   * @param nullLeafNode The NullNode being visited.
   * @return The NULL_NODE_RESULT since NullNode represents a missing node on the path.
   */
  @Override
  public Node<K, V> visit(NullLeafNode<K, V> nullLeafNode) {
    depth++;
    return nullLeafNode;
  }
}
