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

/**
 * Client-supplied enumeration of the binary trie stems to migrate, built from a preimage snapshot
 * of the world state taken at the transition start block.
 *
 * <p>Contract:
 *
 * <ul>
 *   <li>The set of units is fixed at the transition start block and identical on every client
 *       (consensus-critical).
 *   <li>Units are returned in strictly ascending stem order (unsigned bitwise comparison), so every
 *       migration chunk covers a contiguous stem range.
 *   <li>An account's header stem unit carries its {@link LeafSource.AccountHeader} member plus any
 *       {@link LeafSource.StorageSlot} (slots below 64) and {@link LeafSource.CodeChunk} (chunks
 *       below 128) members that map to the header stem; other slots and chunks appear in their own
 *       stem units. Members are ordered by ascending leaf suffix.
 *   <li>Units carry provenance only, never values: the migration resolves current values from the
 *       live Merkle Patricia world state.
 * </ul>
 */
public interface MigrationSource {

  /**
   * Whether more units remain.
   *
   * @return True if {@link #next()} can be called.
   */
  boolean hasNext();

  /**
   * Returns the next unit in ascending stem order.
   *
   * @return The next stem migration unit.
   */
  StemMigrationUnit next();

  /**
   * Repositions the source so that the following {@link #next()} call returns the unit at the given
   * zero-based index of the enumeration. Used to resume after a restart.
   *
   * @param frontier The number of units already consumed.
   */
  void seek(long frontier);

  /**
   * The number of units remaining.
   *
   * @return The remaining unit count.
   */
  long remaining();
}
