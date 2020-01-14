package org.logevents.observers.file;

import org.junit.Test;
import org.logevents.config.Configuration;

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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FileRotationWorkerTest {

    @Test
    public void shouldCalculateArchivedFileByDate() {
        String archiveFilenamePattern = "logs/%date{yyyy-MM}/%application-%date.log";

        ZonedDateTime fileTime = ZonedDateTime.of(2018, 11, 21, 11, 30, 0, 0, ZoneOffset.systemDefault());

        assertEquals("logs/2018-11/" + Configuration.calculateApplicationName() + "-2018-11-21.log",
                getArchiveName("application.log", archiveFilenamePattern, "application.log", fileTime));
    }

    @Test
    public void shouldRetrieveMdcValuesFromCurrentFilename() {
        String filenamePattern = "logs-%mdc{function:-core}/%application-%mdc{ip:-unknown}.log";
        Map<String, String> mdcValues = new FileRotationWorker(filenamePattern, "logs-%mdc{function}/application-%mdc{ip}-%date.log")
                .parseMdcValues("logs-usermanager/" + Configuration.calculateApplicationName() + "-127.0.0.1.log");
        assertEquals("usermanager", mdcValues.get("function"));
        assertEquals("127.0.0.1", mdcValues.get("ip"));
        assertEquals(2, mdcValues.size());
    }

    @Test
    public void shouldRetrieveFromFilename() {
        String archiveFilenamePattern = "logs-%mdc{function:-core}/%date{yyyy-MM}/%application-%date.log";
        ZonedDateTime date = getFileTime(archiveFilenamePattern, "logs-core/2018-11/" + Configuration.calculateApplicationName() + "-2018-11-21.log");
        assertEquals(date.toLocalDate(), LocalDate.of(2018, 11, 21));
        assertEquals(date.toLocalTime(), LocalTime.of(0, 0));
    }

    @Test
    public void shouldRetrieveTimeFromFilename() {
        String archiveFilenamePattern = "logs/%date/application-%date{yyyy-MM-dd-HH-mm}.log";
        ZonedDateTime date = getFileTime(archiveFilenamePattern, "logs/2018-01-01/application-2018-11-21-13-37.log");
        assertEquals(date.toLocalDate(), LocalDate.of(2018, 11, 21));
        assertEquals(date.toLocalTime(), LocalTime.of(13, 37));
    }

    @Test
    public void shouldRetrieveDateFromInexact() {
        String archiveFilenamePattern = "logs/%date{yyyy-MMM}/application.log";
        ZonedDateTime date = getFileTime(archiveFilenamePattern, "logs/2018-Nov/application.log");
        assertEquals(date.toLocalDate(), LocalDate.of(2018, 11, 30));
        assertEquals(date.toLocalTime(), LocalTime.of(0, 0));
    }

    @Test
    public void shouldDetermineArchiveName() {
        String filenamePattern = "%application-%mdc{function:-core}-%marker-%date{EEE}.log";
        String archiveFilenamePattern = "logs-%mdc{function:-core}/%marker/%date{yyyy-MM}/%application-%date.log";

        Configuration configuration = new Configuration();
        String filename = configuration.getApplicationName() + "-myFunc.A2-SECURITY-Tue.log";
        ZonedDateTime date = ZonedDateTime.of(2019, 10, 11, 13, 37, 0, 0, ZoneId.systemDefault());

        String archiveName = getArchiveName(filenamePattern, archiveFilenamePattern, filename, date);
        assertEquals("logs-myFunc.A2/SECURITY/2019-10/" + configuration.getApplicationName() + "-2019-10-11.log", archiveName);
    }

    private String getArchiveName(String filenamePattern, String archiveFilenamePattern, String filename, ZonedDateTime dateTime) {
        return new FileRotationWorker(filenamePattern, archiveFilenamePattern).getArchiveName(filename, dateTime);
    }

    @Test
    public void shouldCombineFromWeek() {
        String archiveFilenamePattern = "logs/%date{YYYY-'W'ww}/application-%date{EEE}.log";
        FileNameFormat fileNameFormat = new FileNameFormat(archiveFilenamePattern, new Configuration());
        ZonedDateTime date = fileNameFormat.parseDate("logs/2020-W01/application-Tue.log");
        assertEquals(LocalDate.of(2019, 12, 31), date.toLocalDate());
        assertEquals(LocalTime.of(0, 0), date.toLocalTime());
    }

    @Test
    public void shouldRecalculateDayFromWeekdayAndWeekInUs() {
        calculateDayFromWeekdayAndWeek(Locale.forLanguageTag("us"));
    }

    @Test
    public void shouldRecalculateDayFromWeekdayAndWeekInEurope() {
        Locale de = Locale.forLanguageTag("de");
        assertEquals(DayOfWeek.MONDAY, WeekFields.of(de).getFirstDayOfWeek());
        calculateDayFromWeekdayAndWeek(de);
    }

    private void calculateDayFromWeekdayAndWeek(Locale locale) {
        String filenamePattern = "logs/%date{YYYY-'W'ww}-%date{EEE}.log";
        FileNameFormat fileNameFormat = new FileNameFormat(filenamePattern, new Configuration(), locale);

        LocalDate startDate = LocalDate.of(2020, 1, 6);
        for (LocalDate date = startDate; date.isBefore(startDate.plusWeeks(1)); date = date.plusDays(1)) {
            ZonedDateTime fileCreationTime = date.atTime(10, 10).atZone(ZoneId.systemDefault());
            String filename = fileNameFormat.generateName(fileCreationTime);

            assertEquals(filename,
                    date, fileNameFormat.parseDate(filename).toLocalDate());
        }
    }

    @Test
    public void shouldTranformDateFormatsToRegex() {
        assertEquals("\\d{1,4}-\\w{2,3}-\\d{1,2}", FileNameFormat.asDateRegex("yyyy-MMM-dd"));
        assertEquals("\\d{1,4}-W\\d{1,2}", FileNameFormat.asDateRegex("YYYY-'W'ww"));
    }

    @Test
    public void shouldArchiveOldActive() throws IOException {
        deleteRecursively(Paths.get("target/logs0"));
        FileRotationWorker generator = new FileRotationWorker("target/logs0/application-%mdc{A}.log", "target/logs0/logs-%mdc{A}/%date{YYYY-'W'ww}/application-%date{EEE}.log");

        Path path = Paths.get("target/logs0/application-core.log");
        Files.createDirectories(path.getParent());
        List<String> fileLines = Collections.singletonList(UUID.randomUUID().toString());
        Files.write(path, fileLines);

        ZonedDateTime fileTime = ZonedDateTime.now().minusDays(2);
        BasicFileAttributeView attributes = Files.getFileAttributeView(path, BasicFileAttributeView.class);
        FileTime from = FileTime.from(fileTime.toInstant());
        attributes.setTimes(from, from, from);

        generator.rollover();
        assertFalse(Files.exists(path));

        HashMap<String, String> mdcMap = new HashMap<>();
        mdcMap.put("A", "core");
        assertEquals(fileLines, Files.readAllLines(Paths.get(generator.getArchiveName(new FileInfo(mdcMap, fileTime, Locale.getDefault(Locale.Category.FORMAT))))));
    }


    @Test
    public void shouldDeleteOldArchives() throws IOException {
        System.out.println(DateTimeFormatter.ofPattern("YYYY-'W'ww").format(ZonedDateTime.of(
                2020, 1, 5, 2, 1, 0, 0, ZoneId.of("Asia/Colombo")
        )));


        deleteRecursively(Paths.get("target/logs2"));
        FileRotationWorker generator = new FileRotationWorker("target/logs2/application.log", "target/logs2/%date{YYYY-'W'ww}/application-%date{EEE}.log");
        generator.setRetention(Period.ofDays(7));
        ZonedDateTime fileTime = ZonedDateTime.now().minus(Period.ofDays(7)).minusDays(2);
        String archiveName = generator.getArchiveName(fileTime);
        Path archive = Paths.get(archiveName);
        Files.createDirectories(archive.getParent());
        Files.write(archive, Collections.singleton("ABC"));
        generator.rollover();
        assertFalse(Files.exists(archive));
    }

    @Test
    public void shouldRetainNewArchives() throws IOException {
        deleteRecursively(Paths.get("target/logs3"));
        FileRotationWorker generator = new FileRotationWorker("logs/application.log", "target/logs3/%date{YYYY-'W'ww}/application-%date{EEE}.log");
        generator.setRetention(Period.ofDays(7));
        String archiveName = generator.getArchiveName(ZonedDateTime.now().minus(Period.ofDays(7)).plusDays(1));
        Path archive = Paths.get(archiveName);
        Files.createDirectories(archive.getParent());
        Files.write(archive, Collections.singleton("ABC"));
        generator.rollover();
        assertTrue(Files.exists(archive));
    }

    @Test
    public void shouldCompressOldArchives() throws IOException {
        deleteRecursively(Paths.get("target/logs4"));
        FileRotationWorker generator = new FileRotationWorker("logs/application.log", "target/logs4/%date{YYYY-'W'ww}/application-%date{EEE}.log");
        generator.setUncompressedRetention(Period.ofDays(3));
        String archiveName = generator.getArchiveName(ZonedDateTime.now().minus(Period.ofDays(3)).minusDays(1));
        Path archive = Paths.get(archiveName);
        Files.createDirectories(archive.getParent());
        List<String> writtenLines = Collections.singletonList("ABC");
        Files.write(archive, writtenLines);

        generator.rollover();

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
        deleteRecursively(Paths.get("target/logs5"));
        FileRotationWorker generator = new FileRotationWorker("target/logs5/application-%mdc{A}.log", "target/logs5/logs-%mdc{A}/%date{YYYY-'W'ww}/application-%date{EEE}.log");

        String activeFilename = "target/logs5/application-utils.log";
        Path activeFile = Paths.get(activeFilename);
        Files.createDirectories(activeFile.getParent());
        List<String> fileLines = Collections.singletonList(UUID.randomUUID().toString());
        Files.write(activeFile, fileLines);

        ZonedDateTime fileTime = ZonedDateTime.now().minusDays(2);
        BasicFileAttributeView attributes = Files.getFileAttributeView(activeFile, BasicFileAttributeView.class);
        FileTime from = FileTime.from(fileTime.toInstant());
        attributes.setTimes(from, from, from);

        String archiveName = generator.getArchiveName(activeFilename, fileTime);
        Files.createDirectories(Paths.get(archiveName).getParent());
        Files.write(Paths.get(archiveName), Collections.singleton("Already existing archive"));
        String archiveName2 = generator.getArchiveName(activeFilename, fileTime) + ".1";
        Files.createDirectories(Paths.get(archiveName2).getParent());
        Files.write(Paths.get(archiveName2), Collections.singleton("Already existing archive"));

        generator.rollover();
        assertFalse(Files.exists(activeFile));

        assertEquals(fileLines, Files.readAllLines(Paths.get(generator.getArchiveName(activeFilename, fileTime) + ".2")));
    }

    private ZonedDateTime getFileTime(String archiveFilenamePattern, String filename) {
        return new FileRotationWorker("application.log", archiveFilenamePattern).parseArchiveFileTime(filename);
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
