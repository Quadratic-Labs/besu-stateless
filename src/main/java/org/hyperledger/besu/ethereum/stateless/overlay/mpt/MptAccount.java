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

import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * The canonical Ethereum account record as stored in the Merkle Patricia account trie, RLP-encoded
 * as the list (nonce, balance, storageRoot, codeHash).
 *
 * @param nonce The account nonce.
 * @param balance The account balance in wei.
 * @param storageRoot The root hash of the account's storage trie.
 * @param codeHash The Keccak-256 hash of the account's code.
 */
public record MptAccount(long nonce, UInt256 balance, Bytes32 storageRoot, Bytes32 codeHash) {

  /** Keccak-256 hash of empty code. */
  public static final Bytes32 EMPTY_CODE_HASH = Keccak.keccak256(Bytes.EMPTY);

  /** Root hash of an empty Merkle Patricia Trie. */
  public static final Bytes32 EMPTY_TRIE_HASH = MerkleTrie.EMPTY_TRIE_NODE_HASH;

  /**
   * RLP-encodes this account.
   *
   * @return The RLP encoding (nonce, balance, storageRoot, codeHash).
   */
  public Bytes toRlp() {
    return RLP.encode(
        out -> {
          out.startList();
          out.writeLongScalar(nonce);
          out.writeUInt256Scalar(balance);
          out.writeBytes(storageRoot);
          out.writeBytes(codeHash);
          out.endList();
        });
  }

  /**
   * Decodes an account from its RLP encoding.
   *
   * @param rlp The RLP encoding of the account.
   * @return The decoded account.
   */
  public static MptAccount fromRlp(final Bytes rlp) {
    final RLPInput in = new BytesValueRLPInput(rlp, false);
    in.enterList();
    final long nonce = in.readLongScalar();
    final UInt256 balance = in.readUInt256Scalar();
    final Bytes32 storageRoot = in.readBytes32();
    final Bytes32 codeHash = in.readBytes32();
    in.leaveList();
    return new MptAccount(nonce, balance, storageRoot, codeHash);
  }

  /**
   * Returns a copy of this account with the given storage root.
   *
   * @param newStorageRoot The new storage trie root hash.
   * @return A new account record with the updated storage root.
   */
  public MptAccount withStorageRoot(final Bytes32 newStorageRoot) {
    return new MptAccount(nonce, balance, newStorageRoot, codeHash);
  }

  /**
   * Returns a copy of this account with the given code hash.
   *
   * @param newCodeHash The new code hash.
   * @return A new account record with the updated code hash.
   */
  public MptAccount withCodeHash(final Bytes32 newCodeHash) {
    return new MptAccount(nonce, balance, storageRoot, newCodeHash);
  }

  /**
   * Returns a copy of this account with the given nonce and balance, preserving storage root and
   * code hash.
   *
   * @param newNonce The new nonce.
   * @param newBalance The new balance.
   * @return A new account record with the updated nonce and balance.
   */
  public MptAccount withNonceAndBalance(final long newNonce, final UInt256 newBalance) {
    return new MptAccount(newNonce, newBalance, storageRoot, codeHash);
  }
}
