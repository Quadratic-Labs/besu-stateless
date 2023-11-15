package org.hyperledger.besu.ethereum.trie.verkle.exporter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DotExporter {

    private static final Pattern FILE_EXTENSION_PATTERN = Pattern.compile("\\.(dot|gv)$");

    /**
     * Exports the VerkleTrie to DOT format and saves it to a file.
     *
     * @param verkleTrieDotString DOT representation of the VerkleTrie.
     * @param filePath            The path where the DOT file will be saved.
     */
    public static void exportToDotFile(String verkleTrieDotString, String filePath) {
        try {
            if (filePath == null || filePath.isEmpty()) {
                // If filePath is not provided, create a default one with the .dot extension.
                filePath = "defaultVerkleTrie.dot";
            } else {
                // Check if the provided filePath has a valid extension (.dot or .gv).
                Matcher matcher = FILE_EXTENSION_PATTERN.matcher(filePath);
                if (!matcher.find()) {
                    System.err.println("Error: Invalid file extension. Use .dot or .gv extension.");
                    return;
                }
            }

            Path path = Paths.get(filePath);

            // Create directories if they don't exist.
            if (!path.getParent().toFile().exists()) {
                path.getParent().toFile().mkdirs();
            }

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toString()))) {
                writer.write(verkleTrieDotString);
            }

        } catch (AccessDeniedException e) {
            System.err.println("Error writing DOT file: Access denied. Check write permissions for the file.");
            e.printStackTrace();
        } catch (FileSystemException e) {
            System.err.println("Error writing DOT file: File system issue. Check disk space and file system restrictions.");
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("Error writing DOT file: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
