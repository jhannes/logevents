package org.logevents.destinations;

import java.nio.file.Path;
import java.util.function.Supplier;

public class DynamicFileDestination extends FileDestination {

    private Supplier<String> filename;

    public DynamicFileDestination(Path parent, Supplier<String> filename) {
        super(parent, null);
        this.filename = filename;
    }

    @Override
    public Path getPath() {
        return logDirectory.resolve(filename.get());
    }
}
