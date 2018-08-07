package org.logevents.destinations;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Properties;

import org.logevents.util.Configuration;

/**
 * Outputs to files with a file name that gets current date appended.
 *
 * @author Johannes Brodwall
 *
 */
public class DateRollingFileDestination implements LogEventDestination {

    private Path logDirectory;
    private Path fileName;

    public DateRollingFileDestination(Properties properties, String prefix) throws IOException {
        this(new Configuration(properties, prefix).getString("filename"));
    }

    public DateRollingFileDestination(String fileName) throws IOException {
        Path path = Paths.get(fileName);
        logDirectory = path.getParent();
        Files.createDirectories(logDirectory);
        this.fileName = path.getFileName();
    }

    @Override
    public synchronized void writeEvent(String message) throws IOException {
        // TODO: Try and keep the file open
        String fileName = this.fileName + "." + LocalDate.now().toString();
        try (Writer writer = new FileWriter(logDirectory.resolve(fileName).toFile(), true)) {
            writer.write(message);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + fileName + "}";
    }
}
