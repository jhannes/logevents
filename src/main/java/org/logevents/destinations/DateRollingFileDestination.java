package org.logevents.destinations;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Properties;

import org.logevents.util.Configuration;

/**
 * Outputs to files with a file name that gets current date appended.
 *
 * @author Johannes Brodwall
 *
 */
public class DateRollingFileDestination extends FileDestination {


    public DateRollingFileDestination(Properties properties, String prefix) throws IOException {
        this(new Configuration(properties, prefix).getString("filename"));
    }

    public DateRollingFileDestination(String fileName) throws IOException {
        super(fileName);
    }

    @Override
    public Path getPath() {
        Path path = super.getPath();
        return path.getParent().resolve(path.getFileName().toString() + LocalDate.now());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + super.getPath().getFileName() + "}";
    }
}
