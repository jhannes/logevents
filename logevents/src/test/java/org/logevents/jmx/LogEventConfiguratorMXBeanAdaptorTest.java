package org.logevents.jmx;

import org.junit.Test;
import org.logevents.config.DefaultLogEventConfigurator;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class LogEventConfiguratorMXBeanAdaptorTest {

    private static int counter = 0;

    private Path propertiesDir = Paths.get("target", "test-data", "jmx-" + (counter++));
    private Properties configuration = new Properties();

    private DefaultLogEventConfigurator configurator = new DefaultLogEventConfigurator(propertiesDir);
    private LogEventConfiguratorMXBeanAdaptor mbean = new LogEventConfiguratorMXBeanAdaptor(configurator);

    @Test
    public void shouldFindConfigurationSources() throws IOException {
        writeProps(propertiesDir.resolve("logevents.properties"), configuration);
        writeProps(propertiesDir.resolve("logevents-test.properties"), configuration);
        writeProps(propertiesDir.resolve("logevents-test2.properties"), configuration);
        System.setProperty("profiles", "test");
        assertEquals(Arrays.asList(
                propertiesDir.resolve("logevents.properties").toString(),
                propertiesDir.resolve("logevents-test.properties").toString()
        ), mbean.getConfigurationSources());
    }

    @Test
    public void getConfigurationValues() throws IOException {
        configuration.setProperty("logevents.status", "INFO");
        configuration.setProperty("observers.console.threshold", "WARN");
        writeProps(propertiesDir.resolve("logevents.properties"), configuration);
        assertEquals(Arrays.asList(
                "logevents.status=INFO",
                "observers.console.threshold=WARN"
        ), mbean.getConfigurationValues());
    }


    public static void writeProps(Path file, Properties defaultProperties) throws IOException {
        Files.createDirectories(file.getParent());
        try (FileWriter writer = new FileWriter(file.toFile())) {
            defaultProperties.store(writer, "Default configuration file");
        }
    }
}
