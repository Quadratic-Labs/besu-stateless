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
package org.hyperledger.besu.ethereum.stateless.bintrie;

public interface BitSequenceFactory<K extends BitSequence<K>> {
  /**
   * Default empty Sequence.
   *
   * @return Empty BitSequence.
   */
  public K empty();

  /**
   * Get a BitSequence from a binary string representation.
   *
   * @param bits Binary string representation.
   * @return A string representation of the node.
   */
  public K fromBinaryString(String bits);

  /**
   * Get a BitSequence from an Integer.
   *
   * @param value Integer value.
   * @return BitSequence representing value in big-endian format.
   */
  public K fromInteger(int value);

  /**
   * Decode a BitSequence from the encoded form.
   *
   * @param encoded The encoded representation of a BytesPackedBitSequence
   * @return Decoded BitSequence.
   */
  public K decode(byte[] encoded);
}
