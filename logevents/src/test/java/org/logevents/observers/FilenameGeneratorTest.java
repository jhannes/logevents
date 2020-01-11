package org.logevents.observers;

import org.junit.Test;
import org.logevents.config.Configuration;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FilenameGeneratorTest {

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
        Map<String, String> mdcValues = new FilenameGenerator(filenamePattern, null)
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
        String filenamePattern = "%application-%mdc{function:-core}-%date{EEE}.log";
        String archiveFilenamePattern = "logs-%mdc{function:-core}/%date{yyyy-MM}/%application-%date.log";

        Configuration configuration = new Configuration();
        String filename = configuration.getApplicationName() + "-myFunc.A2-Tue.log";
        ZonedDateTime date = ZonedDateTime.of(2019, 10, 11, 13, 37, 0, 0, ZoneId.systemDefault());

        String archiveName = getArchiveName(filenamePattern, archiveFilenamePattern, filename, date);
        assertEquals("logs-myFunc.A2/2019-10/" + configuration.getApplicationName() + "-2019-10-11.log", archiveName);
    }

    private String getArchiveName(String filenamePattern, String archiveFilenamePattern, String filename, ZonedDateTime dateTime) {
        return new FilenameGenerator(filenamePattern, archiveFilenamePattern).getArchiveName(filename, dateTime);
    }

    @Test
    public void shouldCombineFromWeek() {
        String archiveFilenamePattern = "logs/%date{YYYY-'W'ww}/application-%date{EEE}.log";
        ZonedDateTime date = getFileTime(archiveFilenamePattern, "logs/2020-W01/application-Tue.log");
        assertEquals(LocalDate.of(2019, 12, 31), date.toLocalDate());
        assertEquals(LocalTime.of(0, 0), date.toLocalTime());
    }

    @Test
    public void shouldTranformDateFormatsToRegex() {
        FilenameGenerator generator = new FilenameGenerator(null, null);
        assertEquals("\\d{1,4}-\\w{3}-\\d{1,2}", FilenameGenerator.asDateRegex("yyyy-MMM-dd"));
        assertEquals("\\d{1,4}-W\\d{1,2}", FilenameGenerator.asDateRegex("YYYY-'W'ww"));
    }

    private ZonedDateTime getFileTime(String archiveFilenamePattern, String filename) {
        return new FilenameGenerator(null, archiveFilenamePattern).parseArchiveFileTime(filename);
    }


    public static void mains(String[] args) throws IOException {

        // Pre-existing files:
        // - MDC current, but old timestamp
        // - MDC older than retention
        // - MDC new
        // - Default MDC current but old timestamp
        // - Default MDC older than retention
        // - Default MDC new


        // For retention and compression to make sense, archive pattern must contain date conversion word and all other conversion words
        // Rollover and expiry should happen when any date pattern would give a different value for today than the file creation time and at startup


        String filenamePattern = "logs/%application-$mdc{function:-core}.log";

        String archiveFilenamePattern = "logs/$mdc{function:-core}/%date{yyyy-MM}/%application-%date.log";


        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");


        String filename = "application.log";

        createFile(formatter, Instant.now().minusSeconds(2 * 24 * 60 * 60 + 3950));
        createFile(formatter, Instant.now().minusSeconds(2 * 7 * 24 * 60 * 60 + 3950));

        Files.deleteIfExists(Paths.get(filename));
        Files.createFile(Paths.get(filename));

        BasicFileAttributeView attributes = Files.getFileAttributeView(Paths.get(filename), BasicFileAttributeView.class);
        FileTime from = FileTime.from(Instant.now().minusSeconds(24 * 60 * 60 + 3400));
        attributes.setTimes(from, from, from);


        Period compression = Period.ofWeeks(1);
        Period retention = Period.ofWeeks(1);





        File[] list = new File(".").listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.matches("application-\\d{4}-\\d{2}-\\d{2}.log");
            }
        });
        for (File file : list) {
            Pattern pattern = Pattern.compile("application-(\\d{4}-\\d{2}-\\d{2}).log");
            Matcher matcher = pattern.matcher(file.getName());
            if (matcher.matches()) {
                TemporalAccessor date = formatter.parse(matcher.group(1));

                date.isSupported(ChronoField.INSTANT_SECONDS);
                date.query(TemporalQueries.localTime());


                if (LocalDate.from(date).plus(retention).isBefore(LocalDate.now())) {
                    System.out.println("DELETE: " + file + " => " + date);
                } else {
                    System.out.println("RETAIN: " + file + " => " + date);
                }
            } else {
                System.err.println("HUH!?");
            }

        }


        Instant fileTime = Files.readAttributes(Paths.get(filename), BasicFileAttributes.class).creationTime().toInstant();

        Instant time = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();

        if (fileTime.isBefore(time)) {
            System.out.println("Should rotate " + filename + " to " + fileTime);
        } else {
            System.out.println("File time " + fileTime);
        }


    }

    private static void createFile(DateTimeFormatter formatter, Instant fileTime) throws IOException {
        String filename = "application-" + formatter.format(fileTime.atZone(ZoneId.systemDefault())) + ".log";
        Files.deleteIfExists(Paths.get(filename));
        Files.createFile(Paths.get(filename));
        BasicFileAttributeView attributes = Files.getFileAttributeView(Paths.get(filename), BasicFileAttributeView.class);
        FileTime from = FileTime.from(fileTime);
        attributes.setTimes(from, from, from);

        Files.deleteIfExists(Paths.get(filename + "s"));
        Files.createFile(Paths.get(filename + "s"));
        Files.deleteIfExists(Paths.get("a" + filename));
        Files.createFile(Paths.get("a" + filename));
    }


}
