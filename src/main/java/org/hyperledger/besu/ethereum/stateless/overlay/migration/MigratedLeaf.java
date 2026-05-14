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

import org.hyperledger.besu.ethereum.stateless.bintrie.BytesBitSequence;

import org.apache.tuweni.bytes.Bytes32;

/**
 * A resolved binary trie leaf produced by the migration, ready to insert.
 *
 * @param key The full trie key (stem plus suffix).
 * @param value The 32-byte leaf value.
 */
public record MigratedLeaf(BytesBitSequence key, Bytes32 value) {}
