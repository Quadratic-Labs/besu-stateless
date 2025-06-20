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

/** Interface representing a sequence of bits, used as prefixes in a Binary Trie. */
public abstract class BitSequence<T extends BitSequence<T>> implements Comparable<BitSequence<T>> {
  /**
   * Get a BitSequence factory
   *
   * @return A factory for building new BitSequences.
   */
  public abstract BitSequenceFactory<T> factory();

  /**
   * Get an identical copy.
   *
   * @return A new BitSequence equal to initial one.
   */
  public abstract T copy();

  /**
   * The binary string representation of the BitSequence.
   *
   * @return A string representation of the node.
   */
  public abstract String toBinaryString();

  /**
   * Convert BitSequence to an int if possible.
   *
   * @return the big-endian integer value.
   */
  public abstract int toInt();

  /**
   * Get length in bits
   *
   * @return Sequence's length in bits.
   */
  public abstract int length();

  /**
   * bit at a given index to a given value
   *
   * @param bit The boolean value to set at the end.
   * @return New BitSequence with added bit at the tail.
   */
  public abstract T add(boolean bit);

  /**
   * bit at a given index to a given value
   *
   * @param suffix The integer value to add at the end of the sequence.
   * @return New BitSequence with added bit at the tail.
   */
  public abstract T add(int suffix);

  /**
   * Get a bit at a given index.
   *
   * @param bitIndex The bit position to set.
   * @return The boolean value at given index.
   */
  public abstract boolean get(int bitIndex);

  /**
   * Set a bit at a given index to a given value
   *
   * @param bitIndex The bit position to set.
   * @param value The boolean value to set.
   */
  public abstract void set(int bitIndex, boolean value);

  /**
   * Get a slice of the BitSequence, starting at start in bits until the end.
   *
   * @param from The starting position.
   * @return A new BitSequence from the slice.
   */
  public abstract T slice(int from);

  /**
   * Get a slice of the BitSequence.
   *
   * @param from The starting position.
   * @param toExclusive The ending position.
   * @return A new BitSequence from the slice.
   */
  public abstract T slice(int from, int toExclusive);

  /**
   * Lexicographical comparaison of two BitSequences.
   *
   * @param other another BitSequence to compare to.
   * @return Comparaison value.
   */
  @Override
  public int compareTo(BitSequence<T> other) {
    int minLength = Math.min(this.length(), other.length());
    for (int i = 0; i < minLength; i++) {
      boolean bitA = this.get(i);
      boolean bitB = other.get(i);
      if (bitA != bitB) {
        return bitA ? 1 : -1;
      }
    }
    return Integer.compare(this.length(), other.length());
  }

  /**
   * Get the common prefix of two BitSequences.
   *
   * @param other The BitSequence to compare to.
   * @return BitSequence of the common prefix.
   */
  public T commonPrefix(BitSequence<T> other) {
    int maxSize = Math.min(this.length(), other.length());
    T result = factory().empty();
    for (int i = 0; i < maxSize; i++) {
      boolean thisBit = this.get(i);
      boolean otherBit = other.get(i);
      if (thisBit != otherBit) {
        break;
      }
      result.add(thisBit);
    }
    return result;
  }

  /**
   * Concatenate 2 BitSequences
   *
   * @param other BitSequence to concatenate
   * @return Concatenates BitSequence
   */
  public T concatenate(BitSequence<T> other) {
    T result = copy();
    for (int i = 0; i < other.length(); i++) {
      result.add(other.get(i));
    }
    return result;
  }

  /**
   * Encode a BitSequence from the encoded form.
   *
   * @return Encoded byte array representation of the BitSequence.
   */
  public abstract byte[] encode();
}
