package org.logevents.destinations;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.logevents.util.Configuration;

/**
 * Output to a given file.
 *
 * @author Johannes Brodwall
 *
 */
public class FileDestination implements LogEventDestination {

    private Path logDirectory;
    private Path fileName;

    public FileDestination(String filename) throws IOException {
        Path path = Paths.get(filename);
        logDirectory = path.getParent();
        Files.createDirectories(logDirectory);
        this.fileName = path.getFileName();
    }

    public FileDestination(Configuration configuration) throws IOException {
        this(configuration.getString("filename"));
    }

    public FileDestination(Properties configuration, String prefix) throws IOException {
        this(new Configuration(configuration, prefix));
    }

    @Override
    public synchronized void writeEvent(String message) throws IOException {
        // TODO: Try and keep the file open
        try (Writer writer = new FileWriter(logDirectory.resolve(fileName).toFile(), true)) {
            writer.write(message);
        }
    }

}
