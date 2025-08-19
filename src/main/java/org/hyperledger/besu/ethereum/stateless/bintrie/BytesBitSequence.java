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

import org.apache.tuweni.bytes.Bytes;

/**
 * Class representing a sequence of bits, used as prefixes in a Binary Trie.
 *
 * <p>Implementation using one byte per bit.
 */
public class BytesBitSequence extends BitSequence<BytesBitSequence> {
  private static final BytesBitSequenceFactory FACTORY = new BytesBitSequenceFactory();

  public final byte[] data;
  public final int bitLength;

  public BytesBitSequence(int bitLength) {
    ensureBitLength(bitLength);
    this.data = new byte[bitLength];
    this.bitLength = bitLength;
  }

  public BytesBitSequence(byte[] data) {
    bitLength = data.length;
    ensureBitLength(bitLength);
    this.data = data.clone();
  }

  @Override
  public BytesBitSequenceFactory factory() {
    return FACTORY;
  }

  public static BytesBitSequence empty() {
    return FACTORY.empty();
  }

  public static BytesBitSequence fromBinaryString(String bits) {
    return FACTORY.fromBinaryString(bits);
  }

  public static BytesBitSequence fromInteger(int value) {
    return FACTORY.fromInteger(value);
  }

  public static BytesBitSequence fromByte(byte value) {
    return FACTORY.fromByte(value);
  }

  public static BytesBitSequence fromBytes(Bytes value) {
    return FACTORY.fromBytes(value);
  }

  public static BytesBitSequence decode(byte[] encoded) {
    return FACTORY.decode(encoded);
  }

  /**
   * Copy itself.
   *
   * @return New identical BytesBitSequence.
   */
  @Override
  public BytesBitSequence copy() {
    return new BytesBitSequence(data);
  }

  /**
   * The binary string representation of the BytesBitSequence.
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

  /**
   * The hex-like representation of the BitSequence.
   *
   * <p>The representation chunks 4 bits into a hexadecimal. The remaining up to 3 bits are appended
   * with toBinaryString(), dot-separated from the hexadecimals.
   *
   * @return hexlike representation.
   */
  @Override
  public String toHexString() {
    StringBuilder sb = new StringBuilder();
    sb.append("0x");
    int i;
    for (i = 0; i < bitLength - 3; i += 4) {
      int value = 0;
      for (int j = 0; j < 4; j++) {
        value = (value << 1) | data[i + j];
      }
      sb.append(String.format("%01x", value));
    }
    // Remaining 0-3 bits
    if (i < bitLength) {
      sb.append("."); // There is at least 1 remaining bit
    }
    for (int j = i; j < bitLength; j++) {
      sb.append(get(j) ? '1' : '0');
    }
    return sb.toString();
  }

  @Override
  public int toInt() {
    if (bitLength > 32) {
      throw new ArithmeticException("BytesBitSequence too long to convert to int");
    }
    if (bitLength == 0) {
      throw new IllegalArgumentException("Cannot convert empty BytesBitSequence to int");
    }

    int result = 0;
    for (byte b : data) {
      result = (result << 1) | (b & 0xFF);
    }
    return result;
  }

