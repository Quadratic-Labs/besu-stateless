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

import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

/** Write access to semantic Ethereum world state, independent of the backing trie. */
public interface WorldStateWriter {

  /**
   * Creates or updates an account's nonce and balance. On creation the account starts with empty
   * code and empty storage; on update, code and storage are preserved.
   *
   * @param address The 20-byte account address.
   * @param nonce The new nonce.
   * @param balance The new balance in wei.
   */
  void putAccount(Bytes address, long nonce, UInt256 balance);

  /**
   * Sets the code of an existing account. The account must have been created with {@link
   * #putAccount} beforehand.
   *
   * @param address The 20-byte account address.
   * @param code The new code bytes.
   */
  void putCode(Bytes address, Bytes code);

  /**
   * Writes a storage slot of an existing account. A zero value removes the slot, matching Merkle
   * Patricia Trie semantics; the backing tries therefore never store zero values.
   *
   * @param address The 20-byte account address.
   * @param slot The storage slot key.
   * @param value The new slot value; {@code UInt256.ZERO} deletes the slot.
   */
  void putStorage(Bytes address, UInt256 slot, UInt256 value);

  /**
   * Removes an account and all its associated state.
   *
   * <p>Binary trie stems are one-way hashes, so the account's storage keys and code cannot be
   * enumerated from the trie and must be supplied by the caller. Post EIP-6780 only accounts
   * created in the same transaction can be destroyed, so the caller always has this key set.
   *
   * @param address The 20-byte account address.
   * @param knownStorageKeys All storage slot keys currently set for the account.
   * @param currentCode The account's current code ({@code Bytes.EMPTY} if none).
   */
  void removeAccount(Bytes address, List<UInt256> knownStorageKeys, Bytes currentCode);
}
