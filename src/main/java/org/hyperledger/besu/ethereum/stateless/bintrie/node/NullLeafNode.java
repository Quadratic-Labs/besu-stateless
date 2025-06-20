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
package org.hyperledger.besu.ethereum.stateless.bintrie.node;

import org.hyperledger.besu.ethereum.stateless.bintrie.BitSequence;
import org.hyperledger.besu.ethereum.stateless.bintrie.visitor.NodeVisitor;
import org.hyperledger.besu.ethereum.stateless.bintrie.visitor.PathNodeVisitor;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

/**
 * A special node representing a null or empty node in the Verkle Trie.
 *
 * <p>The `NullNode` class serves as a placeholder for non-existent nodes in the Verkle Trie
 * structure. It implements the Node interface and represents a node that contains no information or
 * value.
 */
public class NullLeafNode<K extends BitSequence<K>, V> extends Node<K, V> {
  private NullLeafNode() {
    super(Optional.empty());
  }

  /**
   * Accepts a visitor for path-based operations on the node.
   *
   * @param visitor The path node visitor.
   * @param path The path associated with a node.
   * @return The result of the visitor's operation.
   */
  @Override
  public Node<K, V> accept(PathNodeVisitor<K, V> visitor, BitSequence<K> path) {
    return visitor.visit(this, path);
  }

  /**
   * Accepts a visitor for generic node operations.
   *
   * @param visitor The node visitor.
   * @return The result of the visitor's operation.
   */
  @Override
  public Node<K, V> accept(NodeVisitor<K, V> visitor) {
    return visitor.visit(this);
  }

  /**
   * Replace node's Location
   *
   * @param newLocation The new location for the Node
   * @return The updated Node
   */
  @Override
  public Node<K, V> replaceLocation(BitSequence<K> newLocation) {
    return this;
  }

  @Override
  public Bytes encode() {
    return Bytes.EMPTY;
  }

  /**
   * Get a string representation of the `NullNode`.
   *
   * @return A string representation indicating that it is a "NULL" node.
   */
  @Override
  public String print() {
    return "NULL-LEAF";
  }

  /**
   * Generates DOT representation for the NullNode.
   *
   * @param showRepeatingEdges Should show repeating edges.
   * @return DOT representation of the NullNode.
   */
  @Override
  public String toDot(Boolean showRepeatingEdges) {
    String loc = location.map(lc -> lc.toBinaryString()).orElse("");
    if (!showRepeatingEdges) {
      return "";
    }
    return getName() + loc + " [label=\"NL: " + loc + "\"]\n";
  }

  private static final NullLeafNode<?, ?> nullLeafNode = new NullLeafNode<>();

  @SuppressWarnings("unchecked")
  public static <T extends BitSequence<T>, U> NullLeafNode<T, U> node() {
    return (NullLeafNode<T, U>) nullLeafNode;
  }
}
