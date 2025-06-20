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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * Represents an internal node in a Binary Trie.
 *
 * @param <K> The type of node's location.
 * @param <V> The type of the node's value.
 */
public class StemNode<K extends BitSequence<K>, V> extends Node<K, V> {
  public final BitSequence<K> stem;
  public final Optional<Bytes32> valuesCommitment;
  private final List<Node<K, V>> children;

  /**
   * Constructs a new BranchNode with location, hash, path, and children.
   *
   * @param location The location in the tree.
   * @param stem Node's stem.
   * @param commitment The node's commitment
   * @param valuesCommitment The node's commitment to values
   * @param children The list of children nodes.
   */
  public StemNode(
      final Optional<BitSequence<K>> location,
      final BitSequence<K> stem,
      final Optional<Bytes32> commitment,
      final Optional<Bytes32> valuesCommitment,
      final List<Node<K, V>> children) {
    super(location);
    this.stem = stem;
    this.valuesCommitment = Optional.empty();
    this.children = children;
  }

  /**
   * Constructs a new BranchNode with location, hash, path, and children.
   *
   * @param location The location in the tree.
   * @param stem Node's stem.
   * @param children The list of children nodes.
   */
  public StemNode(
      final Optional<BitSequence<K>> location,
      final BitSequence<K> stem,
      final List<Node<K, V>> children) {
    super(location);
    this.stem = stem;
    this.valuesCommitment = Optional.empty();
    this.children = children;
  }

  /**
   * Constructs a new StemNode with optional location and path, initializing children to NullNodes.
   *
   * @param location The optional location in the tree.
   * @param stem Node's stem.
   */
  public StemNode(final Optional<BitSequence<K>> location, final BitSequence<K> stem) {
    super(location);
    this.stem = stem;
    this.valuesCommitment = Optional.empty();

    List<Node<K, V>> nullChildren = new ArrayList<>(maxChild());
    for (int i = 0; i < maxChild(); i++) {
      nullChildren.add(NullNode.nullNode());
    }
    this.children = nullChildren;
  }

  /**
   * Get the maximum number of children nodes (256 for byte indexes).
   *
   * @return The maximum number of children nodes.
   */
  public static int maxChild() {
    return 256;
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
   * Get the child Node at given position
   *
   * @param suffix Position of the child Node
   * @return Child Node
   */
  public Node<K, V> child(final int suffix) {
    return children.get(suffix);
  }

  /**
   * Replace child Node at given position
   *
   * @param suffix Position of child node
   * @param newChild New node.
   * @return the updated StemNode
   */
  public StemNode<K, V> replaceChild(int suffix, Node<K, V> newChild) {
    List<Node<K, V>> newChildren = new ArrayList<>(maxChild());
    for (int i = 0; i < maxChild(); i++) {
      newChildren.set(i, child(i));
    }
    newChildren.set(suffix, newChild);
    return new StemNode<K, V>(location, stem, commitment, valuesCommitment, newChildren);
  }

  /**
   * Replace node's Location
   *
   * @param newLocation The new location for the Node
   * @return The updated Node
   */
  @Override
  public Node<K, V> replaceLocation(BitSequence<K> newLocation) {
    List<Node<K, V>> newChildren = new ArrayList<>(maxChild());
    for (int i = 0; i < maxChild(); i++) {
      BitSequence<K> childLocation = newLocation.add(i);
      newChildren.set(i, child(i).replaceLocation(childLocation));
    }
    return (Node<K, V>)
        new StemNode<K, V>(
            Optional.of(newLocation), stem, commitment, valuesCommitment, newChildren);
  }

  /**
   * Get the RLP-encoded value of the node.
   *
   * @return The RLP-encoded value.
   */
  @Override
  public Bytes encode() {
    return Bytes.concatenate(
        Bytes.of(stem.encode()),
        commitment.map(x -> (Bytes) x).orElse(Bytes.EMPTY),
        valuesCommitment.map(x -> (Bytes) x).orElse(Bytes.EMPTY));
  }

  /**
   * Generates a string representation of the stem node and its children.
   *
   * @return A string representing the stem node and its children.
   */
  @Override
  public String print() {
    String loc = location.map(lc -> lc.toBinaryString()).orElse("[]");
    final StringBuilder builder = new StringBuilder();
    builder.append(
        String.format(
            "Stem %s: stem %s commitment %s",
            loc,
            stem.toBinaryString(),
            commitment.map(x -> (Bytes) x).orElse(Bytes.EMPTY).toHexString()));
    for (int i = 0; i < maxChild(); i++) {
      final Node<K, V> child = child(i);
      if (!(child instanceof NullNode)) {
        builder.append("\n").append(child.print());
      }
    }
    return builder.toString();
  }

  // Not Used for now.
  // private Bytes extractStem(final Bytes stemValue) {
  // return stemValue.slice(0, 31);
  // }

  /**
   * Dot representation of the Node.
   *
   * @param showNullNodes Should include the Null Nodes.
   * @return dot representation of the Node.
   */
  @Override
  public String toDot(Boolean showNullNodes) {
    String loc = location.map(lc -> lc.toBinaryString()).orElse("");
    StringBuilder result =
        new StringBuilder()
            .append(getName())
            .append(loc)
            .append(" [label=\"S: ")
            .append(loc)
            .append("\nStem: ")
            .append(stem.toBinaryString())
            .append("\nCommitment: ")
            .append(commitment.map(x -> (Bytes) x).orElse(Bytes.EMPTY))
            .append("\nValueCommitment: ")
            .append(valuesCommitment.map(x -> (Bytes) x).orElse(Bytes.EMPTY))
            .append("\"]\n");

    for (Node<K, V> child : children) {
      String edgeString =
          getName()
              + loc
              + " -> "
              + child.getName()
              + child.location.map(lc -> lc.toBinaryString()).orElse("")
              + "\n";

      if (showNullNodes || !result.toString().contains(edgeString)) {
        result.append(edgeString);
      }
      result.append(child.toDot(showNullNodes));
    }
    return result.toString();
  }
}
