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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

public class BitSequenceTest {

  @Test
  public void testSetAndGet() {
    BitSequence seq = new BitSequence(3);
    seq.setBit(0, true);
    seq.setBit(1, false);
    seq.setBit(2, true);

    assertThat(seq.getBitLength()).isEqualTo(3);
    assertThat(seq.getBit(0)).isTrue();
    assertThat(seq.getBit(1)).isFalse();
    assertThat(seq.getBit(2)).isTrue();
  }

  @Test
  public void testFromBinaryString() {
    BitSequence seq = BitSequence.fromBinaryString("10101");
    assertThat(seq.getBitLength()).isEqualTo(5);
    assertThat(seq.getBit(0)).isTrue();
    assertThat(seq.getBit(1)).isFalse();
    assertThat(seq.getBit(2)).isTrue();
    assertThat(seq.getBit(3)).isFalse();
    assertThat(seq.getBit(4)).isTrue();
  }

  @Test
  public void testLongFromBinaryString() {
    BitSequence seq = BitSequence.fromBinaryString("10101111001001110");
    assertThat(seq.getBitLength()).isEqualTo(17);
    assertThat(seq.getBit(0)).isTrue();
    assertThat(seq.getBit(-1)).isFalse();
    assertThat(seq.getBit(16)).isFalse();
  }

  @Test
  public void testToInt() {
    BitSequence seq = BitSequence.fromBinaryString("1011");
    assertThat(seq.toInt()).isEqualTo(11);
    BitSequence seq2 = BitSequence.fromBinaryString("1".repeat(32));
    assertThat(seq2.toInt()).isEqualTo(-1);
  }

  @Test
  public void testToIntOverflow() {
    BitSequence seq = BitSequence.fromBinaryString("1".repeat(33));
    assertThatThrownBy(seq::toInt).isInstanceOf(ArithmeticException.class);
  }

  @Test
  public void testAddBit() {
    BitSequence seq = BitSequence.fromBinaryString("101");
    BitSequence seq2 = seq.addBit(true);
    assertThat(seq2.toBinaryString()).isEqualTo("1011");
  }

  @Test
  public void testSlice() {
    BitSequence seq = BitSequence.fromBinaryString("1101011");
    BitSequence slice = seq.slice(2, 6); // 0101
    assertThat(slice.toBinaryString()).isEqualTo("0101");
  }

  @Test
  public void testSliceAlignedFastPath() {
    BitSequence seq = BitSequence.fromBinaryString("1111000");
    BitSequence slice = seq.slice(0, 7);
    assertThat(slice.toBinaryString()).isEqualTo("1111000");
    BitSequence slice2 = seq.slice(0);
    assertThat(slice2.toBinaryString()).isEqualTo("1111000");
  }

  @Test
  public void testCommonPrefix() {
    BitSequence seq = BitSequence.fromBinaryString("101100");
    BitSequence other = BitSequence.fromBinaryString("10100");
    BitSequence prefix = seq.commonPrefix(other);
    assertThat(prefix.toBinaryString()).isEqualTo("101");
  }

  @Test
  public void testEqualsAndHashCode() {
    BitSequence a = BitSequence.fromBinaryString("1101");
    BitSequence b = BitSequence.fromBinaryString("1101");
    BitSequence c = BitSequence.fromBinaryString("1100");

    assertThat(a).isEqualTo(b);
    assertThat(a).isNotEqualTo(c);
    assertThat(a).hasSameHashCodeAs(b);
  }

  @Test
  public void testCompareTo() {
    BitSequence a = BitSequence.fromBinaryString("101");
    BitSequence b = BitSequence.fromBinaryString("110");
    BitSequence c = BitSequence.fromBinaryString("101");

    assertThat(a.compareTo(b) < 0).isTrue();
    assertThat(a.compareTo(c)).isEqualTo(0);
    assertThat(b.compareTo(a) > 0).isTrue();
  }

  @Test
  public void testEncodeEmpty() {
    BitSequence a = new BitSequence(0);
    byte[] encoded = a.encode();
    assertThat(encoded.length).isEqualTo(0);
  }

  @Test
  public void testEncodeDecodeOneByte() {
    BitSequence a = BitSequence.fromBinaryString("1101");
    byte[] encoded = a.encode();
    assertThat(encoded[0]).isEqualTo((byte) 209);
    BitSequence b = BitSequence.decode(encoded);
    assertThat(a).isEqualTo(b);
  }

  @Test
  public void testEncodeDecodeMultiBytes() {
    BitSequence a = BitSequence.fromBinaryString("1101001001");
    byte[] encoded = a.encode();
    assertThat(encoded[0]).isEqualTo((byte) Integer.parseInt("11010101", 2));
    assertThat(encoded[1]).isEqualTo((byte) Integer.parseInt("00100010", 2));
    BitSequence b = BitSequence.decode(encoded);
    assertThat(a).isEqualTo(b);
  }

  @Test
  public void testEncodeDecodeMultiBytesFullyPacked() {
    BitSequence a = BitSequence.fromBinaryString("11111110000000");
    byte[] encoded = a.encode();
    assertThat(encoded.length).isEqualTo(2);
    assertThat(encoded[0]).isEqualTo((byte) Integer.parseInt("11111110", 2));
    assertThat(encoded[1]).isEqualTo((byte) Integer.parseInt("00000111", 2));
    BitSequence b = BitSequence.decode(encoded);
    assertThat(a).isEqualTo(b);
  }
}
