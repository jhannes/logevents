package org.logevents.destinations;

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
public class DateRollingFileDestination extends DynamicFileDestination {

    private Path fileName;

    public DateRollingFileDestination(Properties properties, String prefix) {
        this(new Configuration(properties, prefix).getString("filename"));
    }

    public DateRollingFileDestination(String fileName) {
        this(Paths.get(fileName));
    }

    public DateRollingFileDestination(Path path) {
        this(path.getParent(), path.getFileName());
    }

    public DateRollingFileDestination(Path parent, Path fileName) {
        super(parent, () -> (fileName.toString() + LocalDate.now()));
        this.fileName = fileName;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + fileName + "}";
    }
}
