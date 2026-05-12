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

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

/** Read access to semantic Ethereum world state, independent of the backing trie. */
public interface WorldStateReader {

  /**
   * Retrieves the header fields of an account.
   *
   * @param address The 20-byte account address.
   * @return The account state if the account exists; otherwise empty.
   */
  Optional<AccountState> getAccount(Bytes address);

  /**
   * Retrieves the value of a storage slot.
   *
   * @param address The 20-byte account address.
   * @param slot The storage slot key.
   * @return The slot value if present and non-zero; otherwise empty.
   */
  Optional<UInt256> getStorage(Bytes address, UInt256 slot);

  /**
   * Retrieves the code of an account.
   *
   * @param address The 20-byte account address.
   * @return The account's code ({@code Bytes.EMPTY} for codeless accounts) if the account exists;
   *     otherwise empty.
   */
  Optional<Bytes> getCode(Bytes address);
}
