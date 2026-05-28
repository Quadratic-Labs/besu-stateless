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
package org.hyperledger.besu.ethereum.stateless.bintrie.factory;

import org.hyperledger.besu.ethereum.stateless.bintrie.BitSequence;
import org.hyperledger.besu.ethereum.stateless.bintrie.BitSequenceFactory;
import org.hyperledger.besu.ethereum.stateless.bintrie.node.InternalNode;
import org.hyperledger.besu.ethereum.stateless.bintrie.node.LeafNode;
import org.hyperledger.besu.ethereum.stateless.bintrie.node.Node;
import org.hyperledger.besu.ethereum.stateless.bintrie.node.NullLeafNode;
import org.hyperledger.besu.ethereum.stateless.bintrie.node.NullNode;
import org.hyperledger.besu.ethereum.stateless.bintrie.node.StemNode;
import org.hyperledger.besu.ethereum.stateless.bintrie.node.StoredNode;
import org.hyperledger.besu.ethereum.stateless.bintrie.node.ValueNode;
import org.hyperledger.besu.ethereum.trie.NodeLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * A factory for creating bintrie Trie nodes based on stored data.
 *
 * @param <K> The type of keys stored in bintrie Trie nodes.
 * @param <V> The type of values stored in bintrie Trie nodes.
 */
public class StoredNodeFactory<K extends BitSequence<K>, V> implements NodeFactory<K, V> {
  private final NodeLoader nodeLoader;
  private final BitSequenceFactory<K> keyFactory;
  private final Function<Bytes, V> valueDeserializer;
  private final int stride;

  /**
   * Creates a new StoredNodeFactory with the given node loader and value deserializer, reading
   * internal nodes in chunks of stride 1.
   *
   * @param nodeLoader The loader for retrieving stored nodes.
   * @param keyFactory The function to deserialize keys from Bytes.
   * @param valueDeserializer The function to deserialize values from Bytes.
   */
  public StoredNodeFactory(
      NodeLoader nodeLoader,
      BitSequenceFactory<K> keyFactory,
      Function<Bytes, V> valueDeserializer) {
    this(nodeLoader, keyFactory, valueDeserializer, 1);
  }

  /**
   * Creates a new StoredNodeFactory with the given node loader and value deserializer.
   *
   * @param nodeLoader The loader for retrieving stored nodes.
   * @param keyFactory The function to deserialize keys from Bytes.
   * @param valueDeserializer The function to deserialize values from Bytes.
   * @param stride The depth in bits of the persisted subtrees of internal nodes; must match the
   *     stride the data was written with.
   */
  public StoredNodeFactory(
      NodeLoader nodeLoader,
      BitSequenceFactory<K> keyFactory,
      Function<Bytes, V> valueDeserializer,
      int stride) {
    if (stride < 1) {
      throw new IllegalArgumentException("Chunk stride must be at least 1");
    }
    this.nodeLoader = nodeLoader;
    this.keyFactory = keyFactory;
    this.valueDeserializer = valueDeserializer;
    this.stride = stride;
  }

  @Override
  public int getStride() {
    return stride;
  }

  /**
   * Retrieves a bintrie Trie node from stored data based on the location.
   *
   * @return An optional containing the retrieved node, or an empty optional if the node is not
   *     found.
   */
  @Override
  public Optional<Node<K, V>> retrieveRoot() {
    /*
     * Root node could be a NullNode, StemNode or InternalNode.
     * In case of StemNode, we store the stem at the root key,
     * and retrieve it by stem.
     * For the others, they work as usual.
     */
    Bytes rootKey = Bytes.wrap(keyFactory.empty().encode());
    Bytes32 hash = null; // For backward compatibilty purposes only.
    Optional<Bytes> maybeEncodedValues = nodeLoader.getNode(rootKey, hash);
    Bytes encodedValues = maybeEncodedValues.orElse(Bytes.EMPTY);
    K loc =
        encodedValues.size() == 31
            ? keyFactory.fromHexString(encodedValues.toHexString())
            : keyFactory.empty();
    return retrieve(loc);
  }

  /**
   * Retrieves a bintrie Trie node from stored data based on the location.
   *
   * @param location Node's location
   * @return An optional containing the retrieved node, or an empty optional if the node is not
   *     found.
   */
  @Override
  public Optional<Node<K, V>> retrieve(final K location) {
    /*
     * Currently, Root and Leaf are distinguishable by location.
     * Stems have locations of STEM_SIZE bits, while chunks of internal
     * nodes are keyed by their root location, a multiple of the stride.
     */
    Bytes32 hash = null; // For backward compatibilty purposes only.
    Optional<Bytes> maybeEncodedValues = nodeLoader.getNode(Bytes.wrap(location.encode()), hash);
    if (maybeEncodedValues.isEmpty()) {
      return Optional.empty();
    }
    Bytes encodedValues = maybeEncodedValues.get();
    if (location.length() == Node.STEM_SIZE) {
      return Optional.of(decodeStemNode(location, encodedValues));
    } else {
      return Optional.of(decodeChunk(location, encodedValues));
    }
  }

