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

/**
 * Protocol-level parameters of the Merkle-Patricia-to-binary-trie transition. All values are
 * consensus-critical and must be identical on every client.
 *
 * @param startBlock The first block of the transition: double-writes and migration begin here.
 * @param targetBlock The first block served exclusively by the binary trie; the migration must be
 *     complete before this block begins.
 * @param stemsPerBlock The number of stem units migrated at the end of each transition block.
 */
public record TransitionConfig(long startBlock, long targetBlock, int stemsPerBlock) {

  /**
   * Validates the parameters.
   *
   * @param startBlock The first block of the transition.
   * @param targetBlock The first block served exclusively by the binary trie.
   * @param stemsPerBlock The number of stem units migrated per block.
   */
  public TransitionConfig {
    if (startBlock < 0) {
      throw new IllegalArgumentException("startBlock must be non-negative");
    }
    if (targetBlock <= startBlock) {
      throw new IllegalArgumentException("targetBlock must be after startBlock");
    }
    if (stemsPerBlock <= 0) {
      throw new IllegalArgumentException("stemsPerBlock must be positive");
    }
  }

  /**
   * Computes the transition phase of a block.
   *
   * @param blockNumber The block number.
   * @return The phase the block belongs to.
   */
  public TransitionPhase phaseOf(final long blockNumber) {
    if (blockNumber < startBlock) {
      return TransitionPhase.PRE_TRANSITION;
    }
    if (blockNumber < targetBlock) {
      return TransitionPhase.TRANSITION;
    }
    return TransitionPhase.POST_TRANSITION;
  }
}