  /**
   * The binary string representation of the BitSequence.
   *
   * @return A byte array representation of the node.
   */
  @Override
  public byte[] toBytes() {
    int size = (bitLength + 7) / 8;
    byte[] result = new byte[size];
    for (int i = 0; i < size - 1; i++) {
      int res = 0;
      for (int j = 8 * i; j < 8 * (i + 1); j++) {
        res = (res << 1) | data[j];
      }
      result[i] = (byte) res;
    }
    // Last byte same but truncated
    int res = 0;
    for (int j = 8 * (size - 1); j < bitLength; j++) {
      res = (res << 1) | data[j];
    }
    result[size - 1] = (byte) res;
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
   * @return New BytesBitSequence with added bit at the tail.
   */
  @Override
  public BytesBitSequence add(boolean bit) {
    BytesBitSequence newSeq = new BytesBitSequence(bitLength + 1);
    System.arraycopy(this.data, 0, newSeq.data, 0, bitLength);
    newSeq.set(bitLength, bit);
    return newSeq;
  }

  /**
   * Add minimal BitSequence representation of suffix to the sequence.
   *
   * @param value The integer value to add at the end of the sequence.
   * @return New BitSequence with added bit at the tail.
   */
  @Override
  public BytesBitSequence add(int value) {
    int len = 0;
    boolean[] out = new boolean[8];
    do {
      int remainder = value % 2;
      value = value / 2;
      out[len] = remainder == 1;
      len++;
    } while (value > 0);
    BytesBitSequence tail = new BytesBitSequence(len);
    for (int i = 0; i < len; i++) {
      tail.set(i, out[len - i - 1]);
    }
    return concatenate(tail);
  }

  /**
   * Add fixed-width BitSequence representation of suffix to the sequence.
   *
   * @param value The integer value to add at the end of the sequence.
   * @param width The fixed number of bits in the sequence.
   * @return New BitSequence with added bit at the tail.
   */
  @Override
  public BytesBitSequence add(int value, int width) {
    if (width > 31 || width < 0) {
      throw new RuntimeException("Width must be from 0 to 31");
    }
    if (width == 0) {
      return copy();
    }
    BytesBitSequence tail = new BytesBitSequence(width);
    for (int i = 0; i < width; i++) {
      int remainder = value % 2;
      value = value / 2;
      tail.set(width - 1 - i, remainder == 1);
    }
    return concatenate(tail);
  }

  /**
   * Set a bit at a given index to a given value
   *
   * @param bitIndex The bit position to set.
   * @param value The boolean value to set.
   */
  @Override
  public void set(int bitIndex, boolean value) {
    bitIndex = ensureBitIndex(bitIndex);
    data[bitIndex] = (byte) (value ? 1 : 0);
  }

  public void setAll(int bitIndex, byte value) {
    bitIndex = ensureBitIndex(bitIndex);
    int val = value & 0xFF;
    for (int i = 0; i < 8; i++) {
      int remainder = val % 2;
      val = val / 2;
      set(bitIndex + 7 - i, remainder == 1);
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
    bitIndex = ensureBitIndex(bitIndex);
    return data[bitIndex] != 0;
  }

  /**
   * Get a slice of the BytesBitSequence, starting at start in bits until the end.
   *
   * @param from The starting position.
   * @return A new BytesBitSequence from the slice.
   */
  @Override
  public BytesBitSequence slice(int from) {
    return slice(from, bitLength);
  }

  /**
   * Get a slice of the BytesBitSequence.
   *
   * @param from The starting position.
   * @param toExclusive The ending position.
   * @return A new BytesBitSequence from the slice.
   */
  @Override
  public BytesBitSequence slice(int from, int toExclusive) {
    if (from < 0 || toExclusive > bitLength || from > toExclusive) {
      throw new IndexOutOfBoundsException("Invalid slice range");
    }

    int sliceLength = toExclusive - from;
    if (sliceLength == 0) {
      return new BytesBitSequence(0);
    }

    BytesBitSequence result = new BytesBitSequence(sliceLength);
    System.arraycopy(this.data, from, result.data, 0, sliceLength);
    return result;
  }

  /**
   * Concatenate 2 BytesBitSequences.
   *
   * @param other BytesBitSequence to concatenate
   * @return concatenated BytesBitSequence
   */
  public BytesBitSequence concatenate(BytesBitSequence other) {
    if (other.bitLength == 0) {
      return copy();
    }
    int totalBits = bitLength + other.bitLength;
    BytesBitSequence result = new BytesBitSequence(totalBits);
    System.arraycopy(data, 0, result.data, 0, bitLength);
    System.arraycopy(other.data, 0, result.data, bitLength, other.length());
    return result;
  }

  /**
   * Get the common prefix of two BytesBitSequences.
   *
   * @param other The BytesBitSequence to compare to.
   * @return BytesBitSequence of the common prefix.
   */
  public BytesBitSequence commonPrefix(BytesBitSequence other) {
    int length = 0;
    for (int i = 0; i < length() && i < other.length(); i++) {
      int diff = this.data[i] ^ other.data[i];
      if (diff == 0) { // All bits are the same
        length += 1;
      } else {
        break;
      }
    }
    return slice(0, length);
  }

  /**
   * Encode a BytesBitSequence from the encoded form.
   *
   * @return Encoded byte array representation of the BytesBitSequence.
   */
  @Override
  public byte[] encode() {
    // Pack 8bits into a byte, except for last byte.
    // Last byte is prefix-encoded, so up to 7bits.
    // Special case: empty -> empty
    if (length() == 0) {
      return new byte[0];
    }
    int nBytes = 1 + length() / 8;
    byte[] out = new byte[nBytes];

    // Pack 8bits into a byte, except for last byte.
    for (int i = 0; i < nBytes - 1; i++) {
      int j = 8 * i;
      out[i] = (byte) slice(j, j + 8).toInt();
    }

    // Last byte is prefix-encoded, so 0-7bits.
    BytesBitSequence tail = slice(8 * (nBytes - 1));
    if (tail.length() == 0) {
      out[nBytes - 1] = (byte) 0;
    } else {
      int tailInt = tail.toInt();
      int encodedInt = tailInt << (8 - tail.length()); // right padded
      encodedInt += tail.nZeroes();
      out[nBytes - 1] = (byte) encodedInt;
    }
    return out;
  }

  /**
   * Get a string representation of the node.
   *
   * @return A string representation of the node.
   */
  @Override
  public String toString() {
    return String.format("BytesBitSequence(%s)", toBinaryString());
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;

    BytesBitSequence other = (BytesBitSequence) obj;
    if (this.bitLength != other.bitLength) return false;

    return Arrays.equals(this.data, 0, this.length(), other.data, 0, other.length());
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

  private int ensureBitIndex(int bitIndex) {
    if (bitIndex < -bitLength || bitIndex >= bitLength) {
      throw new IndexOutOfBoundsException();
    }
    return bitIndex < 0 ? bitLength + bitIndex : bitIndex;
  }

  private int nZeroes() {
    int result = 0;
    for (byte b : data) {
      if (b == 0) {
        result++;
      }
    }
    return result;
  }
}
