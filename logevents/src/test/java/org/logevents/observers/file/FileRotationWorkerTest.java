package org.logevents.observers.file;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.logevents.config.Configuration;
import org.logevents.optional.junit.LogEventStatusRule;
import org.logevents.status.StatusEvent;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FileRotationWorkerTest {

    @Rule
    public LogEventStatusRule logEventStatusRule = new LogEventStatusRule();
    private Configuration configuration;

    @Before
    public void setUp() {
        configuration = new Configuration() {
            @Override
            public Locale getLocale() {
                return Locale.US;
            }
        };
    }

    @Test
    public void shouldCalculateArchivedFileByDate() {
        String archiveFilenamePattern = "logs/%date{yyyy-MM}/%application-%date.log";

        ZonedDateTime fileTime = ZonedDateTime.of(2018, 11, 21, 11, 30, 0, 0, ZoneOffset.systemDefault());

        assertEquals("logs/2018-11/" + getApplicationName() + "-2018-11-21.log",
                new FileRotationWorker("application.log", archiveFilenamePattern, configuration).getArchiveName("application.log", fileTime));
    }

    private String getApplicationName() {
        return configuration.getApplicationName();
    }

    @Test
    public void shouldDetermineArchiveName() {
        FileRotationWorker fileRotationWorker = new FileRotationWorker(
                "%application-%mdc{function:-core}-%marker-%date{EEE}.log",
                "logs-%mdc{function:-core}/%marker/%date{yyyy-MM}/%application-%date.log",
                configuration
        );

        String filename = configuration.getApplicationName() + "-myFunc.A2-SECURITY-Tue.log";
        ZonedDateTime date = ZonedDateTime.of(2019, 10, 11, 13, 37, 0, 0, ZoneId.systemDefault());

        String archiveName = fileRotationWorker.getArchiveName(filename, date);
        assertEquals("logs-myFunc.A2/SECURITY/2019-10/" + configuration.getApplicationName() + "-2019-10-11.log", archiveName);
    }

    @Test
    public void shouldArchiveOldActive() throws IOException {
        deleteRecursively(Paths.get("target/logs/0"));
        FileRotationWorker worker = new FileRotationWorker("target/logs/0/application-%mdc{A}.log", "target/logs/0/logs-%mdc{A}/%date{YYYY-'W'ww}/application-%date{EEE}.log", configuration);

        Path path = Paths.get("target/logs/0/application-core.log");
        Files.createDirectories(path.getParent());
        List<String> fileLines = Collections.singletonList(UUID.randomUUID().toString());
        Files.write(path, fileLines);

        ZonedDateTime fileTime = ZonedDateTime.now().minusDays(2);
        BasicFileAttributeView attributes = Files.getFileAttributeView(path, BasicFileAttributeView.class);
        FileTime from = FileTime.from(fileTime.toInstant());
        attributes.setTimes(from, from, from);

        worker.rollover();
        assertFalse(Files.exists(path));

        HashMap<String, String> mdcMap = new HashMap<>();
        mdcMap.put("A", "core");
        assertEquals(fileLines, Files.readAllLines(Paths.get(worker.getArchiveName(new FileInfo(mdcMap, fileTime, Locale.getDefault(Locale.Category.FORMAT))))));
    }

    @Test
    public void shouldDeleteOldArchives() throws IOException {
        deleteRecursively(Paths.get("target/logs/1"));
        FileRotationWorker worker = new FileRotationWorker("target/logs/1/application.log", "target/logs/1/%date{YYYY-'W'ww}/application-%date{EEE}.log", configuration);
        worker.setRetention(Period.ofDays(7));
        ZonedDateTime fileTime = ZonedDateTime.now().minus(Period.ofDays(7)).minusDays(2);
        String archiveName = worker.getArchiveName(fileTime);
        Path archive = Paths.get(archiveName);
        Files.createDirectories(archive.getParent());
        Files.write(archive, Collections.singleton("ABC"));
        worker.rollover();
        assertFalse(Files.exists(archive));
    }

    @Test
    public void shouldContinueScanningAfterIOException() throws IOException {
        deleteRecursively(Paths.get("target/logs/2"));
        FileRotationWorker worker = new FileRotationWorker("target/logs/2/application.log", "target/logs/2/%date{YYYY-'W'ww}/application-%date{EEE}.log", configuration);
        worker.setRetention(Period.ofDays(7));

        String invalidArchiveName = worker.getArchiveName(ZonedDateTime.now().minus(Period.ofDays(7)).minusDays(3).minusWeeks(2));
        Path invalidArchive = Paths.get(invalidArchiveName).getParent();
        Files.createDirectories(invalidArchive.getParent());
        Files.write(invalidArchive, Collections.singleton("ABC"));

        ZonedDateTime fileTime = ZonedDateTime.now().minus(Period.ofDays(7)).minusDays(2);
        Path archive = Paths.get(worker.getArchiveName(fileTime));
        Files.createDirectories(archive.getParent());
        Files.write(archive, Collections.singleton("ABC"));

        logEventStatusRule.setStatusLevel(StatusEvent.StatusLevel.NONE);
        worker.rollover();
        assertFalse(Files.exists(archive));
    }

    @Test
    public void shouldRetainNewArchives() throws IOException {
        deleteRecursively(Paths.get("target/logs/3"));
        FileRotationWorker worker = new FileRotationWorker("logs/application.log", "target/logs/3/%date{YYYY-'W'ww}/application-%date{EEE}.log", configuration);
        worker.setRetention(Period.ofDays(7));
        String archiveName = worker.getArchiveName(ZonedDateTime.now().minus(Period.ofDays(7)).plusDays(1));
        Path archive = Paths.get(archiveName);
        Files.createDirectories(archive.getParent());
        Files.write(archive, Collections.singleton("ABC"));
        worker.rollover();
        assertTrue(Files.exists(archive));
    }

    @Test
    public void shouldCompressOldArchives() throws IOException {
        deleteRecursively(Paths.get("target/logs/4"));
        FileRotationWorker worker = new FileRotationWorker("logs/application.log", "target/logs/4/%date{YYYY-'W'ww}/application-%date{EEE}.log", configuration);
        worker.setCompressAfter(Period.ofDays(3));
        String archiveName = worker.getArchiveName(ZonedDateTime.now().minus(Period.ofDays(3)).minusDays(1));
        Path archive = Paths.get(archiveName);
        Files.createDirectories(archive.getParent());
        List<String> writtenLines = Collections.singletonList("ABC");
        Files.write(archive, writtenLines);

        worker.rollover();

        assertFalse(Files.exists(archive));
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(Paths.get(archiveName + ".gz").toFile()))))) {
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            assertEquals(lines, writtenLines);
        }
    }

    @Test
    public void shouldUseFallbackNameIfTargetArchiveAlreadyExists() throws IOException {
        deleteRecursively(Paths.get("target/logs/5"));
        FileRotationWorker worker = new FileRotationWorker("target/logs/5/application-%mdc{A}.log", "target/logs/5/logs-%mdc{A}/%date{YYYY-'W'ww}/application-%date{EEE}.log", configuration);

        String activeFilename = "target/logs/5/application-utils.log";
        Path activeFile = Paths.get(activeFilename);
        Files.createDirectories(activeFile.getParent());
        List<String> fileLines = Collections.singletonList(UUID.randomUUID().toString());
        Files.write(activeFile, fileLines);

        ZonedDateTime fileTime = ZonedDateTime.now().minusDays(2);
        BasicFileAttributeView attributes = Files.getFileAttributeView(activeFile, BasicFileAttributeView.class);
        FileTime from = FileTime.from(fileTime.toInstant());
        attributes.setTimes(from, from, from);

        String archiveName = worker.getArchiveName(activeFilename, fileTime);
        Files.createDirectories(Paths.get(archiveName).getParent());
        Files.write(Paths.get(archiveName), Collections.singleton("Already existing archive"));
        String archiveName2 = worker.getArchiveName(activeFilename, fileTime) + ".1";
        Files.createDirectories(Paths.get(archiveName2).getParent());
        Files.write(Paths.get(archiveName2), Collections.singleton("Already existing archive"));

        worker.rollover();
        assertFalse(Files.exists(activeFile));

        assertEquals(fileLines, Files.readAllLines(Paths.get(worker.getArchiveName(activeFilename, fileTime) + ".2")));
    }

    @Test
    public void shouldExpireCompressedFiles() throws IOException {
        deleteRecursively(Paths.get("target/logs/6"));

        FileRotationWorker worker = new FileRotationWorker("target/logs/6/application.log", "target/logs/6/%date{YYYY-'W'ww}/application-%date{EEE}.log", configuration);
        deleteRecursively(Paths.get("target/logs/6"));
        worker.setRetention(Period.ofDays(7));
        ZonedDateTime fileTime = ZonedDateTime.now().minus(Period.ofDays(7)).minusDays(2);
        String archiveName = worker.getArchiveName(fileTime) + ".gz";
        Path archive = Paths.get(archiveName);
        Files.createDirectories(archive.getParent());
        Files.write(archive, Collections.singleton("ABC"));
        worker.rollover();
        assertFalse(Files.exists(archive));
    }


    private void deleteRecursively(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

}
