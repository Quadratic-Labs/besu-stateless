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

import java.util.Arrays;

/**
 * Class representing a sequence of bits, used as prefixes in a Binary Trie. Implementation using
 * packed bits into byte array.
 */
public class BytesPackedBitSequence extends BitSequence<BytesPackedBitSequence> {
  // Internally represented by 7bits per byte to simplify encoding.
  private static final int N_BITS_PER_BYTE = 7;
  private static final BytesPackedBitSequenceFactory FACTORY = new BytesPackedBitSequenceFactory();

  private final byte[] data;
  private final int bitLength;

  public BytesPackedBitSequence(int bitLength) {
    ensureBitLength(bitLength);
    this.data = new byte[getByteLength(bitLength)];
    this.bitLength = bitLength;
  }

  public BytesPackedBitSequence(int bitLength, byte[] data) {
    ensureBitLength(bitLength);
    this.data = data.clone();
    this.bitLength = bitLength;
  }

  @Override
  public BytesPackedBitSequenceFactory factory() {
    return FACTORY;
  }

  public static BytesPackedBitSequence empty() {
    return FACTORY.empty();
  }

  public static BytesPackedBitSequence fromBinaryString(String bits) {
    return FACTORY.fromBinaryString(bits);
  }

  public static BytesPackedBitSequence fromInteger(int value) {
    return FACTORY.fromInteger(value);
  }

  public static BytesPackedBitSequence decode(byte[] encoded) {
    return FACTORY.decode(encoded);
  }

  /**
   * Copy itself.
   *
   * @return New identical BytesPackedBitSequence.
   */
  @Override
  public BytesPackedBitSequence copy() {
    return new BytesPackedBitSequence(bitLength, data.clone());
  }

