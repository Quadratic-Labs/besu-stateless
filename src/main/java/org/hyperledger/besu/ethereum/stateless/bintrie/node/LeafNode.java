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
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;

/**
 * Represents a leaf node in the Verkle Trie.
 *
 * @param <K> The type of node's location.
 * @param <V> The type of the node's value.
 */
public class LeafNode<K extends BitSequence<K>, V> extends Node<K, V> {
  public final Optional<V> value; // Value associated with the node
  public final Function<V, Bytes> valueSerializer; // Serializer function for the value

  /**
   * Constructs a new LeafNode with location, value.
   *
   * @param location The location of the node in the tree.
   */
  public LeafNode(final Optional<BitSequence<K>> location) {
    super(location);
    this.value = Optional.empty();
    this.valueSerializer = val -> (Bytes) val;
  }

  /**
   * Constructs a new LeafNode with location, value.
   *
   * @param location The location of the node in the tree.
   * @param value The value associated with the node.
   */
  public LeafNode(final Optional<BitSequence<K>> location, final Optional<V> value) {
    super(location);
    this.value = value;
    this.valueSerializer = val -> (Bytes) val;
  }

  /**
   * Constructs a new LeafNode with location, value.
   *
   * @param location The location of the node in the tree.
   * @param value The value associated with the node.
   * @param valueSerializer Serializer for values.
   */
  public LeafNode(
      final Optional<BitSequence<K>> location,
      final Optional<V> value,
      final Function<V, Bytes> valueSerializer) {
    super(location);
    this.value = value;
    this.valueSerializer = valueSerializer;
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

  @Override
  public Node<K, V> replaceLocation(BitSequence<K> newLocation) {
    return new LeafNode<K, V>(Optional.of(newLocation), value, valueSerializer);
  }

  /**
   * Get the RLP-encoded value of the node.
   *
   * @return The RLP-encoded value.
   */
  @Override
  public Bytes encode() {
    return value.isPresent() ? valueSerializer.apply(value.get()) : Bytes.EMPTY;
  }

  /**
   * Get a string representation of the node.
   *
   * @return A string representation of the node.
   */
  @Override
  public String print() {
    return "Leaf:" + value.map(Object::toString).orElse("empty");
  }

  /**
   * Generates DOT representation for the LeafNode.
   *
   * @param showNullNodes Should include Null Nodes.
   * @return DOT representation of the LeafNode.
   */
  @Override
  public String toDot(Boolean showNullNodes) {
    BitSequence<K> loc = location.get();
    String locString = loc.toBinaryString();
    int suffix = loc.length() > 0 ? loc.slice(loc.length() - 1).toInt() : -1;

    return getName()
        + locString
        + " [label=\"L: "
        + locString
        + "\nSuffix: "
        + suffix
        + "\"]\n"
        + getName()
        + locString
        + " -> "
        + "Value"
        + locString
        + "\nValue"
        + locString
        + " [label=\"Value: "
        + value.orElse(null)
        + "\"]\n";
  }
}
