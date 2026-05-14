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

import org.apache.tuweni.bytes.Bytes32;

/**
 * Outcome of committing one block through the overlay.
 *
 * @param blockNumber The committed block number.
 * @param phase The transition phase the block was processed in.
 * @param mptRootHash The Merkle Patricia account trie root after the block (frozen from the target
 *     block on).
 * @param binTrieRootHash The binary trie root after the block.
 * @param stemsMigrated The number of stem units migrated in this block.
 * @param migrationComplete Whether the migration enumeration is exhausted.
 */
public record BlockCommitResult(
    long blockNumber,
    TransitionPhase phase,
    Bytes32 mptRootHash,
    Bytes32 binTrieRootHash,
    int stemsMigrated,
    boolean migrationComplete) {}
