package org.hyperledger.besu.ethereum.trie.verkle;

import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.ethereum.trie.verkle.exporter.DotExporter;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DotDisplayTest {

    /**
     * Reads the content of a file from the resources folder.
     *
     * @param fileName The name of the file in the resources folder.
     * @return The content of the file as a String.
     * @throws IOException If an I/O error occurs.
     */
    private String getResources(final String fileName) throws IOException {
        var classLoader = DotDisplayTest.class.getClassLoader();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                classLoader.getResourceAsStream(fileName), StandardCharsets.UTF_8))) {

            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    @Test
    public void testToDotTrieOneValueNoRepeatingEdgesExport() throws IOException {
        SimpleVerkleTrie<Bytes32, Bytes32> trie = new SimpleVerkleTrie<>();
        Bytes32 key = Bytes32.fromHexString("0x00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");
        Bytes32 value = Bytes32.fromHexString("0x1000000000000000000000000000000000000000000000000000000000000000");
        trie.put(key, value);

        final String fileName = "expectedTreeOneValueNoRepeatingEdges.txt";
        final String expectedTree = getResources(fileName);
        final String actualTree = trie.toDotTree();

        assertEquals(expectedTree, actualTree);
    }

    @Test
    public void testToDotTrieOneValueNoRepeatingEdges() throws IOException {
        SimpleVerkleTrie<Bytes32, Bytes32> trie = new SimpleVerkleTrie<>();
        Bytes32 key = Bytes32.fromHexString("0x00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");
        Bytes32 value = Bytes32.fromHexString("0x1000000000000000000000000000000000000000000000000000000000000000");
        trie.put(key, value);

        final String fileName = "expectedTreeOneValueNoRepeatingEdges.txt";
        final String expectedTree = getResources(fileName);
        final String actualTree = trie.toDotTree();
        assertEquals(expectedTree, actualTree);
    }



    @Test
    public void testToDotTrieTwoValuesNoRepeatingEdges() throws IOException {
        SimpleVerkleTrie<Bytes32, Bytes32> trie = new SimpleVerkleTrie<Bytes32, Bytes32>();
        Bytes32 key1 = Bytes32.fromHexString("0x00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");
        Bytes32 value1 = Bytes32.fromHexString("0x1000000000000000000000000000000000000000000000000000000000000000");
        Bytes32 key2 = Bytes32.fromHexString("0x00112233445566778899aabbccddeeff00112233445566778899aabbccddee00");
        Bytes32 value2 = Bytes32.fromHexString("0x0100000000000000000000000000000000000000000000000000000000000000");

        trie.put(key1, value1);
        trie.put(key2, value2);

        final String fileName = "expectedTreeTwoValuesNoRepeatingEdges.txt";
        final String expectedTree = getResources(fileName);;
        final String actualTree = trie.toDotTree();
        assertEquals(expectedTree, actualTree);
    }

    @Test
    public void testToDotTrieOneValueRepeatingEdges() throws IOException {
        SimpleVerkleTrie<Bytes32, Bytes32> trie = new SimpleVerkleTrie<>();
        Bytes32 key = Bytes32.fromHexString("0x00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");
        Bytes32 value = Bytes32.fromHexString("0x1000000000000000000000000000000000000000000000000000000000000000");
        trie.put(key, value);

        final String fileName = "expectedTreeOneValueRepeatingEdges.txt";
        final String expectedTree = getResources(fileName);
        final String actualTree = trie.toDotTree(true);
        assertEquals(expectedTree, actualTree);
    }

    @Test
    public void testToDotTrieTwoValuesRepeatingEdges() throws IOException {
        SimpleVerkleTrie<Bytes32, Bytes32> trie = new SimpleVerkleTrie<Bytes32, Bytes32>();
        Bytes32 key1 = Bytes32.fromHexString("0x00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");
        Bytes32 value1 = Bytes32.fromHexString("0x1000000000000000000000000000000000000000000000000000000000000000");
        Bytes32 key2 = Bytes32.fromHexString("0x00112233445566778899aabbccddeeff00112233445566778899aabbccddee00");
        Bytes32 value2 = Bytes32.fromHexString("0x0100000000000000000000000000000000000000000000000000000000000000");

        trie.put(key1, value1);
        trie.put(key2, value2);

        final String fileName = "expectedTreeTwoValuesRepeatingEdges.txt";
        final String expectedTree = getResources(fileName);
        final String actualTree = trie.toDotTree(true);
        assertEquals(expectedTree, actualTree);
    }
}
