package org.logevents.observers.file;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.logevents.status.LogEventStatus;
import org.logevents.status.StatusEvent;
import org.logevents.status.StatusEvent.StatusLevel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FileDestinationTest {

    private StatusLevel oldThreshold;

    @Test
    public void shouldOutputToFile() throws IOException {
        Path path = Paths.get("target", "logs", "file-test.log");
        FileDestination file = createFileDestination(path);

        file.writeEvent(path, "Hello world\n");

        assertEquals(Arrays.asList("Hello world"), Files.readAllLines(path));
    }

    @Test
    public void shouldRecoverFromFailedOpen() throws IOException {
        Path path = Paths.get("target", "logs", "file-test-3.log");
        FileDestination file = createFileDestination(path);

        // Now we can't log to path, because it's an existing DIRECTORY
        Files.createDirectories(path);
        file.writeEvent(path, "Test message - dropped because log file is blocked by existing directory\n");
        file.writeEvent(path, "Test message - also dropped\n");

        Files.delete(path);
        file.writeEvent(path, "Test message - written as we recover\n");

        assertEquals(Arrays.asList("Test message - written as we recover"), Files.readAllLines(path));
    }

    @Test
    public void shouldBreakCircuitAfterSuccessiveFailures() throws IOException {
        Path path = Paths.get("target", "test", "logs", "file-test-4.log");
        FileDestination file = createFileDestination(path);

        // Now we can't log to path, because it's an existing DIRECTORY
        Files.createDirectories(path);

        for (int i = 0; i < file.getCircuitBreakThreshold() + 1; i++) {
            file.writeEvent(path, "Test message - file can't be created\n");
        }

        // Now we can write...
        Files.deleteIfExists(path);
        file.writeEvent(path, "Test message - won't be written because circuit is broken\n");

        file.setCircuitBrokenUntil(Instant.now().minusMillis(1));
        file.writeEvent(path, "Test message - circuit is recovered\n");

        assertEquals(Arrays.asList("Test message - circuit is recovered"), Files.readAllLines(path));
    }

    @Test
    public void shouldWriteNewFileAfterPathChange() throws IOException {
        Path first = Paths.get("target", "logs", "file-test-first.log");
        Path second = Paths.get("target", "logs", "file-test-second.log");
        Files.deleteIfExists(second);

        FileDestination file = createFileDestination(first);

        file.writeEvent(first, "Written to old file\n");

        file.writeEvent(second, "Written to new file\n");
        assertEquals(Arrays.asList("Written to new file"), Files.readAllLines(second));

    }

    public static class Locker {
        public static void main(String[] args) throws InterruptedException, IOException {
            Path path = Paths.get(args[0]);
            FileChannel channel = FileChannel.open(path, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
            channel.lock();
            System.out.println("Locked file: " + path);
            Thread.sleep(10000L);
        }
    }

    @Test
    public void shouldRecoverFromLockedFile() throws IOException, InterruptedException {
        Assume.assumeTrue("File locking is not supported on Linux", isWindows());

        Path path = Paths.get("target", "logs", "file-test-2.log");
        Files.deleteIfExists(path);

        String java = System.getProperty("java.home") + "/bin/java";
        ProcessBuilder builder = new ProcessBuilder(java, "-classpath", "target/test-classes", Locker.class.getName(),
                path.toAbsolutePath().toString());
        Process process = builder.start();

        BufferedReader processReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        processReader.readLine();

        FileDestination file = new FileDestination(true);

        file.writeEvent(path, "Test message\n");

        assertTrue(process.isAlive());
        process.destroy();
        process.waitFor();

        assertEquals(Collections.emptyList(), Files.readAllLines(path));

        file.writeEvent(path, "Test message\n");
        assertEquals(Arrays.asList("Test message"), Files.readAllLines(path));

        Assume.assumeTrue("Error message is localized", Locale.getDefault().toLanguageTag().startsWith("en"));
        List<StatusEvent> messages = LogEventStatus.getInstance().getMessages(file, StatusLevel.ERROR);
        assertEquals("The process cannot access the file because another process has locked a portion of the file",
                messages.get(0).getMessage());
    }

    @Before
    public void turnOffStatusLogging() {
        oldThreshold = LogEventStatus.getInstance().setThreshold(StatusEvent.StatusLevel.NONE);
    }

    @After
    public void restoreStatusLogging() {
        LogEventStatus.getInstance().setThreshold(oldThreshold);
    }


    private FileDestination createFileDestination(Path path) throws IOException {
        Files.deleteIfExists(path);
        return new FileDestination(true);
    }

    private boolean isWindows() {
        return System.getProperty("os.name").startsWith("Windows");
    }
}
