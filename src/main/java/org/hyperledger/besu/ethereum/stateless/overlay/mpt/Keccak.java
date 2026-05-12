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

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.crypto.digests.KeccakDigest;

/** Keccak-256 hashing utility, used for Merkle Patricia Trie key derivation. */
public class Keccak {

  private Keccak() {}

  /**
   * Computes the Keccak-256 hash of the given input.
   *
   * @param input The bytes to hash.
   * @return The 32-byte Keccak-256 digest.
   */
  public static Bytes32 keccak256(final Bytes input) {
    final KeccakDigest digest = new KeccakDigest(256);
    final byte[] bytes = input.toArrayUnsafe();
    digest.update(bytes, 0, bytes.length);
    final byte[] out = new byte[32];
    digest.doFinal(out, 0);
    return Bytes32.wrap(out);
  }
}
