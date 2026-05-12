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
import org.hyperledger.besu.ethereum.stateless.bintrie.node.ValueNode;

/**
 * Class representing a visitor for traversing nodes in a Trie tree to find a node based on a path.
 *
 * @param <K> The type of node's location.
 * @param <V> The type of node values.
 */
public class RemoveVisitor<K extends BitSequence<K>, V> implements NodeVisitor<K, V> {

  public final K path;
  private int depth;

  public RemoveVisitor(final K path) {
    if (path == null) {
      throw new IllegalArgumentException("RemoveVisitor's path cannot be null");
    }
    if (path.length() > Node.KEY_SIZE) {
      throw new IllegalArgumentException(
          String.format("RemoveVisitor's path's size cannot be more than %s", Node.KEY_SIZE));
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
    final boolean branch = path.get(depth);
    final Node<K, V> childToVisit = internalNode.child(branch);
    final Node<K, V> visitedChild = childToVisit.accept(this);
    final InternalNode<K, V> updatedNode = internalNode.replaceChild(branch, visitedChild);
    final boolean wasChildNullified =
        (!(childToVisit instanceof NullNode) && (visitedChild instanceof NullNode));
    if (visitedChild.isDirty() || wasChildNullified) {
      updatedNode.markDirty();
    }
    return updatedNode;
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
    final K prefix = path.commonPrefix(stemNode.stem);
    if (prefix.length() < stemNode.stem.length()) {
      // The path's stem diverges from this stem: the key is not in the trie, so removal is a
      // no-op. Returning the node unchanged is essential — the parent uses the returned node as
      // the replacement child, so returning NullNode here would wipe the whole unrelated stem.
      return stemNode;
    }
    int suffix = path.slice(Node.STEM_SIZE).toInt();
    final LeafNode<K, V> childToVisit = stemNode.child(suffix);
    final LeafNode<K, V> visitedChild = childToVisit.accept(this);
    final StemNode<K, V> updatedNode = stemNode.replaceChild(suffix, visitedChild);
    final boolean wasChildNullified =
        (!(childToVisit instanceof NullLeafNode) && (visitedChild instanceof NullLeafNode));
    if (visitedChild.isDirty() || wasChildNullified) {
      updatedNode.markDirty();
    }
    return updatedNode;
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
   * Visits a ValueNode to determine the matching node based on a given path.
   *
   * @param valueNode The ValueNode being visited.
   * @return The NULL_NODE_RESULT since NullNode represents a missing node on the path.
   */
  @Override
  public LeafNode<K, V> visit(ValueNode<K, V> valueNode) {
    return NullLeafNode.node();
  }

  /**
   * Visits a NullLeafNode to determine the matching node based on a given path.
   *
   * @param nullLeafNode The NullNode being visited.
   * @return The NULL_NODE_RESULT since NullNode represents a missing node on the path.
   */
  @Override
  public LeafNode<K, V> visit(NullLeafNode<K, V> nullLeafNode) {
    depth++;
    return nullLeafNode;
  }
}