  /**
   * Decodes a chunk: the subtree of internal nodes of depth at most stride rooted at the given
   * location. See the {@code CHUNK_*} tags in {@link Node} for the format.
   *
   * <p>In-chunk internal nodes are fully materialized; stem links and child chunk references are
   * materialized as {@link StoredNode}s resolved lazily from storage.
   *
   * @param location The location of the chunk's root; its length is a multiple of the stride.
   * @param encodedValues The encoded chunk retrieved from storage.
   * @return The chunk's root InternalNode.
   */
  InternalNode<K, V> decodeChunk(K location, Bytes encodedValues) {
    if (location.length() % stride != 0) {
      throw new IllegalArgumentException("Chunk location misaligned with stride");
    }
    ChunkDecoder decoder = new ChunkDecoder(encodedValues);
    Node<K, V> node = decoder.decodeNode(location, 0);
    assert decoder.cursor == encodedValues.size() : "Unread bytes in stored chunk representation";
    if (!(node instanceof InternalNode)) {
      throw new IllegalArgumentException("Chunk root must be an InternalNode");
    }
    return (InternalNode<K, V>) node;
  }

  /** Cursor-based recursive decoder of a serialized chunk of internal nodes. */
  private class ChunkDecoder {
    final Bytes encodedValues;
    int cursor = 0;

    ChunkDecoder(Bytes encodedValues) {
      this.encodedValues = encodedValues;
    }

    Node<K, V> decodeNode(K location, int depth) {
      byte tag = encodedValues.get(cursor);
      cursor += 1;
      switch (tag) {
        case Node.CHUNK_NULL:
          return NullNode.node();
        case Node.CHUNK_INTERNAL:
          {
            if (depth >= stride) {
              throw new IllegalArgumentException("Internal node overflowing its chunk");
            }
            Optional<Bytes32> commitment = Optional.of(Bytes32.wrap(encodedValues, cursor));
            cursor += 32;
            Node<K, V> left = decodeNode(location.add(false), depth + 1);
            Node<K, V> right = decodeNode(location.add(true), depth + 1);
            InternalNode<K, V> internalNode =
                new InternalNode<>(Optional.of(location), commitment, left, right);
            internalNode.markClean();
            return internalNode;
          }
        case Node.CHUNK_STEM:
          {
            int extensionLength = Byte.toUnsignedInt(encodedValues.get(cursor));
            cursor += 1;
            Bytes extension = encodedValues.slice(cursor, extensionLength);
            cursor += extensionLength;
            K stem = (extensionLength > 0) ? location.concatenate(extension.toArray()) : location;
            if (stem.length() != Node.STEM_SIZE) {
              throw new IllegalArgumentException("Stem link does not resolve to a stem");
            }
            StoredNode<K, V> stemNode = new StoredNode<>(StoredNodeFactory.this, Optional.of(stem));
            stemNode.markClean();
            return stemNode;
          }
        case Node.CHUNK_CHILD:
          {
            if (depth != stride) {
              throw new IllegalArgumentException("Child chunk reference not at chunk boundary");
            }
            StoredNode<K, V> childChunk =
                new StoredNode<>(StoredNodeFactory.this, Optional.of(location));
            childChunk.markClean();
            return childChunk;
          }
        default:
          throw new IllegalArgumentException("Unknown node tag in stored chunk: " + tag);
      }
    }
  }

  /**
   * Creates a StemNode using the provided stem, hash and encodedValues
   *
   * @param stem The stem of the BranchNode.
   * @param encodedValues List of Bytes values retrieved from storage.
   * @return A BranchNode instance.
   */
  public StemNode<K, V> decodeStemNode(K stem, Bytes encodedValues) {

    // Decode encodedValues
    int cursor = 0;
    Optional<Bytes32> commitment = Optional.of((Bytes32) encodedValues.slice(cursor, cursor + 32));
    cursor += 32;
    int depth = encodedValues.get(cursor);
    K location = stem.slice(0, depth);
    cursor += 1;
    List<LeafNode<K, V>> children = new ArrayList<>(StemNode.maxChild());
    for (int i = 0; i < StemNode.maxChild(); i++) {
      children.add(NullLeafNode.node());
    }
    while (encodedValues.size() > cursor) {
      int suffix = Byte.toUnsignedInt(encodedValues.get(cursor));
      V value = valueDeserializer.apply(encodedValues.slice(cursor + 1, 32));
      K loc = location.add(suffix, StemNode.maxChildWidth());
      final ValueNode<K, V> valueNode =
          new ValueNode<>(Optional.of(loc), Optional.of(value), Optional.of(value));
      valueNode.markClean();
      children.set(suffix, valueNode);
      cursor += 33;
    }
    assert encodedValues.size() == cursor : "Unread bytes in stored StemNode representation";

    final StemNode<K, V> stemNode =
        new StemNode<>(Optional.of(location), stem, commitment, children);
    stemNode.markClean();
    return stemNode;
  }
}
