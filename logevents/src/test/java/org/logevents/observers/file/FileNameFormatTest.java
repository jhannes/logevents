package org.logevents.observers.file;

import org.junit.Test;
import org.logevents.config.Configuration;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class FileNameFormatTest {

    @Test
    public void shouldRetrieveMdcValuesFromCurrentFilename() {
        String filenamePattern = "logs-%mdc{function:-core}/%application-%mdc{ip:-unknown}.log";
        Map<String, String> mdcValues = new FileNameFormat(filenamePattern)
                .parse("logs-usermanager/" + Configuration.calculateApplicationName() + "-127.0.0.1.log").getMdc();
        assertEquals("usermanager", mdcValues.get("function"));
        assertEquals("127.0.0.1", mdcValues.get("ip"));
        assertEquals(2, mdcValues.size());
    }

    @Test
    public void shouldRetrieveFromFilename() {
        String archiveFilenamePattern = "logs-%mdc{function:-core}/%date{yyyy-MM}/%application-%date.log";
        ZonedDateTime date = new FileNameFormat(archiveFilenamePattern)
                .parseDate("logs-core/2018-11/" + Configuration.calculateApplicationName() + "-2018-11-21.log");
        assertEquals(date.toLocalDate(), LocalDate.of(2018, 11, 21));
        assertEquals(date.toLocalTime(), LocalTime.of(0, 0));
    }

    @Test
    public void shouldRetrieveDateFromInexact() {
        String archiveFilenamePattern = "logs/%date{yyyy-MMM}/application.log";
        ZonedDateTime date = new FileNameFormat(archiveFilenamePattern).parseDate("logs/2018-Nov/application.log");
        assertEquals(date.toLocalDate(), LocalDate.of(2018, 11, 30));
        assertEquals(date.toLocalTime(), LocalTime.of(0, 0));
    }

    @Test
    public void shouldRetrieveTimeFromFilename() {
        String archiveFilenamePattern = "logs/%date/application-%date{yyyy-MM-dd-HH-mm}.log";
        ZonedDateTime date = new FileNameFormat(archiveFilenamePattern).parseDate("logs/2018-01-01/application-2018-11-21-13-37.log");
        assertEquals(date.toLocalDate(), LocalDate.of(2018, 11, 21));
        assertEquals(date.toLocalTime(), LocalTime.of(13, 37));
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

}
