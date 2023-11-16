package org.hyperledger.besu.ethereum.trie.verkle.exporter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DotExporter {

    private static final Pattern FILE_EXTENSION_PATTERN = Pattern.compile("\\.(dot|gv)$");
    private static final String DEFAULT_FILE_NAME = "./VerkleTrie.gv";

    /**
     * Exports the VerkleTrie to DOT format and saves it to a file with a default path.
     *
     * @param verkleTrieDotString DOT representation of the VerkleTrie.
     */
    public static void exportToDotFile(String verkleTrieDotString) {
        exportToDotFile(verkleTrieDotString, null);
    }

    /**
     * Exports the VerkleTrie to DOT format and saves it to a file.
     *
     * @param verkleTrieDotString DOT representation of the VerkleTrie.
     * @param filePath            The path where the DOT file will be saved. If null or empty, the default path will be used.
     */
    public static void exportToDotFile(String verkleTrieDotString, String filePath) {
        try {
            if (filePath == null || filePath.isEmpty()) {
                filePath = DEFAULT_FILE_NAME;
            } else {
                // Check if the provided filePath has a valid extension (.dot or .gv).
                Matcher matcher = FILE_EXTENSION_PATTERN.matcher(filePath);
                if (!matcher.find()) {
                    System.err.println("Error: Invalid file extension. Use .dot or .gv extension.");
                    return;
                }
            }

            Path path = Paths.get(filePath);

            if (!path.getParent().toFile().exists()) {
                path.getParent().toFile().mkdirs();
            }

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toString(), StandardCharsets.UTF_8))) {
                writer.write(verkleTrieDotString);
            }

        } catch (AccessDeniedException e) {
            handleFileWritingError("Access denied. Check write permissions for the file.", e);
        } catch (FileSystemException e) {
            handleFileWritingError("File system issue. Check disk space and file system restrictions.", e);
        } catch (IOException e) {
            handleFileWritingError(e.getMessage(), e);
        }
    }

    private static void handleFileWritingError(String errorMessage, Exception exception) {
        System.err.println("Error writing DOT file: " + errorMessage + " Details: " + exception.getMessage());
        exception.printStackTrace();
    }
}
