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
import org.hyperledger.besu.ethereum.stateless.bintrie.node.StoredNode;
import org.hyperledger.besu.ethereum.stateless.bintrie.node.ValueNode;
import org.hyperledger.besu.ethereum.trie.NodeUpdater;

import java.util.ArrayList;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;

/**
 * Class representing a visitor persisting the modified parts of a Trie.
 *
 * <p>Internal nodes are not persisted individually: subtrees of internal nodes of depth {@code
 * stride}, rooted at locations whose length is a multiple of the stride, are serialized into a
 * single value stored under the encoded location of the subtree's root. See the {@code CHUNK_*}
 * tags in {@link Node} for the format. StemNodes keep their own entry under their encoded stem.
 *
 * @param <K> The type of node's location.
 * @param <V> The type of node values.
 */
public class CommitVisitor<K extends BitSequence<K>, V> implements NodeVisitor<K, V> {
  /** The NodeUpdater used to store changes in the Trie structure. */
  protected final NodeUpdater nodeUpdater;

  /** The depth in bits of the subtrees of internal nodes persisted as a single value. */
  protected final int stride;

  public CommitVisitor(final NodeUpdater nodeUpdater) {
    this(nodeUpdater, 1);
  }

  /**
   * Creates a CommitVisitor persisting internal nodes in chunks of the given stride.
   *
   * @param nodeUpdater The NodeUpdater used to store changes in the Trie structure.
   * @param stride The depth of the persisted subtrees; must match the stride used when reading.
   */
  public CommitVisitor(final NodeUpdater nodeUpdater, final int stride) {
    if (stride < 1) {
      throw new IllegalArgumentException("Chunk stride must be at least 1");
    }
    this.nodeUpdater = nodeUpdater;
    this.stride = stride;
  }

  /**
   * Visits a internalNode.
   *
   * @param internalNode The internalNode being visited.
   * @return The matching node or NULL_NODE_RESULT if not found.
   */
  @Override
  public Node<K, V> visit(InternalNode<K, V> internalNode) {
    if (!internalNode.isDirty()) {
      return internalNode;
    }
    if (internalNode.location.isEmpty()) {
      throw new RuntimeException("Cannot persist node without location");
    }
    K location = internalNode.location.get();
    // Persist descendant chunks and stems first. StoredNode children are already
    // persisted and clean; visiting them would needlessly load them from storage.
    if (!(internalNode.left instanceof StoredNode)) {
      internalNode.left.accept(this);
    }
    if (!(internalNode.right instanceof StoredNode)) {
      internalNode.right.accept(this);
    }
    if (location.length() % stride == 0) {
      nodeUpdater.store(Bytes.wrap(location.encode()), null, encodeChunk(internalNode, location));
    }
    internalNode.markClean();
    return internalNode;
  }

  /**
   * Serializes the subtree of internal nodes of depth at most {@code stride} rooted at the given
   * chunk root.
   *
   * @param chunkRoot The internal node at the root of the chunk.
   * @param location The chunk root's location; its length is a multiple of the stride.
   * @return The encoded chunk.
   */
  Bytes encodeChunk(InternalNode<K, V> chunkRoot, K location) {
    List<Bytes> out = new ArrayList<>();
    encodeSubtree(chunkRoot, location.length(), 0, out);
    return Bytes.concatenate(out.toArray(new Bytes[0]));
  }

  private void encodeSubtree(Node<K, V> node, int chunkRootDepth, int depth, List<Bytes> out) {
    if (node instanceof NullNode) {
      out.add(Bytes.of(Node.CHUNK_NULL));
    } else if (node instanceof StemNode) {
      encodeStemLink(((StemNode<K, V>) node).stem, chunkRootDepth + depth, out);
    } else if (node instanceof StoredNode) {
      K childLocation =
          node.location.orElseThrow(
              () -> new RuntimeException("Cannot persist StoredNode without location"));
      if (childLocation.length() == Node.STEM_SIZE) {
        encodeStemLink(childLocation, chunkRootDepth + depth, out);
      } else if (depth == stride && childLocation.length() % stride == 0) {
        out.add(Bytes.of(Node.CHUNK_CHILD));
      } else {
        throw new RuntimeException("StoredNode misaligned with chunk boundaries");
      }
    } else if (node instanceof InternalNode) {
      if (depth == stride) {
        // Root of the next chunk, persisted under its own location key.
        out.add(Bytes.of(Node.CHUNK_CHILD));
        return;
      }
      InternalNode<K, V> internalNode = (InternalNode<K, V>) node;
      out.add(Bytes.of(Node.CHUNK_INTERNAL));
      out.add(
          internalNode.commitment.orElseThrow(
              () -> new RuntimeException("Cannot persist node without commitment")));
      encodeSubtree(internalNode.left, chunkRootDepth, depth + 1, out);
      encodeSubtree(internalNode.right, chunkRootDepth, depth + 1, out);
    } else {
      throw new RuntimeException("Unexpected node type in internal tree: " + node.getName());
    }
  }

  private void encodeStemLink(K stem, int fromDepth, List<Bytes> out) {
    Bytes extension = Bytes.wrap(stem.slice(fromDepth).encode());
    out.add(Bytes.of(Node.CHUNK_STEM));
    out.add(Bytes.of(extension.size()));
    out.add(extension);
  }

  /**
   * Visits a stemNode.
   *
   * @param stemNode The stemNode being visited.
   * @return The matching node or NULL_NODE_RESULT if not found.
   */
  @Override
  public Node<K, V> visit(StemNode<K, V> stemNode) {
    if (!stemNode.isDirty()) {
      return stemNode;
    }
    if (stemNode.commitment.isEmpty()) {
      throw new RuntimeException("Cannot persist node without commitment");
    }
    if (stemNode.location.isEmpty()) {
      throw new RuntimeException("Cannot persist node without location");
    }
    for (int i = 0; i < StemNode.maxChild(); ++i) {
      stemNode.child(i).accept(this);
    }
    Bytes key = Bytes.wrap(stemNode.stem.encode());
    nodeUpdater.store(key, null, stemNode.getEncodedValue());
    stemNode.markClean();
    K location = stemNode.location.get();
    if (location.length() == 0) {
      nodeUpdater.store(Bytes.wrap(location.encode()), null, Bytes.wrap(stemNode.stem.toBytes()));
    }
    return stemNode;
  }

  /**
   * Visits a NullNode.
   *
   * @param nullNode The NullNode being visited.
   * @return The NULL_NODE_RESULT since NullNode represents a missing node on the path.
   */
  @Override
  public Node<K, V> visit(NullNode<K, V> nullNode) {
    return nullNode;
  }

  /**
   * Visits a ValueNode.
   *
   * @param valueNode The NullNode being visited.
   * @return The NULL_NODE_RESULT since NullNode represents a missing node on the path.
   */
  @Override
  public LeafNode<K, V> visit(ValueNode<K, V> valueNode) {
    valueNode.markClean();
    return valueNode;
  }

  /**
   * Visits a NullLeafNode.
   *
   * @param nullLeafNode The NullNode being visited.
   * @return The NULL_NODE_RESULT since NullNode represents a missing node on the path.
   */
  @Override
  public LeafNode<K, V> visit(NullLeafNode<K, V> nullLeafNode) {
    return nullLeafNode;
  }
}
