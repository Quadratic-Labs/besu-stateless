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

public class BytesPackedBitSequenceFactory implements BitSequenceFactory<BytesPackedBitSequence> {
  /**
   * Default empty Sequence.
   *
   * @return Empty BitSequence.
   */
  @Override
  public BytesPackedBitSequence empty() {
    return new BytesPackedBitSequence(0);
  }

  /**
   * Get a BytesPackedBitSequence from a binary string representation.
   *
   * @param bits Binary string representation.
   * @return A string representation of the node.
   */
  @Override
  public BytesPackedBitSequence fromBinaryString(String bits) {
    if (bits == null || !bits.matches("[01]*")) {
      throw new IllegalArgumentException(
          "Input must be a binary string (only '0' and '1' allowed)");
    }

    BytesPackedBitSequence result = new BytesPackedBitSequence(bits.length());
    for (int i = 0; i < bits.length(); i++) {
      result.set(i, bits.charAt(i) == '1');
    }
    return result;
  }

  /**
   * Get a BytesPackedBitSequence from an Integer.
   *
   * @param value Integer value.
   * @return BytesPackedBitSequence representing value in big-endian format.
   */
  @Override
  public BytesPackedBitSequence fromInteger(int value) {
    // Should implement more efficient conversion
    return fromBinaryString(Integer.toBinaryString(value));
  }

  /**
   * Decode a BytesPackedBitSequence from the encoded form.
   *
   * @param encoded The encoded representation of a BytesPackedBitSequence
   * @return Decoded BytesPackedBitSequence.
   */
  @Override
  public BytesPackedBitSequence decode(byte[] encoded) {
    int encodedInt;
    int decodedInt;
    int decodedBitLength = 0;
    int power;
    byte[] outData = new byte[encoded.length];

    for (int i = 0; i < encoded.length; i++) {
      decodedInt = 0;
      encodedInt = Byte.toUnsignedInt(encoded[i]);
      power = 128;
      while (encodedInt > 0) {
        decodedBitLength++;
        if (encodedInt >= power) {
          encodedInt -= power;
          decodedInt += power;
        } else {
          encodedInt -= 1;
        }
        power >>= 1;
      }
      outData[i] = (byte) decodedInt;
    }
    return new BytesPackedBitSequence(decodedBitLength, outData);
  }
}
