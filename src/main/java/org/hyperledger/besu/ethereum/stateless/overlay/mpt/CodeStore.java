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
package org.hyperledger.besu.ethereum.stateless.overlay.mpt;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * Content-addressed store for contract code, keyed by the Keccak-256 hash of the code.
 *
 * <p>Contract code lives outside both tries; the client supplies the backing store.
 */
public interface CodeStore {

  /**
   * Retrieves code by its hash.
   *
   * @param codeHash The Keccak-256 hash of the code.
   * @return The code bytes if present; otherwise empty.
   */
  Optional<Bytes> getCode(Bytes32 codeHash);

  /**
   * Stores code under its hash.
   *
   * @param codeHash The Keccak-256 hash of the code.
   * @param code The code bytes.
   */
  void putCode(Bytes32 codeHash, Bytes code);
}
