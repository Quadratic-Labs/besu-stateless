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

import java.util.Optional;

/**
 * A visitor for inserting or updating values in a Verkle Trie.
 *
 * <p>This class implements the PathNodeVisitor interface and is used to visit and modify nodes in
 * the Verkle Trie while inserting or updating a value associated with a specific path.
 *
 * @param <V> The type of values to insert or update.
 */
public class PutVisitor<K extends BitSequence<K>, V> implements NodeVisitor<K, V> {
  public final BitSequence<K> path;
  public final V value;
  private int depth = -1;

  /**
   * Constructs a new PutVisitor with the provided value to insert or update.
   *
   * @param value The value to be inserted or updated in the Verkle Trie.
   */
  public PutVisitor(final BitSequence<K> path, final V value) {
    assert path.length() < Node.KEY_SIZE;
    this.path = path;
    this.value = value;
  }

  /**
   * Visits a branch node to insert or update a value associated with the provided path.
   *
   * @param internalNode The internal node to visit.
   * @return The updated branch node with the inserted or updated value.
   */
  @Override
  public Node<K, V> visit(final InternalNode<K, V> internalNode) {
    depth++;
    Node<K, V> result;
    if (path.get(depth)) {
      result =
          new InternalNode<K, V>(
              internalNode.location,
              internalNode.commitment,
              internalNode.left,
              internalNode.right.accept(this));
    } else {
      result =
          new InternalNode<K, V>(
              internalNode.location,
              internalNode.commitment,
              internalNode.left.accept(this),
              internalNode.right);
    }
    return result;
  }

  /**
   * Visits a stem node to insert or update a value associated with the provided path.
   *
   * @param stemNode The stem node to visit.
   * @return The updated branch node with the inserted or updated value.
   */
  @Override
  public Node<K, V> visit(final StemNode<K, V> stemNode) {
    // Do not depth++ as in case of divergent stem, we do not move down
    final BitSequence<K> newStem = path.slice(0, Node.STEM_SIZE);
    if (stemNode.stem.compareTo(newStem) == 0) { // Same stem => skip to leaf in StemNode
      depth++;
      final int suffix = path.slice(Node.STEM_SIZE).toInt();
      return stemNode.replaceChild(suffix, stemNode.child(suffix).accept(this));
    } else { // Divergent stems => push StemNode one level down
      InternalNode<K, V> result;
      if (stemNode.stem.get(depth + 1)) {
        result =
            new InternalNode<K, V>(
                stemNode.location,
                NullNode.nullNode(),
                stemNode.replaceLocation(stemNode.location.get().add(true)));
      } else {
        result =
            new InternalNode<K, V>(
                stemNode.location,
                stemNode.replaceLocation(stemNode.location.get().add(false)),
                NullNode.nullNode());
      }
      return result.accept(this);
    }
  }

  /**
   * Visits a leaf node to insert or update a value associated with the provided path.
   *
   * @param leafNode The leaf node to visit.
   * @return The updated leaf node with the inserted or updated value.
   */
  @Override
  public Node<K, V> visit(final LeafNode<K, V> leafNode) {
    depth++;
    return new LeafNode<K, V>(leafNode.location, Optional.of(value), leafNode.valueSerializer);
  }

  /**
   * Visits a null node to insert or update a value associated with the provided path.
   *
   * @param nullNode The null node to visit.
   * @return A new leaf node containing the inserted or updated value.
   */
  @Override
  public Node<K, V> visit(final NullNode<K, V> nullNode) {
    depth++;
    return new StemNode<K, V>(Optional.of(path.slice(0, depth)), path.slice(0, Node.STEM_SIZE))
        .accept(this);
  }

  /**
   * Visits a null leafnode to insert or update a value associated with the provided path.
   *
   * @param nullLeafNode The null node to visit.
   * @return A new leaf node containing the inserted or updated value.
   */
  @Override
  public Node<K, V> visit(final NullLeafNode<K, V> nullLeafNode) {
    depth++;
    return new LeafNode<K, V>(Optional.of(path.slice(0, depth)), Optional.of(value));
  }
}
