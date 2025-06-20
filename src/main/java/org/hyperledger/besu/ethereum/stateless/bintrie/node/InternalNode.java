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
import org.apache.tuweni.bytes.Bytes32;

/**
 * Represents an internal node in a Binary Trie.
 *
 * @param <K> The type of node's location.
 * @param <V> The type of the node's value.
 */
public class InternalNode<K extends BitSequence<K>, V> extends Node<K, V> {
  public final Node<K, V> left;
  public final Node<K, V> right;

  /** Constructs a new empty InternalNode. */
  public InternalNode() {
    super();
    left = NullNode.nullNode();
    right = NullNode.nullNode();
  }

  /**
   * Constructs a new empty located InternalNode.
   *
   * @param location InternalNode's location
   */
  public InternalNode(final Optional<BitSequence<K>> location) {
    super(location);
    left = NullNode.nullNode();
    right = NullNode.nullNode();
  }

  /**
   * Constructs a new InternalNode with location and children.
   *
   * @param location The location in the tree.
   * @param left Left Node.
   * @param right Rigth Node.
   */
  public InternalNode(
      final Optional<BitSequence<K>> location, final Node<K, V> left, final Node<K, V> right) {
    super(location);
    this.left = left;
    this.right = right;
  }

  /**
   * Constructs a new InternalNode with location, commitment and children.
   *
   * @param location The location in the tree.
   * @param commitment Node's vector commitment.
   * @param left Left Node.
   * @param right Rigth Node.
   */
  public InternalNode(
      final Optional<BitSequence<K>> location,
      final Optional<Bytes32> commitment,
      final Node<K, V> left,
      final Node<K, V> right) {
    super(location, commitment);
    this.left = left;
    this.right = right;
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
  public InternalNode<K, V> replaceLocation(BitSequence<K> newLocation) {
    Node<K, V> newLeft = left.replaceLocation(newLocation.add(false));
    Node<K, V> newRight = right.replaceLocation(newLocation.add(true));
    return new InternalNode<K, V>(Optional.of(newLocation), commitment, newLeft, newRight);
  }

  /**
   * Get the RLP-encoded value of the node.
   *
   * @return The RLP-encoded value.
   */
  @Override
  public Bytes encode() {
    return commitment.map(x -> x != EMPTY_COMMITMENT ? (Bytes) x : Bytes.EMPTY).get();
  }

  /**
   * Generates a string representation of the branch node and its children.
   *
   * @return A string representing the branch node and its children.
   */
  @Override
  public String print() {
    String loc = location.map(l -> l.toBinaryString()).orElse("");
    String com = commitment.map(x -> (Bytes) x).orElse(Bytes.EMPTY).toHexString();
    final StringBuilder builder = new StringBuilder();
    builder.append(String.format("Internal [%s]: [%s]", loc, com));
    builder.append("\n").append(left.print());
    builder.append("\n").append(right.print());
    return builder.toString();
  }

  /**
   * Generates DOT representation for the InternalNode.
   *
   * @param showNullNodes Should include Null Nodes.
   * @return DOT representation of the InternalNode.
   */
  @Override
  public String toDot(Boolean showNullNodes) {
    String loc = location.map(lc -> lc.toBinaryString()).orElse("");
    StringBuilder result =
        new StringBuilder()
            .append(getName())
            .append(loc)
            .append(" [label=\"I: ")
            .append(loc)
            .append("\nCommitment: ")
            .append(commitment.map(x -> (Bytes) x).orElse(Bytes.EMPTY))
            .append("\"]\n");

    String edgeString = getName() + loc + " -> " + left.getName() + loc + "\n";
    if (showNullNodes || !result.toString().contains(edgeString)) {
      result.append(edgeString);
    }
    result.append(left.toDot(showNullNodes));

    edgeString = getName() + loc + " -> " + right.getName() + loc + "\n";
    if (showNullNodes || !result.toString().contains(edgeString)) {
      result.append(edgeString);
    }
    result.append(right.toDot(showNullNodes));

    return result.toString();
  }
}
