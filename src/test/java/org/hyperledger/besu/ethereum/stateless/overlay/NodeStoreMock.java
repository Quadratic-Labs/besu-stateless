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
package org.hyperledger.besu.ethereum.stateless.overlay;

import org.hyperledger.besu.ethereum.trie.NodeLoader;
import org.hyperledger.besu.ethereum.trie.NodeUpdater;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * In-memory node store usable by both trie kinds: the Merkle Patricia Trie addresses nodes by hash
 * (hash non-null), the binary trie by location (hash always null).
 */
public class NodeStoreMock implements NodeLoader, NodeUpdater {

  public final Map<Bytes, Bytes> storage;

  public NodeStoreMock() {
    this.storage = new HashMap<>();
  }

  public NodeStoreMock(Map<Bytes, Bytes> storage) {
    this.storage = storage;
  }

  private Bytes key(Bytes location, Bytes32 hash) {
    return hash != null ? hash : location;
  }

  @Override
  public Optional<Bytes> getNode(Bytes location, Bytes32 hash) {
    return Optional.ofNullable(storage.get(key(location, hash)));
  }

  @Override
  public void store(Bytes location, Bytes32 hash, Bytes value) {
    if (value == null) {
      storage.remove(key(location, hash));
    } else {
      storage.put(key(location, hash), value);
    }
  }

  /** Creates a copy of this store with an independent backing map. */
  public NodeStoreMock copy() {
    return new NodeStoreMock(new HashMap<>(storage));
  }
}
