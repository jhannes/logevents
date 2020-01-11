package org.logevents.observers.file;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.TemporalQueries;
import java.util.HashMap;
import java.util.Map;

class FileInfo {
    private final Map<String, String> mdc;
    private ZonedDateTime fileTime;

    FileInfo(Map<String, String> mdc, ZonedDateTime fileTime) {
        this.mdc = mdc;
        this.fileTime = fileTime;
    }

    public FileInfo() {
        mdc = new HashMap<>();
    }

    public Map<String, String> getMdc() {
        return mdc;
    }

    public ZonedDateTime getFileTime() {
        return fileTime;
    }

    private LocalDate date = LocalDate.now();
    private Integer dayOfWeek = null;
    private LocalTime time = LocalTime.of(0, 0);
    private ZoneId zone = ZoneId.systemDefault();

    public void addTimeInfo(TemporalAccessor parsedDate) {
        if (parsedDate.isSupported(ChronoField.INSTANT_SECONDS)) {
            ZonedDateTime dateTime = ZonedDateTime.from(parsedDate);
            date = dateTime.toLocalDate();
            time = dateTime.toLocalTime();
            zone = dateTime.getZone();
        } else {
            if (parsedDate.query(TemporalQueries.localDate()) != null) {
                date = LocalDate.from(parsedDate);
            } else if (parsedDate.isSupported(ChronoField.YEAR) && parsedDate.isSupported(ChronoField.MONTH_OF_YEAR)) {
                int month = parsedDate.get(ChronoField.MONTH_OF_YEAR);
                int year = parsedDate.get(ChronoField.YEAR);
                date = LocalDate.of(year, month, 1).with(TemporalAdjusters.lastDayOfMonth());
            } else if (parsedDate.isSupported(ChronoField.DAY_OF_WEEK)) {
                dayOfWeek = parsedDate.get(ChronoField.DAY_OF_WEEK);
            } else {
                throw new RuntimeException("Uh oh");
            }
            if (parsedDate.query(TemporalQueries.localTime()) != null) {
                time = LocalTime.from(parsedDate);
            }
            if (parsedDate.query(TemporalQueries.zone()) != null) {
                zone = parsedDate.query(TemporalQueries.zone());
            }
        }
    }

    public ZonedDateTime getParsedDateTime() {
        LocalDate date = this.date;
        if (dayOfWeek != null) {
            date = date.minusDays(date.getDayOfWeek().getValue()).plusDays(dayOfWeek);
        }
        return ZonedDateTime.of(date, time, zone);
    }
}
