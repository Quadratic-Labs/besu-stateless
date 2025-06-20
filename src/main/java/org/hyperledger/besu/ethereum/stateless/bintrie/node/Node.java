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
 * An interface representing a node in the Verkle Trie.
 *
 * @param <K> The type of node's location.
 * @param <V> The type of the node's value.
 */
public abstract class Node<K extends BitSequence<K>, V> {

  // The Null commitment
  public static Bytes32 EMPTY_COMMITMENT = Bytes32.ZERO;

  // Number of bits in stem and key
  public static int STEM_SIZE = 248;
  public static int KEY_SIZE = 256;

  // Data fields
  public final Optional<BitSequence<K>> location;
  public final Optional<Bytes32> commitment;

  // Cache fields
  protected Optional<Bytes> encodedValue = Optional.empty(); // Encoded value
  protected boolean dirty = false;

  /** Constructs a new Node with empty defaults. */
  public Node() {
    location = Optional.empty();
    commitment = Optional.empty();
  }

  /**
   * Constructs a new located Node with yet unset commitment.
   *
   * @param location The location of the node in the trie.
   */
  public Node(final Optional<BitSequence<K>> location) {
    this.location = location;
    commitment = Optional.empty();
  }

  /**
   * Constructs a new Node
   *
   * @param location The location of the node in the trie.
   * @param commitment The node's commitment value.
   */
  public Node(final Optional<BitSequence<K>> location, final Optional<Bytes32> commitment) {
    this.location = location;
    this.commitment = commitment;
  }

  /**
   * Accept a visitor to perform operations on the node based on a provided path.
   *
   * @param visitor The visitor to accept.
   * @param path The path associated with a node.
   * @return The result of visitor's operation.
   */
  public abstract Node<K, V> accept(PathNodeVisitor<K, V> visitor, BitSequence<K> path);

  /**
   * Accept a visitor to perform operations on the node.
   *
   * @param visitor The visitor to accept.
   * @return The result of the visitor's operation.
   */
  public abstract Node<K, V> accept(NodeVisitor<K, V> visitor);

  /**
   * Replace node's Location
   *
   * @param newLocation The new location for the Node
   * @return The updated Node
   */
  public abstract Node<K, V> replaceLocation(BitSequence<K> newLocation);

  /**
   * Get the encoded value of the node.
   *
   * @return The encoded value of the node.
   */
  public Bytes getEncodedValue() {
    if (encodedValue.isEmpty()) {
      this.encodedValue = Optional.of(encode());
    }
    return encodedValue.get();
  }

  /**
   * Get the encoded value of the node.
   *
   * @return The encoded value of the node.
   */
  public abstract Bytes encode();

  /** Marks the node as needs to be persisted */
  public void markDirty() {
    dirty = true;
  }

  /** Marks this node as needing an update of its scalar and commitment. */
  public void markClean() {
    dirty = false;
  }

  /**
   * Is this node needing an update of its scalar and commitment?
   *
   * @return True if the node needs to be updated.
   */
  public boolean isDirty() {
    return dirty;
  }

  /**
   * Get a string representation of the node.
   *
   * @return A string representation of the node.
   */
  public abstract String print();

  /**
   * Generates DOT representation for the Node.
   *
   * @param showNullNodes If true, prints NullNodes and NullLeafNodes; if false, prints only unique
   *     edges.
   * @return DOT representation of the Node.
   */
  public abstract String toDot(Boolean showNullNodes);

  /**
   * Generates DOT representation for the Node.
   *
   * <p>Representation does not contain repeating edges.
   *
   * @return DOT representation of the Node.
   */
  public String toDot() {
    return toDot(false);
  }

  /**
   * Gets the name for the DOT representation for the Node.
   *
   * @return name for the DOT representation for the Node.
   */
  public String getName() {
    return getClass().getSimpleName();
  }
}
