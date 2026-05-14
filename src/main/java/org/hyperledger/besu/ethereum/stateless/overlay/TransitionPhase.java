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

/** Phase of the Merkle-Patricia-to-binary-trie transition, determined by the block number. */
public enum TransitionPhase {
  /** Before the start block: the Merkle Patricia Trie serves all reads and writes. */
  PRE_TRANSITION,
  /**
   * From the start block (inclusive) to the target block (exclusive): the Merkle Patricia Trie is
   * canonical and serves reads; every write also goes to the binary trie, and a chunk of pre-fork
   * state is migrated at the end of every block.
   */
  TRANSITION,
  /** From the target block on: the binary trie serves all reads and writes. */
  POST_TRANSITION
}
