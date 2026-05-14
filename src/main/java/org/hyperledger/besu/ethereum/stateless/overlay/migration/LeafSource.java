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
package org.hyperledger.besu.ethereum.stateless.overlay.migration;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * Provenance of a binary trie leaf to migrate: which piece of semantic state (fixed at the fork
 * block) the leaf is derived from. Migration resolves the current value of each leaf from the live
 * Merkle Patricia world state; a {@link LeafSource} deliberately carries no value.
 */
public sealed interface LeafSource {

  /**
   * The account whose state the leaf belongs to.
   *
   * @return The 20-byte account address.
   */
  Bytes address();

  /**
   * The account-header leaves of an account: the basic-data leaf (suffix 0) and code-hash leaf
   * (suffix 1) of the account's header stem.
   *
   * @param address The 20-byte account address.
   */
  record AccountHeader(Bytes address) implements LeafSource {}

  /**
   * A storage slot leaf.
   *
   * @param address The 20-byte account address.
   * @param slot The storage slot key.
   */
  record StorageSlot(Bytes address, UInt256 slot) implements LeafSource {}

  /**
   * A code chunk leaf.
   *
   * @param address The 20-byte account address.
   * @param chunkId The zero-based 31-byte code chunk index.
   */
  record CodeChunk(Bytes address, UInt256 chunkId) implements LeafSource {}
}
