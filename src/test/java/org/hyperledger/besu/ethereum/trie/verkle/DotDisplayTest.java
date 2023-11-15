package org.hyperledger.besu.ethereum.trie.verkle;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

public class DotDisplayTest {

    @Test
    public void testToDotTrieOneValueNoRepeatingEdges() {
        SimpleVerkleTrie<Bytes32, Bytes32> trie = new SimpleVerkleTrie<>();
        Bytes32 key = Bytes32.fromHexString("0x00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");
        Bytes32 value = Bytes32.fromHexString("0x1000000000000000000000000000000000000000000000000000000000000000");
        trie.put(key, value);

        System.out.println(trie.toDotTree());
    }

    @Test
    public void testToDotTrieTwoValuesNoRepeatingEdges() {
        SimpleVerkleTrie<Bytes32, Bytes32> trie = new SimpleVerkleTrie<Bytes32, Bytes32>();
        Bytes32 key1 = Bytes32.fromHexString("0x00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");
        Bytes32 value1 = Bytes32.fromHexString("0x1000000000000000000000000000000000000000000000000000000000000000");
        Bytes32 key2 = Bytes32.fromHexString("0x00112233445566778899aabbccddeeff00112233445566778899aabbccddee00");
        Bytes32 value2 = Bytes32.fromHexString("0x0100000000000000000000000000000000000000000000000000000000000000");

        trie.put(key1, value1);
        trie.put(key2, value2);

        System.out.println(trie.toDotTree());
    }
    @Test
    public void testToDotTrieOneValueRepeatingEdges() {
        SimpleVerkleTrie<Bytes32, Bytes32> trie = new SimpleVerkleTrie<>();
        Bytes32 key = Bytes32.fromHexString("0x00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");
        Bytes32 value = Bytes32.fromHexString("0x1000000000000000000000000000000000000000000000000000000000000000");
        trie.put(key, value);

        System.out.println(trie.toDotTree(true));
    }

    @Test
    public void testToDotTrieTwoValuesRepeatingEdges() {
        SimpleVerkleTrie<Bytes32, Bytes32> trie = new SimpleVerkleTrie<Bytes32, Bytes32>();
        Bytes32 key1 = Bytes32.fromHexString("0x00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");
        Bytes32 value1 = Bytes32.fromHexString("0x1000000000000000000000000000000000000000000000000000000000000000");
        Bytes32 key2 = Bytes32.fromHexString("0x00112233445566778899aabbccddeeff00112233445566778899aabbccddee00");
        Bytes32 value2 = Bytes32.fromHexString("0x0100000000000000000000000000000000000000000000000000000000000000");

        trie.put(key1, value1);
        trie.put(key2, value2);

        System.out.println(trie.toDotTree(true));
    }
}
