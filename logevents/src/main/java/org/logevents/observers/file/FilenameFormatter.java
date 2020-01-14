package org.logevents.observers.file;

import org.logevents.LogEvent;
import org.logevents.config.Configuration;
import org.logevents.status.LogEventStatus;
import org.logevents.util.pattern.PatternConverterSpec;
import org.logevents.util.pattern.StringScanner;
import org.slf4j.Marker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Creates, parses and scans for file names matching a specified pattern, such as
 * <code>logs/%mdc{tenant:-common}/%date{YYYY-'w'WW}/%application-%EE.log</code>, which would expand to
 * <code>logs/example.org/2020-W01/myapplication-Mon.log</code> if the {@link org.slf4j.MDC} variable
 * "tenant" was "example.org" and the date 2019/12/30 (which was the first week of the year 2020).
 *
 * <p>The following conversion words are supported in the filename:</p>
 * <ul>
 *     <li>
 *         <code>%date</code>: The date and time of the log event.
 *         Optionally supports a date formatting pattern from {@link DateTimeFormatter#ofPattern}
 *         e.g. %date{DD-MMM HH:mm:ss}. Default format is <code>yyyy-MM-dd HH:mm:ss.SSS</code>.
 *     </li>
 *     <li><code>%marker[{defaultValue}]</code>: {@link Marker} (if any)</li>
 *     <li>
 *         <code>%mdc{key:-default}</code>:
 *         the specified {@link org.slf4j.MDC} variable or default value if not set
 *     </li>
 *     <li><code>%application</code>: The value of {@link Configuration#getApplicationName()}</li>
 *     <li><code>%node</code>: The value of {@link Configuration#getNodeName()} ()}</li>
 * </ul>
 */
public class FilenameFormatter {
    private final Function<FileInfo, String> filenameGenerator;
    private final Function<LogEvent, String> logFileGenerator;

    private final Pattern filenameRegex;
    private List<BiConsumer<String, FileInfo>> regexGroupExtractor = new ArrayList<>();
    private final String filenamePattern;
    private Locale locale;

    public FilenameFormatter(String filenamePattern) {
        this(filenamePattern, new Configuration());
    }

    public FilenameFormatter(String filenamePattern, Configuration configuration) {
        this(filenamePattern, configuration, configuration.getLocale());
    }

    public FilenameFormatter(String filenamePattern, Configuration configuration, Locale locale) {
        this.filenamePattern = filenamePattern;
        this.locale = locale;
        StringScanner scanner = new StringScanner(filenamePattern);

        List<Function<FileInfo, String>> filenameGenerators = new ArrayList<>();
        List<Function<LogEvent, String>> logfileGenerators = new ArrayList<>();
        StringBuilder filenameRegexBuilder = new StringBuilder();

        while (scanner.hasMoreCharacters()) {
            String text = scanner.readUntil('%');
            filenameGenerators.add(file -> text);
            logfileGenerators.add(event -> text);
            filenameRegexBuilder.append(text);
            if (scanner.hasMoreCharacters()) {
                PatternConverterSpec spec = new PatternConverterSpec(configuration, scanner);
                spec.readConversion();
                spec.readParameters();
                switch (spec.getConversionWord()) {
                    case "d":
                    case "date":
                        DateTimeFormatter formatter = spec.getParameter(0)
                                .map(pattern -> new DateTimeFormatterBuilder().appendPattern(pattern).toFormatter(locale))
                                .orElse(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                        filenameGenerators.add(info -> formatter.format(info.getFileCreationTime()));

                        String dateFormat = spec.getParameter(0).orElse("yyyy-MM-dd");
                        DateTimeFormatterBuilder parserBuilder = new DateTimeFormatterBuilder().appendPattern(dateFormat);
                        if (dateFormat.contains("w") && !dateFormat.contains("d")) {
                            parserBuilder.parseDefaulting(WeekFields.ISO.dayOfWeek(), 1);
                        }
                        DateTimeFormatter dateTimeFormatter = parserBuilder.toFormatter(locale);
                        filenameRegexBuilder.append("(").append(asDateRegex(dateFormat)).append(")");
                        regexGroupExtractor.add((group, fileInfo) -> fileInfo.addTimeInfo(dateTimeFormatter.parse(group)));

                        ZoneId zone = spec.getParameter(1)
                                .map(ZoneId::of)
                                .orElse(ZoneId.systemDefault());
                        logfileGenerators.add(e -> formatter.format(e.getInstant().atZone(zone)));
                        break;
                    case "application":
                        filenameGenerators.add(info -> configuration.getApplicationName());
                        filenameRegexBuilder.append(configuration.getApplicationName());
                        logfileGenerators.add(e -> configuration.getApplicationName());
                        break;
                    case "X":
                    case "mdc":
                        String[] parts = spec.getParameters().get(0).split(":-");
                        String key = parts[0];
                        String defaultValue = parts.length > 1 ? parts[1] : "";
                        filenameGenerators.add(info -> info.getMdc().getOrDefault(key, defaultValue));

                        filenameRegexBuilder.append("([a-zA-Z0-9.-_]*)");
                        regexGroupExtractor.add((group, fileInfo) -> fileInfo.getMdc().put(key, group));
                        logfileGenerators.add(e -> e.getMdc(key, defaultValue));
                        break;
                    case "node":
                        filenameGenerators.add(info -> configuration.getNodeName());
                        filenameRegexBuilder.append(configuration.getNodeName());
                        logfileGenerators.add(e -> configuration.getNodeName());
                        break;
                    case "marker":
                        filenameGenerators.add(FileInfo::getMarker);
                        filenameRegexBuilder.append("([a-zA-Z0-9.-_]*)");
                        regexGroupExtractor.add((group, fileInfo) -> fileInfo.setMarker(group));
                        logfileGenerators.add(e -> Optional.ofNullable(e.getMarker()).map(Marker::toString).orElse(spec.getParameter(0).orElse("")));
                        break;
                    default:
                        throw new IllegalArgumentException(spec.toString());
                }
            }
        }
        filenameRegexBuilder.append("(.gz)?");
        regexGroupExtractor.add((group, fileInfo) -> {});

        filenameRegex = Pattern.compile(filenameRegexBuilder.toString());

        filenameGenerator = info -> filenameGenerators.stream().map(f -> f.apply(info)).collect(Collectors.joining());
        logFileGenerator = event -> logfileGenerators.stream().map(f -> f.apply(event)).collect(Collectors.joining());
    }

    public List<String> findFileNames() {
        String[] split = filenameRegex.pattern().split("/");
        List<String> result = new ArrayList<>();
        findFileNames("", Paths.get("."), split, 0, result);
        return result;
    }

    private void findFileNames(String prefix, Path directory, String[] fileParts, int index, List<String> collectedFiles) {
        if (!Files.exists(directory)) {
            return;
        } else if (index == fileParts.length) {
            collectedFiles.add(prefix.substring(0, prefix.length()-1)); // remove trailing "/"
            return;
        }
        if (fileParts[index].matches("^[.$a-zA-Z0-9_-]+")) {
            findFileNames(prefix + fileParts[index] + "/", directory.resolve(fileParts[index]), fileParts, index+1, collectedFiles);
        } else {
            try {
                for (Path path : Files.list(directory)
                        .filter(p -> p.getFileName().toString().matches(fileParts[index]))
                        .collect(Collectors.toList())) {
                    findFileNames(prefix + path.getFileName().toString() + "/", path, fileParts, index+1, collectedFiles);
                }
            } catch (IOException e) {
                LogEventStatus.getInstance().addError(this, "Failed to list logfiles in " + directory, e);
                // Continue listing other files
            }
        }
    }

    ZonedDateTime parseDate(String filename) {
        return parse(filename).getParsedDateTime();
    }

    public String format(LogEvent logEvent) {
        return logFileGenerator.apply(logEvent);
    }

    public String generateName(FileInfo fileInfo) {
        return filenameGenerator.apply(fileInfo);
    }

    public String generateName(ZonedDateTime fileTime) {
        return generateName(new FileInfo(new HashMap<>(), fileTime, locale));
    }

    public FileInfo parse(String filename) {
        Matcher matcher = filenameRegex.matcher(filename);
        if (matcher.matches()) {
            FileInfo fileInfo = new FileInfo(locale);
            for (int group = 1; group <= matcher.groupCount(); group++) {
                regexGroupExtractor.get(group - 1).accept(matcher.group(group), fileInfo);
            }
            return fileInfo;
        } else {
            throw new IllegalArgumentException("File does not match pattern: " + filename + " !~ " + filenameRegex);
        }
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
                if (cur == 'E') {
                    regex.append("\\w{2,3}");
                } else if (cur == 'M' || cur == 'd') {
                    if (count == 3) {
                        regex.append("\\w{2,3}");
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

    @Override
    public String toString() {
        return "FilenameFormatter{" + filenamePattern + '}';
    }
}
