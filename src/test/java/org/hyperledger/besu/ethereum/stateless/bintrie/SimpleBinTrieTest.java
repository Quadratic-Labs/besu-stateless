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

import org.hyperledger.besu.ethereum.stateless.bintrie.node.Node;
import org.hyperledger.besu.ethereum.stateless.bintrie.node.StemNode;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class SimpleBinTrieTest {
  static BytesPackedBitSequenceFactory keyFactory;

  @BeforeAll
  static void setup() {
    keyFactory = new BytesPackedBitSequenceFactory();
  }

  @Test
  public void testEmptyTrie() {
    SimpleBinTrie<BytesPackedBitSequence, Bytes32> trie = new SimpleBinTrie<>();
    assertThat(trie.getRootHash())
        .as("Retrieve root hash")
        .isEqualByComparingTo(Node.EMPTY_COMMITMENT);
  }

  @Test
  public void testOneValue() {
    SimpleBinTrie<BytesPackedBitSequence, Bytes32> trie = new SimpleBinTrie<>();
    BytesPackedBitSequence key =
        keyFactory.fromHexString(
            "0x00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");
    Bytes32 value =
        Bytes32.fromHexString("0x1000000000000000000000000000000000000000000000000000000000000000");
    trie.put(key, value);
    assertThat(trie.get(key))
        .as("Get one value should be the inserted value")
        .isEqualTo(Optional.of(value));
    Bytes32 expectedRootHash =
        Bytes32.fromHexString("0x951d42689548318da1121b320255387e6d7bb17c34f2ef4885af82596ad384ed");
    assertThat(trie.getRootHash()).as("Retrieve root hash").isEqualByComparingTo(expectedRootHash);
  }

  @Test
  public void testDeleteAlreadyDeletedValue() {
    SimpleBinTrie<BytesPackedBitSequence, Bytes32> trie = new SimpleBinTrie<>();
    BytesPackedBitSequence key =
        keyFactory.fromHexString(
            "0x00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");
    Bytes32 value =
        Bytes32.fromHexString("0x1000000000000000000000000000000000000000000000000000000000000000");
    trie.put(key, value);
    assertThat(trie.getRoot()).as("Stem root").isInstanceOf(StemNode.class);
    trie.remove(key);
    trie.remove(key);
    assertThat(trie.getRootHash()).isEqualTo(Bytes32.ZERO);
  }

  @Test
  public void testDeleteKeyWithDivergentStemDoesNotWipeExistingStem() {
    // Regression test: removing a key whose stem is absent from the trie must be a no-op, even
    // when the traversal lands on an existing stem node sharing a path prefix. RemoveVisitor
    // used to return a NullNode in that case, wiping the whole unrelated stem.
    SimpleBinTrie<BytesPackedBitSequence, Bytes32> trie = new SimpleBinTrie<>();
    BytesPackedBitSequence key =
        keyFactory.fromHexString(
            "0x00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");
    Bytes32 value =
        Bytes32.fromHexString("0x1000000000000000000000000000000000000000000000000000000000000000");
    trie.put(key, value);
    Bytes32 rootBefore = trie.getRootHash();

    // same prefix bits, divergent stem, absent from the trie
    BytesPackedBitSequence absentKey =
        keyFactory.fromHexString(
            "0x00112233445566778899aabbccddee0000112233445566778899aabbccddeeff");
    trie.remove(absentKey);

    assertThat(trie.get(key)).as("Existing value survives").isEqualTo(Optional.of(value));
    assertThat(trie.getRootHash()).as("Root unchanged").isEqualTo(rootBefore);
  }

  @Test
  public void testTwoValuesAtSameStem() throws Exception {
    SimpleBinTrie<BytesPackedBitSequence, Bytes32> trie = new SimpleBinTrie<>();
    BytesPackedBitSequence key1 =
        keyFactory.fromHexString(
            "0x00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");
    Bytes32 value1 =
        Bytes32.fromHexString("0x1000000000000000000000000000000000000000000000000000000000000000");
    BytesPackedBitSequence key2 =
        keyFactory.fromHexString(
            "0x00112233445566778899aabbccddeeff00112233445566778899aabbccddee00");
    Bytes32 value2 =
        Bytes32.fromHexString("0x0100000000000000000000000000000000000000000000000000000000000000");
    BytesPackedBitSequence key3 =
        keyFactory.fromHexString(
            "0xde112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");
    trie.put(key1, value1);
    trie.put(key2, value2);
    assertThat(trie.get(key1).get()).as("Get first value").isEqualByComparingTo(value1);
    assertThat(trie.get(key2).get()).as("Get second value").isEqualByComparingTo(value2);
    assertThat(trie.get(key3)).as("Get non-key returns empty").isEmpty();

    Bytes32 expectedRootHash =
        Bytes32.fromHexString("0xd0a463ba4288815c0ab1f5c6c4ba8c21a416983549dc8147d50ab3105d097050");
    assertThat(trie.getRootHash()).as("Get root hash").isEqualByComparingTo(expectedRootHash);
  }

  @Test
  public void testTwoValuesAtDifferentIndex() throws Exception {
    SimpleBinTrie<BytesPackedBitSequence, Bytes32> trie = new SimpleBinTrie<>();
    BytesPackedBitSequence key1 =
        keyFactory.fromHexString(
            "0x00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");
    Bytes32 value1 =
        Bytes32.fromHexString("0x1000000000000000000000000000000000000000000000000000000000000000");
    BytesPackedBitSequence key2 =
        keyFactory.fromHexString(
            "0xff112233445566778899aabbccddeeff00112233445566778899aabbccddee00");
    Bytes32 value2 =
        Bytes32.fromHexString("0x0100000000000000000000000000000000000000000000000000000000000000");
    trie.put(key1, value1);
    trie.put(key2, value2);
    Bytes32 hash = trie.getRootHash();
    assertThat(trie.get(key1).get()).as("Get first value").isEqualByComparingTo(value1);
    assertThat(trie.get(key2).get()).as("Get second value").isEqualByComparingTo(value2);
    Bytes32 expectedRootHash =
        Bytes32.fromHexString("0x68c411009ff23a134bd625459125bebb0347262e0a880aa8de37bd255edb77ac");
    assertThat(hash).as("Retrieve root hash").isEqualByComparingTo(expectedRootHash);
  }

  @Test
  public void testTwoValuesWithDivergentStemsAtDepth2() throws Exception {
    SimpleBinTrie<BytesPackedBitSequence, Bytes32> trie = new SimpleBinTrie<>();
    BytesPackedBitSequence key1 =
        keyFactory.fromHexString(
            "0x00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");
    Bytes32 value1 =
        Bytes32.fromHexString("0x1000000000000000000000000000000000000000000000000000000000000000");
    BytesPackedBitSequence key2 =
        keyFactory.fromHexString(
            "0x00ff112233445566778899aabbccddeeff00112233445566778899aabbccddee");
    Bytes32 value2 =
        Bytes32.fromHexString("0x0100000000000000000000000000000000000000000000000000000000000000");
    trie.put(key1, value1);
    trie.put(key2, value2);
    assertThat(trie.get(key1)).as("Retrieve first value").isEqualTo(Optional.of(value1));
    assertThat(trie.get(key2)).as("Retrieve second value").isEqualTo(Optional.of(value2));
    Bytes32 expectedRootHash =
        Bytes32.fromHexString("0xb5aa9c1591c7a6422ec7f68d9dc69e91bf3b135eedb929f3164802a62ef1b1c8");
    assertThat(trie.getRootHash()).as("Retrieve root hash").isEqualByComparingTo(expectedRootHash);
  }

  @Test
  public void testDeleteTwoValuesAtSameStem() throws Exception {
    SimpleBinTrie<BytesPackedBitSequence, Bytes32> trie = new SimpleBinTrie<>();
    BytesPackedBitSequence key1 =
        keyFactory.fromHexString(
            "0x00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");
    Bytes32 value1 =
        Bytes32.fromHexString("0x1000000000000000000000000000000000000000000000000000000000000001");
    BytesPackedBitSequence key2 =
        keyFactory.fromHexString(
            "0x00112233445566778899aabbccddeeff00112233445566778899aabbccddee00");
    Bytes32 value2 =
        Bytes32.fromHexString("0x0100000000000000000000000000000000000000000000000000000000000002");
    trie.put(key1, value1);
    trie.put(key2, value2);
    trie.remove(key1);
    assertThat(trie.get(key1)).as("Make sure value is deleted").isEqualTo(Optional.empty());
    trie.remove(key2);
    assertThat(trie.get(key2)).as("Make sure value is deleted").isEqualTo(Optional.empty());
  }

  @Test
  public void testDeleteTwoValuesAtDifferentIndex() throws Exception {
    SimpleBinTrie<BytesPackedBitSequence, Bytes32> trie = new SimpleBinTrie<>();
    BytesPackedBitSequence key1 =
        keyFactory.fromHexString(
            "0x00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");
    Bytes32 value1 =
        Bytes32.fromHexString("0x1000000000000000000000000000000000000000000000000000000000000000");
    BytesPackedBitSequence key2 =
        keyFactory.fromHexString(
            "0xff112233445566778899aabbccddeeff00112233445566778899aabbccddee00");
    Bytes32 value2 =
        Bytes32.fromHexString("0x0100000000000000000000000000000000000000000000000000000000000000");
    trie.put(key1, value1);
    trie.put(key2, value2);
    trie.remove(key1);
    assertThat(trie.get(key1)).as("Make sure value is deleted").isEqualTo(Optional.empty());
    trie.remove(key2);
    assertThat(trie.get(key2)).as("Make sure value is deleted").isEqualTo(Optional.empty());
  }

  @Test
  public void testDeleteTwoValuesWithDivergentStemsAtDepth2() throws Exception {
    SimpleBinTrie<BytesPackedBitSequence, Bytes32> trie = new SimpleBinTrie<>();
    BytesPackedBitSequence key1 =
        keyFactory.fromHexString(
            "0x00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");
    Bytes32 value1 =
        Bytes32.fromHexString("0x1000000000000000000000000000000000000000000000000000000000000000");
    BytesPackedBitSequence key2 =
        keyFactory.fromHexString(
            "0x00ff112233445566778899aabbccddeeff00112233445566778899aabbccddee");
    Bytes32 value2 =
        Bytes32.fromHexString("0x0100000000000000000000000000000000000000000000000000000000000000");
    trie.put(key1, value1);
    trie.put(key2, value2);
    trie.remove(key1);
    assertThat(trie.get(key1)).as("Make sure value is deleted").isEqualTo(Optional.empty());
    trie.remove(key2);
    assertThat(trie.get(key2)).as("Make sure value is deleted").isEqualTo(Optional.empty());
  }

  @Test
  public void testDeleteThreeValues() throws Exception {
    SimpleBinTrie<BytesPackedBitSequence, Bytes32> trie = new SimpleBinTrie<>();
    BytesPackedBitSequence key1 =
        keyFactory.fromHexString(
            "0x00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");
    Bytes32 value1 =
        Bytes32.fromHexString("0x1000000000000000000000000000000000000000000000000000000000000000");
    BytesPackedBitSequence key2 =
        keyFactory.fromHexString(
            "0x00ff112233445566778899aabbccddeeff00112233445566778899aabbccddee");
    Bytes32 value2 =
        Bytes32.fromHexString("0x0200000000000000000000000000000000000000000000000000000000000000");
    BytesPackedBitSequence key3 =
        keyFactory.fromHexString(
            "0x00ff112233445566778899aabbccddeeff00112233445566778899aabbccddff");
    Bytes32 value3 =
        Bytes32.fromHexString("0x0300000000000000000000000000000000000000000000000000000000000000");
    trie.put(key1, value1);
    trie.put(key2, value2);
    trie.put(key3, value3);
    trie.remove(key3);
    assertThat(trie.get(key3)).as("Make sure value is deleted").isEqualTo(Optional.empty());
    assertThat(trie.get(key2)).as("Retrieve second value").isEqualTo(Optional.of(value2));
    trie.remove(key2);
    assertThat(trie.get(key2)).as("Make sure value is deleted").isEqualTo(Optional.empty());
    assertThat(trie.get(key1)).as("Retrieve first value").isEqualTo(Optional.of(value1));
    trie.remove(key1);
    assertThat(trie.get(key1)).as("Make sure value is deleted").isEqualTo(Optional.empty());
  }

  @Test
  public void testDeleteThreeValuesWithFlattening() throws Exception {
    SimpleBinTrie<BytesPackedBitSequence, Bytes32> trie = new SimpleBinTrie<>();
    BytesPackedBitSequence key1 =
        keyFactory.fromHexString(
            "0x00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");
    Bytes32 value1 =
        Bytes32.fromHexString("0x1000000000000000000000000000000000000000000000000000000000000000");
    BytesPackedBitSequence key2 =
        keyFactory.fromHexString(
            "0x00ff112233445566778899aabbccddeeff00112233445566778899aabbccddee");
    Bytes32 value2 =
        Bytes32.fromHexString("0x0200000000000000000000000000000000000000000000000000000000000000");
    BytesPackedBitSequence key3 =
        keyFactory.fromHexString(
            "0x00ff112233445566778899aabbccddeeff00112233445566778899aabbccddff");
    Bytes32 value3 =
        Bytes32.fromHexString("0x0300000000000000000000000000000000000000000000000000000000000000");
    trie.put(key1, value1);
    trie.put(key2, value2);
    trie.put(key3, value3);
    trie.remove(key1);
    assertThat(trie.get(key1)).as("First value has been deleted").isEqualTo(Optional.empty());
    assertThat(trie.get(key2)).as("Second value").isEqualTo(Optional.of(value2));
    trie.remove(key2);
    assertThat(trie.get(key2)).as("Second value has been deleted").isEqualTo(Optional.empty());
    assertThat(trie.get(key3)).as("Third value").isEqualTo(Optional.of(value3));
    trie.remove(key3);
    assertThat(trie.get(key3)).as("Third value has been deleted").isEqualTo(Optional.empty());
  }

  @Test
  public void testDeleteAllValuesWithDivergentStemsAtDepth2() throws Exception {
    SimpleBinTrie<BytesPackedBitSequence, Bytes32> trie = new SimpleBinTrie<>();
    assertThat(trie.getRootHash()).isEqualTo(Bytes32.ZERO);
    BytesPackedBitSequence key0 =
        keyFactory.fromHexString(
            "0x1e4abaeaa58259f4784e086ddbaa74a9d3975efb2e4380595f0eed5692c45641");
    Bytes32 value0 =
        Bytes32.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000001");
    BytesPackedBitSequence key1 =
        keyFactory.fromHexString(
            "0x1e4abaeaa58259f4784e086ddbaa74a9d3975efb2e4380595f0eed5692c45601");
    Bytes32 value1 =
        Bytes32.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000001");
    BytesPackedBitSequence key2 =
        keyFactory.fromHexString(
            "0x1e4abaeaa58259f4784e086ddbaa74a9d3975efb2e4380595f0eed5692c45602");
    Bytes32 value2 = Bytes32.fromHexString("0x01");
    BytesPackedBitSequence key3 =
        keyFactory.fromHexString(
            "0x1e4abaeaa58259f4784e086ddbaa74a9d3975efb2e4380595f0eed5692c45600");
    Bytes32 value3 = Bytes32.fromHexString("0x00");
    BytesPackedBitSequence key4 =
        keyFactory.fromHexString(
            "0x1e4abaeaa58259f4784e086ddbaa74a9d3975efb2e4380595f0eed5692c45603");
    Bytes32 value4 =
        Bytes32.fromHexString("0xf84a97f1f0a956e738abd85c2e0a5026f8874e3ec09c8f012159dfeeaab2b156");
    BytesPackedBitSequence key5 =
        keyFactory.fromHexString(
            "0x1e4abaeaa58259f4784e086ddbaa74a9d3975efb2e4380595f0eed5692c45604");
    Bytes32 value5 = Bytes32.fromHexString("0x03");
    BytesPackedBitSequence key6 =
        keyFactory.fromHexString(
            "0x1e4abaeaa58259f4784e086ddbaa74a9d3975efb2e4380595f0eed5692c45680");
    Bytes32 value6 =
        Bytes32.fromHexString("0x0000010200000000000000000000000000000000000000000000000000000000");
    trie.put(key0, value0);
    trie.put(key1, value1);
    trie.put(key2, value2);
    trie.put(key3, value3);
    trie.put(key4, value4);
    trie.put(key5, value5);
    trie.put(key6, value6);
    trie.remove(key0);
    trie.remove(key4);
    trie.remove(key5);
    trie.remove(key6);
    trie.remove(key3);
    trie.remove(key1);
    trie.remove(key2);
    assertThat(trie.getRootHash()).isEqualTo(Bytes32.ZERO);
  }

  @Test
  public void testDeleteManyValuesWithDivergentStemsAtDepth2() throws Exception {
    SimpleBinTrie<BytesPackedBitSequence, Bytes32> trie = new SimpleBinTrie<>();
    assertThat(trie.getRootHash()).isEqualTo(Bytes32.ZERO);
    BytesPackedBitSequence key0 =
        keyFactory.fromHexString(
            "0x1e4abaeaa58259f4784e086ddbaa74a9d3975efb2e4380595f0eed5692c45641");
    Bytes32 value0 =
        Bytes32.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000001");
    BytesPackedBitSequence key1 =
        keyFactory.fromHexString(
            "0x1e4abaeaa58259f4784e086ddbaa74a9d3975efb2e4380595f0eed5692c45601");
    Bytes32 value1 =
        Bytes32.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000001");
    BytesPackedBitSequence key2 =
        keyFactory.fromHexString(
            "0x1e4abaeaa58259f4784e086ddbaa74a9d3975efb2e4380595f0eed5692c45602");
    Bytes32 value2 = Bytes32.fromHexString("0x01");
    BytesPackedBitSequence key3 =
        keyFactory.fromHexString(
            "0x1e4abaeaa58259f4784e086ddbaa74a9d3975efb2e4380595f0eed5692c45600");
    Bytes32 value3 = Bytes32.fromHexString("0x00");
    BytesPackedBitSequence key4 =
        keyFactory.fromHexString(
            "0x1e4abaeaa58259f4784e086ddbaa74a9d3975efb2e4380595f0eed5692c45603");
    Bytes32 value4 =
        Bytes32.fromHexString("0xf84a97f1f0a956e738abd85c2e0a5026f8874e3ec09c8f012159dfeeaab2b156");
    BytesPackedBitSequence key5 =
        keyFactory.fromHexString(
            "0x1e4abaeaa58259f4784e086ddbaa74a9d3975efb2e4380595f0eed5692c45604");
    Bytes32 value5 = Bytes32.fromHexString("0x03");
    BytesPackedBitSequence key6 =
        keyFactory.fromHexString(
            "0x1e4abaeaa58259f4784e086ddbaa74a9d3975efb2e4380595f0eed5692c45680");
    Bytes32 value6 =
        Bytes32.fromHexString("0x0000010200000000000000000000000000000000000000000000000000000000");
    trie.put(key0, value0);
    trie.put(key1, value1);
    trie.put(key2, value2);
    final Bytes32 expectedIntermediateRootHash = trie.getRootHash();
    trie.put(key3, value3);
    final Bytes32 expectedIntermediateRootHash2 = trie.getRootHash();
    trie.put(key4, value4);
    trie.put(key5, value5);
    trie.put(key6, value6);
    trie.remove(key4);
    trie.remove(key5);
    trie.remove(key6);
    assertThat(trie.getRootHash()).isEqualTo(expectedIntermediateRootHash2);
    trie.remove(key3);
    assertThat(trie.getRootHash()).isEqualTo(expectedIntermediateRootHash);
    trie.remove(key1);
    trie.remove(key0);
    trie.remove(key2);
    assertThat(trie.getRootHash()).isEqualTo(Bytes32.ZERO);
  }
}
