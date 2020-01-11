package org.logevents.observers;

import org.logevents.config.Configuration;
import org.logevents.util.pattern.PatternConverterSpec;
import org.logevents.util.pattern.StringScanner;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.TemporalQueries;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class FilenameGenerator {

    private static class ArchiveFileParser {
        private final Function<FileInfo, String> filenameGenerator;
        private Pattern filenameRegex;
        private List<DateTimeFormatter> datePatterns = new ArrayList<>();

        public ArchiveFileParser(String archiveFilenamePattern) {
            Configuration configuration = new Configuration();

            StringScanner scanner = new StringScanner(archiveFilenamePattern);

            List<Function<FileInfo, String>> filenameGenerators = new ArrayList<>();
            StringBuilder filenameRegexBuilder = new StringBuilder();

            while (scanner.hasMoreCharacters()) {
                String text = scanner.readUntil('%');
                filenameGenerators.add(file -> text);
                filenameRegexBuilder.append(text);
                if (scanner.hasMoreCharacters()) {
                    PatternConverterSpec spec = new PatternConverterSpec(configuration, scanner);
                    spec.readConversion();
                    spec.readParameters();
                    switch (spec.getConversionWord()) {
                        case "d":
                        case "date":
                            DateTimeFormatter formatter = spec.getParameter(0)
                                    .map(pattern -> new DateTimeFormatterBuilder().appendPattern(pattern).parseDefaulting(WeekFields.ISO.dayOfWeek(), 1).toFormatter())
                                    .orElse(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                            filenameGenerators.add(info -> formatter.format(info.getTime()));

                            String dateFormat = spec.getParameter(0).orElse("yyyy-MM-dd");
                            DateTimeFormatterBuilder parserBuilder = new DateTimeFormatterBuilder().appendPattern(dateFormat);
                            if (dateFormat.contains("w") && !dateFormat.contains("d")) {
                                parserBuilder.parseDefaulting(WeekFields.ISO.dayOfWeek(), 1);
                            }
                            datePatterns.add(parserBuilder.toFormatter());
                            filenameRegexBuilder.append("(" + asDateRegex(dateFormat) + ")");

                            break;
                        case "application":
                            filenameGenerators.add(info -> configuration.getApplicationName());

                            filenameRegexBuilder.append(configuration.getApplicationName());
                            break;
                        case "X":
                        case "mdc":
                            String[] parts = spec.getParameters().get(0).split(":-");
                            String key = parts[0];
                            String defaultValue = parts.length > 1 ? parts[1] : "";
                            filenameGenerators.add(info -> info.getMdc().getOrDefault(key, defaultValue));

                            filenameRegexBuilder.append("[a-zA-Z0-9.-_]*");
                            break;
                        case "node":
                        case "marker":
                        default:
                            throw new IllegalArgumentException(spec.toString());
                    }
                }
            }
            filenameRegex = Pattern.compile(filenameRegexBuilder.toString());

            filenameGenerator = info -> filenameGenerators.stream().map(f -> f.apply(info)).collect(Collectors.joining());
        }

        ZonedDateTime parseDate(String filename) {
            Matcher matcher = filenameRegex.matcher(filename);

            if (matcher.matches()) {
                LocalDate date = null;
                Integer dayOfWeek = null;
                LocalTime time = LocalTime.of(0, 0);
                ZoneId zone = ZoneId.systemDefault();

                for (int group = 1; group <= matcher.groupCount(); group++) {
                    DateTimeFormatter formatter = datePatterns.get(group - 1);
                    String dateString = matcher.group(group);
                    TemporalAccessor parsedDate = formatter.parse(dateString);
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
                if (dayOfWeek != null) {
                    date = date.minusDays(date.getDayOfWeek().getValue()).plusDays(dayOfWeek);
                }
                return ZonedDateTime.of(date, time, zone);
            } else {
                throw new RuntimeException("Uh oh");
            }
        }

        public String generateName(Map<String, String> mdcMap, ZonedDateTime fileCreationTime) {
            return filenameGenerator.apply(new FileInfo(mdcMap, fileCreationTime));
        }
    }

    private class LogFileParser {

        private final Pattern filenameRegex;
        private List<String> mdcNames = new ArrayList<>();

        public LogFileParser(String filenamePattern) {
            StringScanner scanner = new StringScanner(filenamePattern);
            StringBuilder filenameRegexBuilder = new StringBuilder();

            while (scanner.hasMoreCharacters()) {
                String text = scanner.readUntil('%');
                filenameRegexBuilder.append(text);
                if (scanner.hasMoreCharacters()) {
                    PatternConverterSpec spec = new PatternConverterSpec(new Configuration(), scanner);
                    spec.readConversion();
                    spec.readParameters();
                    switch (spec.getConversionWord()) {
                        case "d":
                        case "date":
                            String dateFormat = spec.getParameter(0).orElse("yyyy-MM-dd");
                            filenameRegexBuilder.append(asDateRegex(dateFormat));
                            break;
                        case "application":
                            filenameRegexBuilder.append(getApplicationName());
                            break;
                        case "X":
                        case "mdc":
                            String[] parts = spec.getParameters().get(0).split(":-");
                            String key = parts[0];
                            filenameRegexBuilder.append("([a-zA-Z0-9.-_]*)");
                            mdcNames.add(key);
                            break;
                        case "node":
                        case "marker":
                        default:
                            throw new IllegalArgumentException(spec.toString());
                    }
                }
            }
            this.filenameRegex = Pattern.compile(filenameRegexBuilder.toString());
        }

        public HashMap<String, String> parseMdcValues(String filename) {
            Matcher matcher = filenameRegex.matcher(filename);
            if (matcher.matches()) {
                HashMap<String, String> result = new HashMap<>();
                for (int group = 1; group <= matcher.groupCount(); group++) {
                    result.put(mdcNames.get(group - 1), matcher.group(group));
                }
                return result;
            }
            return null;
        }
    }

    static class FileInfo {
        private final Map<String, String> mdc;
        private final ZonedDateTime time;

        FileInfo(Map<String, String> mdc, ZonedDateTime time) {
            this.mdc = mdc;
            this.time = time;
        }

        public Map<String, String> getMdc() {
            return mdc;
        }

        public ZonedDateTime getTime() {
            return time;
        }
    }

    private final ArchiveFileParser archiveFileNameFormat;
    private final LogFileParser logFileNameFormat;

    public FilenameGenerator(String filenamePattern, String archiveFilenamePattern) {
        if (filenamePattern != null) {
            this.logFileNameFormat = new LogFileParser(filenamePattern);
        } else {
            this.logFileNameFormat = null;
        }

        if (archiveFilenamePattern != null) {
            this.archiveFileNameFormat = new ArchiveFileParser(archiveFilenamePattern);
        } else {
            this.archiveFileNameFormat = null;
        }
    }

    private String getApplicationName() {
        return new Configuration().getApplicationName();
    }

    /**
     * Copied from {@link DateTimeFormatterBuilder#appendPattern(String)}
     */
    static String asDateRegex(String pattern) {
        StringBuilder regex = new StringBuilder();
        for (int pos = 0; pos < pattern.length(); pos++) {
            char cur = pattern.charAt(pos);
            if ((cur >= 'A' && cur <= 'Z') || (cur >= 'a' && cur <= 'z')) {
                int start = pos++;
                for ( ; pos < pattern.length() && pattern.charAt(pos) == cur; pos++);  // short loop
                int count = pos - start;
                if (cur == 'M' || cur == 'd' || cur == 'E') {
                    if (count == 3) {
                        regex.append("\\w{3}");
                    } else if (count >= 3) {
                        regex.append("\\w{3,}");
                    } else {
                        regex.append("\\d{1,").append(count).append("}");
                    }
                } else {
                    regex.append("\\d{1,").append(count).append("}");
                }
                pos--;
            } else if (cur == '\'') {
                int start = pos++;
                for ( ; pos < pattern.length(); pos++) {
                    if (pattern.charAt(pos) == '\'') {
                        if (pos + 1 < pattern.length() && pattern.charAt(pos + 1) == '\'') {
                            pos++;
                        } else {
                            break;  // end of literal
                        }
                    }
                }
                if (pos >= pattern.length()) {
                    throw new IllegalArgumentException("Pattern ends with an incomplete string literal: " + pattern);
                }
                regex.append(pattern, start + 1, pos);
            } else {
                regex.append(cur);
            }
        }
        return regex.toString();
    }

    public ZonedDateTime parseArchiveFileTime(String filename) {
        return archiveFileNameFormat.parseDate(filename);
    }

    public String getArchiveName(String filename, ZonedDateTime fileCreationTime) {
        Map<String, String> mdcMap = logFileNameFormat.parseMdcValues(filename);
        if (mdcMap == null) {
            return null;
        }
        return getArchiveName(fileCreationTime, mdcMap);
    }

    String getArchiveName(ZonedDateTime fileCreationTime, Map<String, String> mdcMap) {
        return archiveFileNameFormat.generateName(mdcMap, fileCreationTime);
    }

    public Map<String, String> parseMdcValues(String filename) {
        return logFileNameFormat.parseMdcValues(filename);
    }
}


