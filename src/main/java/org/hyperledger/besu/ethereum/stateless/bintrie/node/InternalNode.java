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
    left = NullNode.node();
    right = NullNode.node();
  }

  /**
   * Constructs a new empty located InternalNode.
   *
   * @param location InternalNode's location
   */
  public InternalNode(final Optional<K> location) {
    super(location);
    left = NullNode.node();
    right = NullNode.node();
  }

  /**
   * Constructs a new InternalNode with location and children.
   *
   * @param location The location in the tree.
   * @param left Left Node.
   * @param right Rigth Node.
   */
  public InternalNode(final Optional<K> location, final Node<K, V> left, final Node<K, V> right) {
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
      final Optional<K> location,
      final Optional<Bytes32> commitment,
      final Node<K, V> left,
      final Node<K, V> right) {
    super(location, commitment);
    this.left = left;
    this.right = right;
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
   * Get the child Node at given position
   *
   * @param branch Position of the child Node
   * @return Child Node
   */
  public Node<K, V> child(final boolean branch) {
    return branch ? right : left;
  }

  /**
   * Replace child Node at given position
   *
   * @param branch Position of child node
   * @param newChild New node.
   * @return the updated StemNode
   */
  public InternalNode<K, V> replaceChild(boolean branch, Node<K, V> newChild) {
    if (branch) {
      return new InternalNode<K, V>(location, commitment, left, newChild);
    } else {
      return new InternalNode<K, V>(location, commitment, newChild, right);
    }
  }

  /**
   * Get branch of only non-null node if it exists.
   *
   * @return if there is only on non-null child, its branch
   */
  public Optional<Boolean> findOnlyChild() {
    if (left instanceof NullNode) {
      return Optional.of(true);
    }
    if (right instanceof NullNode) {
      return Optional.of(false);
    }
    return Optional.empty();
  }

  /**
   * Replace node's Location
   *
   * @param newLocation The new location for the Node
   * @return The updated Node
   */
  @Override
  public InternalNode<K, V> setLocation(Optional<K> newLocation) {
    return new InternalNode<K, V>(newLocation, commitment, left, right);
  }

  /**
   * Replace node's Location
   *
   * @param newLocation The new location for the Node
   * @return The updated Node
   */
  @Override
  public InternalNode<K, V> replaceLocation(K newLocation) {
    Node<K, V> newLeft =
        (left instanceof NullNode) ? left : left.replaceLocation(newLocation.add(false));
    Node<K, V> newRight =
        (right instanceof NullNode) ? right : right.replaceLocation(newLocation.add(true));
    return new InternalNode<K, V>(Optional.of(newLocation), commitment, newLeft, newRight);
  }

  /**
   * Set node's commitment
   *
   * @param newCommitment The new commitment for the Node
   * @return The updated Node
   */
  @Override
  public InternalNode<K, V> setCommitment(Optional<Bytes32> newCommitment) {
    return new InternalNode<K, V>(location, newCommitment, left, right);
  }

  /**
   * InternalNodes have no individual persisted representation: they are serialized as part of a
   * subtree chunk by the CommitVisitor.
   *
   * @return Never returns.
   */
  @Override
  public Bytes encode() {
    throw new UnsupportedOperationException(
        "InternalNodes are persisted in subtree chunks; see CommitVisitor");
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
    String loc = location.map(lc -> lc.toHexString()).orElse("");
    String leftLoc = left.location.map(lc -> lc.toHexString()).orElse("");
    String rightLoc = right.location.map(lc -> lc.toHexString()).orElse("");

    StringBuilder result =
        new StringBuilder()
            .append("\n")
            .append(getName())
            .append(loc)
            .append(" [commitment=")
            .append(commitment.map(x -> (Bytes) x).orElse(Bytes.EMPTY))
            .append("]");

    if (!(left instanceof NullNode) || showNullNodes) {
      result.append("\n" + getName() + loc + " -> " + left.getName() + leftLoc);
    }
    if (!(right instanceof NullNode) || showNullNodes) {
      result.append("\n" + getName() + loc + " -> " + right.getName() + rightLoc);
    }
    result.append(left.toDot(showNullNodes));
    result.append(right.toDot(showNullNodes));
    return result.toString();
  }
}
