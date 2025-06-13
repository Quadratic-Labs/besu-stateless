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

/** Class representing a sequence of bits, used as prefixes in a Binary Trie. */
public class BitSequence implements Comparable<BitSequence> {
  // Internally represented by 7bits per byte to simplify encoding.
  private static final int N_BITS_PER_BYTE = 7;

  private final byte[] data;
  private final int bitLength;

  public BitSequence(int bitLength) {
    ensureBitLength(bitLength);
    this.data = new byte[getByteLength(bitLength)];
    this.bitLength = bitLength;
  }

  public BitSequence(int bitLength, byte[] data) {
    ensureBitLength(bitLength);
    this.data = data.clone();
    this.bitLength = bitLength;
  }

  /**
   * Get a BitSequence from a binary string representation.
   *
   * @param bits Binary string representation.
   * @return A string representation of the node.
   */
  public static BitSequence fromBinaryString(String bits) {
    if (bits == null || !bits.matches("[01]*")) {
      throw new IllegalArgumentException(
          "Input must be a binary string (only '0' and '1' allowed)");
    }

    BitSequence result = new BitSequence(bits.length());
    for (int i = 0; i < bits.length(); i++) {
      result.setBit(i, bits.charAt(i) == '1');
    }
    return result;
  }

  /**
   * The binary string representation of the BitSequence.
   *
   * @return A string representation of the node.
   */
  public String toBinaryString() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < bitLength; i++) {
      sb.append(getBit(i) ? '1' : '0');
    }
    return sb.toString();
  }

  /**
   * Get length in bits
   *
   * @return Sequence's length in bits.
   */
  public int getBitLength() {
    return bitLength;
  }

  /**
   * bit at a given index to a given value
   *
   * @param bit The boolean value to set at the end.
   * @return New BitSequence with added bit at the tail.
   */
  public BitSequence addBit(boolean bit) {
    BitSequence newSeq = new BitSequence(bitLength + 1);
    int byteCount = newSeq.getByteLength();
    System.arraycopy(this.data, 0, newSeq.data, 0, byteCount);
    newSeq.setBit(bitLength, bit);
    return newSeq;
  }

  /**
   * Set a bit at a given index to a given value
   *
   * @param bitIndex The bit position to set.
   * @param value The boolean value to set.
   */
  public void setBit(int bitIndex, boolean value) {
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
  public boolean getBit(int bitIndex) {
    if (bitIndex < -bitLength || bitIndex >= bitLength) {
      throw new IndexOutOfBoundsException();
    }
    bitIndex = bitIndex < 0 ? bitLength + bitIndex : bitIndex;
    int byteIndex = bitIndex / N_BITS_PER_BYTE;
    int bitPos = 7 - (bitIndex % N_BITS_PER_BYTE);
    return (data[byteIndex] & (1 << bitPos)) != 0;
  }

  /**
   * Get BitSequence as byte array.
   *
   * @return Underlying byte array.
   */
  public byte[] getBytes() {
    return data.clone();
  }

  /**
   * Get a slice of the BitSequence, starting at start in bits until the end.
   *
   * @param from The starting position.
   * @return A new BitSequence from the slice.
   */
  public BitSequence slice(int from) {
    return slice(from, bitLength);
  }

  /**
   * Get a slice of the BitSequence.
   *
   * @param from The starting position.
   * @param toExclusive The ending position.
   * @return A new BitSequence from the slice.
   */
  public BitSequence slice(int from, int toExclusive) {
    if (from < 0 || toExclusive > bitLength || from > toExclusive) {
      throw new IndexOutOfBoundsException("Invalid slice range");
    }

    int sliceLength = toExclusive - from;
    if (sliceLength == 0) {
      return new BitSequence(0);
    }

    BitSequence result = new BitSequence(sliceLength);

    int startByte = from / N_BITS_PER_BYTE;
    int startBitOffset = from % N_BITS_PER_BYTE;

    if (startBitOffset == 0 && sliceLength % 8 == 0) {
      System.arraycopy(this.data, startByte, result.data, 0, result.getByteLength());
      return result;
    }

    // General case: slower bitwise copy
    // ENH: use arraycopy for all full bytes.
    for (int i = 0; i < sliceLength; i++) {
      result.setBit(i, this.getBit(from + i));
    }

    return result;
  }

  /**
   * Lexicographical comparaison of two BitSequences.
   *
   * @param other another BitSequence to compare to.
   * @return Comparaison value.
   */
  @Override
  public int compareTo(BitSequence other) {
    int minLength = Math.min(this.bitLength, other.bitLength);
    for (int i = 0; i < minLength; i++) {
      boolean bitA = this.getBit(i);
      boolean bitB = other.getBit(i);
      if (bitA != bitB) {
        return bitA ? 1 : -1;
      }
    }
    return Integer.compare(this.bitLength, other.bitLength);
  }

  /**
   * Get the common prefix of two BitSequences.
   *
   * @param other The BitSequence to compare to.
   * @return BitSequence of the common prefix.
   */
  public BitSequence commonPrefix(BitSequence other) {
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
      return new BitSequence(0);
    }
    int nBytes = getByteLength(length);
    out = new byte[nBytes];
    if (length % N_BITS_PER_BYTE == 0) {
      System.arraycopy(this.data, 0, out, 0, nBytes);
    } else {
      System.arraycopy(this.data, 0, out, 0, nBytes - 1);
      out[nBytes - 1] = (byte) (this.data[nBytes - 1] & ~diff);
    }
    return new BitSequence(length, out);

    // BitSequence prefix = new BitSequence(maxBits);

    // for (int i = 0; i < maxBits; i++) {
    //   boolean bitA = this.getBit(i);
    //   boolean bitB = other.getBit(i);
    //   if (bitA != bitB) {
    //     break;
    //   }
    //   prefix.setBit(i, bitA);
    // }
    // return prefix;
  }

  /**
   * Convert to an int
   *
   * @return the big-endian integer value.
   */
  public int toInt() {
    if (bitLength > 32) {
      throw new ArithmeticException("BitSequence too long to convert to int");
    }
    if (bitLength == 0) {
      throw new IllegalArgumentException("Cannot convert empty BitSequence to int");
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
   * Encode a BitSequence from the encoded form.
   *
   * @return Encoded byte array representation of the BitSequence.
   */
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
   * Decode a BitSequence from the encoded form.
   *
   * @param encoded The encoded representation of a BitSequence
   * @return Decoded BitSequence.
   */
  public static BitSequence decode(byte[] encoded) {
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
    return new BitSequence(decodedBitLength, outData);
  }

  /**
   * Get a string representation of the node.
   *
   * @return A string representation of the node.
   */
  @Override
  public String toString() {
    return String.format("BitSequence(%s)", toBinaryString());
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;

    BitSequence other = (BitSequence) obj;
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
