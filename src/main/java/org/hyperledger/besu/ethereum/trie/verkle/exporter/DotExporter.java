package org.hyperledger.besu.ethereum.trie.verkle.exporter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for exporting Verkle Trie representations to DOT files.
 */
public class DotExporter {

    private static final Logger LOG = LoggerFactory.getLogger(DotExporter.class);
    private static final Pattern FILE_EXTENSION_PATTERN = Pattern.compile("\\.(dot|gv)$");
    private static final String DEFAULT_FILE_NAME = "./VerkleTrie.gv";

    /**
     * Exports the Verkle Trie DOT representation to a '.gv' file located in the current directory.
     * The default file name is "VerkleTrie.gv".
     *
     * @param verkleTrieDotString The DOT representation of the Verkle Trie.
     * @throws IOException If an I/O error occurs during the export process.
     */
    public static void exportToDotFile(String verkleTrieDotString) throws IOException {
        exportToDotFile(verkleTrieDotString, DEFAULT_FILE_NAME);
    }


    /**
     * Exports the Verkle Trie DOT representation to a '.gv' file located at the specified path.
     *
     * @param verkleTrieDotString The DOT representation of the Verkle Trie.
     * @param filePath            The location where the DOT file will be saved.
     * @throws IOException If an I/O error occurs during the export process.
     */
    public static void exportToDotFile(String verkleTrieDotString, String filePath) throws IOException {
        try {
            if (filePath == null || filePath.isEmpty()) {
                filePath = DEFAULT_FILE_NAME;
            } else {
                Matcher matcher = FILE_EXTENSION_PATTERN.matcher(filePath);
                if (!matcher.find()) {
                    throw new IllegalArgumentException("Invalid file extension. Use .dot or .gv extension.");
                }
            }

            Path path = Paths.get(filePath);

            Files.createDirectories(path.getParent());

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toString(), StandardCharsets.UTF_8))) {
                writer.write(verkleTrieDotString);
            }

        } catch (AccessDeniedException e) {
            LOG.error("Access denied. Check write permissions for the file. Details: {}", e.getMessage(), e);
            throw e;
        } catch (FileSystemException e) {
            LOG.error("File system issue. Check disk space and file system restrictions. Details: {}", e.getMessage(), e);
            throw e;
        } catch (IOException e) {
            LOG.error("Error writing DOT file: {}. Details: {}", e.getMessage(), e);
            throw e;
        }
    }
}
