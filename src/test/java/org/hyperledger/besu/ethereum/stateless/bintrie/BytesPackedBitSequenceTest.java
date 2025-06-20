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

public class BytesPackedBitSequenceTest {

  @Test
  public void testSetAndGet() {
    BytesPackedBitSequence seq = new BytesPackedBitSequence(3);
    seq.set(0, true);
    seq.set(1, false);
    seq.set(2, true);

    assertThat(seq.length()).isEqualTo(3);
    assertThat(seq.get(0)).isTrue();
    assertThat(seq.get(1)).isFalse();
    assertThat(seq.get(2)).isTrue();
  }

  @Test
  public void testFromBinaryString() {
    BytesPackedBitSequence seq = BytesPackedBitSequence.fromBinaryString("10101");
    assertThat(seq.length()).isEqualTo(5);
    assertThat(seq.get(0)).isTrue();
    assertThat(seq.get(1)).isFalse();
    assertThat(seq.get(2)).isTrue();
    assertThat(seq.get(3)).isFalse();
    assertThat(seq.get(4)).isTrue();
  }

  @Test
  public void testLongFromBinaryString() {
    BytesPackedBitSequence seq = BytesPackedBitSequence.fromBinaryString("10101111001001110");
    assertThat(seq.length()).isEqualTo(17);
    assertThat(seq.get(0)).isTrue();
    assertThat(seq.get(-1)).isFalse();
    assertThat(seq.get(16)).isFalse();
  }

  @Test
  public void testToInt() {
    BytesPackedBitSequence seq = BytesPackedBitSequence.fromBinaryString("1011");
    assertThat(seq.toInt()).isEqualTo(11);
    BytesPackedBitSequence seq2 = BytesPackedBitSequence.fromBinaryString("1".repeat(32));
    assertThat(seq2.toInt()).isEqualTo(-1);
  }

  @Test
  public void testToIntOverflow() {
    BytesPackedBitSequence seq = BytesPackedBitSequence.fromBinaryString("1".repeat(33));
    assertThatThrownBy(seq::toInt).isInstanceOf(ArithmeticException.class);
  }

  @Test
  public void testAddBit() {
    BytesPackedBitSequence seq = BytesPackedBitSequence.fromBinaryString("101");
    BytesPackedBitSequence seq2 = seq.add(true);
    assertThat(seq2.toBinaryString()).isEqualTo("1011");
  }

  @Test
  public void testSlice() {
    BytesPackedBitSequence seq = BytesPackedBitSequence.fromBinaryString("1101011");
    BytesPackedBitSequence slice = seq.slice(2, 6); // 0101
    assertThat(slice.toBinaryString()).isEqualTo("0101");
  }

  @Test
  public void testSliceAlignedFastPath() {
    BytesPackedBitSequence seq = BytesPackedBitSequence.fromBinaryString("1111000");
    BytesPackedBitSequence slice = seq.slice(0, 7);
    assertThat(slice.toBinaryString()).isEqualTo("1111000");
    BytesPackedBitSequence slice2 = seq.slice(0);
    assertThat(slice2.toBinaryString()).isEqualTo("1111000");
  }

  @Test
  public void testCommonPrefix() {
    BytesPackedBitSequence seq = BytesPackedBitSequence.fromBinaryString("101100");
    BytesPackedBitSequence other = BytesPackedBitSequence.fromBinaryString("10100");
    BytesPackedBitSequence prefix = seq.commonPrefix(other);
    assertThat(prefix.toBinaryString()).isEqualTo("101");
  }

  @Test
  public void testEqualsAndHashCode() {
    BytesPackedBitSequence a = BytesPackedBitSequence.fromBinaryString("1101");
    BytesPackedBitSequence b = BytesPackedBitSequence.fromBinaryString("1101");
    BytesPackedBitSequence c = BytesPackedBitSequence.fromBinaryString("1100");

    assertThat(a).isEqualTo(b);
    assertThat(a).isNotEqualTo(c);
    assertThat(a).hasSameHashCodeAs(b);
  }

  @Test
  public void testCompareTo() {
    BytesPackedBitSequence a = BytesPackedBitSequence.fromBinaryString("101");
    BytesPackedBitSequence b = BytesPackedBitSequence.fromBinaryString("110");
    BytesPackedBitSequence c = BytesPackedBitSequence.fromBinaryString("101");

    assertThat(a.compareTo(b) < 0).isTrue();
    assertThat(a.compareTo(c)).isEqualTo(0);
    assertThat(b.compareTo(a) > 0).isTrue();
  }

  @Test
  public void testLexicographicOrder() {
    BytesPackedBitSequence previous = BytesPackedBitSequence.fromInteger(0);
    for (int i = 1; i < 128; i++) {
      BytesPackedBitSequence current = BytesPackedBitSequence.fromInteger(i);
      assertThat(previous.compareTo(current) < 0)
          .as(
              String.format(
                  "Test LexOrder %s < %s", previous.toBinaryString(), current.toBinaryString()))
          .isTrue();
      byte[] prev = previous.encode();
      byte[] curr = current.encode();
      assertThat(prev[0] < (curr[0] & 0xFF))
          .as(String.format("Test Encoded LexOrder %s < %s", prev[0], curr[0]))
          .isTrue();
    }
  }

  @Test
  public void testEncodeEmpty() {
    BytesPackedBitSequence a = new BytesPackedBitSequence(0);
    byte[] encoded = a.encode();
    assertThat(encoded.length).isEqualTo(0);
  }

  @Test
  public void testEncodeDecodeOneByte() {
    BytesPackedBitSequence a = BytesPackedBitSequence.fromBinaryString("1101");
    byte[] encoded = a.encode();
    assertThat(encoded[0]).isEqualTo((byte) 209);
    BytesPackedBitSequence b = BytesPackedBitSequence.decode(encoded);
    assertThat(a).isEqualTo(b);
  }

  @Test
  public void testEncodeDecodeMultiBytes() {
    BytesPackedBitSequence a = BytesPackedBitSequence.fromBinaryString("1101001001");
    byte[] encoded = a.encode();
    assertThat(encoded[0]).isEqualTo((byte) Integer.parseInt("11010101", 2));
    assertThat(encoded[1]).isEqualTo((byte) Integer.parseInt("00100010", 2));
    BytesPackedBitSequence b = BytesPackedBitSequence.decode(encoded);
    assertThat(a).isEqualTo(b);
  }

  @Test
  public void testEncodeDecodeMultiBytesFullyPacked() {
    BytesPackedBitSequence a = BytesPackedBitSequence.fromBinaryString("11111110000000");
    byte[] encoded = a.encode();
    assertThat(encoded.length).isEqualTo(2);
    assertThat(encoded[0]).isEqualTo((byte) Integer.parseInt("11111110", 2));
    assertThat(encoded[1]).isEqualTo((byte) Integer.parseInt("00000111", 2));
    BytesPackedBitSequence b = BytesPackedBitSequence.decode(encoded);
    assertThat(a).isEqualTo(b);
  }
}
