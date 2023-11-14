package org.hyperledger.besu.ethereum.trie.verkle.exporter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Utility class to save DOT format VerkleTrie to a file.
 */
public class DotExporter {

    /**
     * Exports the VerkleTrie to DOT format and saves it to a file.
     *
     * @param verkleTrieDotString DOT representation of the VerkleTrie.
     * @param filePath            The path where the DOT file will be saved.
     */
    public static void exportToDotFile(String verkleTrieDotString, String filePath) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write(verkleTrieDotString);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error writing DOT file: " + e.getMessage());
        }
    }
}