  /**
   * The binary string representation of the BytesPackedBitSequence.
   *
   * @return A string representation of the node.
   */
  @Override
  public String toBinaryString() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < bitLength; i++) {
      sb.append(get(i) ? '1' : '0');
    }
    return sb.toString();
  }

  @Override
  public int toInt() {
    if (bitLength > 32) {
      throw new ArithmeticException("BytesPackedBitSequence too long to convert to int");
    }
    if (bitLength == 0) {
      throw new IllegalArgumentException("Cannot convert empty BytesPackedBitSequence to int");
    }

    int result = 0;
    int nBytes = data.length;
    // Process least significant byte, possibly partially filled.
    int remainingLength = bitLength % N_BITS_PER_BYTE;
    if (remainingLength == 0) {
      remainingLength = N_BITS_PER_BYTE;
    }
    result += Byte.toUnsignedInt(data[nBytes - 1]) >> (8 - remainingLength);
    int power = 1 << remainingLength;
    // Process full bytes right to left
    for (int i = nBytes - 2; i >= 0; i--) {
      result += (Byte.toUnsignedInt(data[i]) >> 1) * power;
      power <<= N_BITS_PER_BYTE;
    }
    return result;
  }

  /**
   * Get length in bits
   *
   * @return Sequence's length in bits.
   */
  @Override
  public int length() {
    return bitLength;
  }

  /**
   * Add a new bit at the end of the Sequence.
   *
   * @param bit The boolean value to set at the end.
   * @return New BytesPackedBitSequence with added bit at the tail.
   */
  @Override
  public BytesPackedBitSequence add(boolean bit) {
    BytesPackedBitSequence newSeq = new BytesPackedBitSequence(bitLength + 1);
    int byteCount = newSeq.getByteLength();
    System.arraycopy(this.data, 0, newSeq.data, 0, byteCount);
    newSeq.set(bitLength, bit);
    return newSeq;
  }

  /**
   * Add new bits at the end of the Sequence from an Integer.
   *
   * @param suffix The integer value to add at the end of the sequence
   * @return New BytesPackedBitSequence with added bit at the tail.
   */
  @Override
  public BytesPackedBitSequence add(int suffix) {
    return concatenate(FACTORY.fromInteger(suffix));
  }

  /**
   * Set a bit at a given index to a given value
   *
   * @param bitIndex The bit position to set.
   * @param value The boolean value to set.
   */
  @Override
  public void set(int bitIndex, boolean value) {
    if (bitIndex < -bitLength || bitIndex >= bitLength) {
      throw new IndexOutOfBoundsException();
    }
    bitIndex = bitIndex < 0 ? bitLength + bitIndex : bitIndex;
    int byteIndex = bitIndex / N_BITS_PER_BYTE;
    int bitPos = 7 - (bitIndex % N_BITS_PER_BYTE);
    if (value) {
      data[byteIndex] = (byte) (data[byteIndex] | (1 << bitPos));
    } else {
      data[byteIndex] = (byte) (data[byteIndex] & ~(1 << bitPos));
    }
  }

  /**
   * Get a bit at a given index.
   *
   * @param bitIndex The bit position to set.
   * @return The boolean value at given index.
   */
  @Override
  public boolean get(int bitIndex) {
    if (bitIndex < -bitLength || bitIndex >= bitLength) {
      throw new IndexOutOfBoundsException();
    }
    bitIndex = bitIndex < 0 ? bitLength + bitIndex : bitIndex;
    int byteIndex = bitIndex / N_BITS_PER_BYTE;
    int bitPos = 7 - (bitIndex % N_BITS_PER_BYTE);
    return (data[byteIndex] & (1 << bitPos)) != 0;
  }

  /**
   * Get BytesPackedBitSequence as byte array.
   *
   * @return Underlying byte array.
   */
  public byte[] getBytes() {
    return data.clone();
  }

  /**
   * Get a slice of the BytesPackedBitSequence, starting at start in bits until the end.
   *
   * @param from The starting position.
   * @return A new BytesPackedBitSequence from the slice.
   */
  @Override
  public BytesPackedBitSequence slice(int from) {
    return slice(from, bitLength);
  }

  /**
   * Get a slice of the BytesPackedBitSequence.
   *
   * @param from The starting position.
   * @param toExclusive The ending position.
   * @return A new BytesPackedBitSequence from the slice.
   */
  @Override
  public BytesPackedBitSequence slice(int from, int toExclusive) {
    if (from < 0 || toExclusive > bitLength || from > toExclusive) {
      throw new IndexOutOfBoundsException("Invalid slice range");
    }

    int sliceLength = toExclusive - from;
    if (sliceLength == 0) {
      return new BytesPackedBitSequence(0);
    }

    BytesPackedBitSequence result = new BytesPackedBitSequence(sliceLength);

    int startByte = from / N_BITS_PER_BYTE;
    int startBitOffset = from % N_BITS_PER_BYTE;

    if (startBitOffset == 0 && sliceLength % 8 == 0) {
      System.arraycopy(this.data, startByte, result.data, 0, result.getByteLength());
      return result;
    }

    // General case: slower bitwise copy
    // ENH: use arraycopy for all full bytes.
    for (int i = 0; i < sliceLength; i++) {
      result.set(i, this.get(from + i));
    }

    return result;
  }

  /**
   * Concatenate 2 BytesPackedBitSequences.
   *
   * @param other BytesPackedBitSequence to concatenate
   * @return concatenated BytesPackedBitSequence
   */
  public BytesPackedBitSequence concatenate(BytesPackedBitSequence other) {
    if (other.bitLength == 0) {
      return new BytesPackedBitSequence(bitLength, data);
    }
    int offset = bitLength % N_BITS_PER_BYTE;
    int totalBits = bitLength + other.bitLength;
    int thisNBytes = getByteLength();
    int otherNBytes = other.getByteLength();
    BytesPackedBitSequence result = new BytesPackedBitSequence(totalBits);
    System.arraycopy(data, 0, result.data, 0, thisNBytes);
    if (offset == 0) {
      System.arraycopy(other.data, 0, result.data, thisNBytes, otherNBytes);
    } else {
      result.data[thisNBytes - 1] =
          (byte) (result.data[thisNBytes - 1] & (other.data[0] >> (N_BITS_PER_BYTE - offset)));
      for (int i = 1; i < otherNBytes; i++) {
        result.data[thisNBytes + i - 1] =
            (byte) ((other.data[i - 1] << offset) & (other.data[i] >> (N_BITS_PER_BYTE - offset)));
      }
      if (totalBits % N_BITS_PER_BYTE != 0) { // Left over bits
        result.data[thisNBytes + otherNBytes - 1] = (byte) (other.data[otherNBytes - 1] << offset);
      }
    }
    return result;
  }

  /**
   * Get the common prefix of two BytesPackedBitSequences.
   *
   * @param other The BytesPackedBitSequence to compare to.
   * @return BytesPackedBitSequence of the common prefix.
   */
  public BytesPackedBitSequence commonPrefix(BytesPackedBitSequence other) {
    int diff = 0;
    int length = 0;
    int maxBytes = Math.min(this.getByteLength(), other.getByteLength());
    byte[] out;
    for (int i = 0; i < maxBytes; i++) {
      diff = this.data[i] ^ other.data[i];
      if (diff == 0) { // All bits are the same
        length += N_BITS_PER_BYTE;
      } else {
        int n_bits = Integer.numberOfLeadingZeros(diff);
        if (n_bits == 0) {
          break;
        }
        length += n_bits - 24;
        break;
      }
    }
    if (length == 0) {
      return new BytesPackedBitSequence(0);
    }
    int nBytes = getByteLength(length);
    out = new byte[nBytes];
    if (length % N_BITS_PER_BYTE == 0) {
      System.arraycopy(this.data, 0, out, 0, nBytes);
    } else {
      System.arraycopy(this.data, 0, out, 0, nBytes - 1);
      out[nBytes - 1] = (byte) (this.data[nBytes - 1] & ~diff);
    }
    return new BytesPackedBitSequence(length, out);
  }

  /**
   * Encode a BytesPackedBitSequence from the encoded form.
   *
   * @return Encoded byte array representation of the BytesPackedBitSequence.
   */
  @Override
  public byte[] encode() {
    int encodedInt;
    int nBytes = getByteLength();
    byte[] out = new byte[nBytes];
    if (nBytes == 0) {
      return out;
    }
    for (int i = 0; i < nBytes - 1; i++) {
      encodedInt = Byte.toUnsignedInt(data[i]);
      out[i] = (byte) (encodedInt + N_BITS_PER_BYTE - Integer.bitCount(encodedInt));
    }
    // Last byte may be incomplete
    encodedInt = Byte.toUnsignedInt(data[nBytes - 1]);
    int nBits = (bitLength % N_BITS_PER_BYTE);
    if (nBits == 0) nBits = N_BITS_PER_BYTE;
    out[nBytes - 1] = (byte) (encodedInt + nBits - Integer.bitCount(encodedInt));
    return out;
  }

  /**
   * Get a string representation of the node.
   *
   * @return A string representation of the node.
   */
  @Override
  public String toString() {
    return String.format("BytesPackedBitSequence(%s)", toBinaryString());
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;

    BytesPackedBitSequence other = (BytesPackedBitSequence) obj;
    if (this.bitLength != other.bitLength) return false;

    int nBytes = getByteLength();
    return Arrays.equals(this.data, 0, nBytes, other.data, 0, nBytes);
  }

  @Override
  public int hashCode() {
    int result = Integer.hashCode(bitLength);
    for (int i = 0; i < data.length; i++) {
      result = 31 * result + Byte.hashCode(data[i]);
    }
    return result;
  }

  private void ensureBitLength(int bitLength) {
    if (bitLength < 0) {
      throw new IllegalArgumentException("Bit length must be non-negative");
    }
  }

  private int getByteLength() {
    return (this.bitLength + N_BITS_PER_BYTE - 1) / N_BITS_PER_BYTE;
  }

  private int getByteLength(int bitLength) {
    return (bitLength + N_BITS_PER_BYTE - 1) / N_BITS_PER_BYTE;
  }
}
