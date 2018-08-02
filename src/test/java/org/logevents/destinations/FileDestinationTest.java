package org.logevents.destinations;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;

import org.junit.Test;

public class FileDestinationTest {

    @Test
    public void shouldOutputToFile() throws IOException {
        Path path = Paths.get("target", "logs", "file-test.log");
        Files.delete(path);
        Properties properties = new Properties();
        properties.setProperty("observer.file.destination.filename", path.toString());
        FileDestination file = new FileDestination(properties, "observer.file.destination");

        file.writeEvent("Hello world");

        assertEquals(Arrays.asList("Hello world"), Files.readAllLines(path));
    }

}
