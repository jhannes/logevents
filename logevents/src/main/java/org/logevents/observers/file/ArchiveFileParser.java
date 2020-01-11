package org.logevents.observers.file;

import org.logevents.config.Configuration;
import org.logevents.util.pattern.PatternConverterSpec;
import org.logevents.util.pattern.StringScanner;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class ArchiveFileParser {
    private final Function<FileInfo, String> filenameGenerator;

    private final Pattern filenameRegex;
    private List<BiConsumer<String, FileInfo>> regexGroupExtractor = new ArrayList<>();

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
                        filenameGenerators.add(info -> formatter.format(info.getFileTime()));

                        String dateFormat = spec.getParameter(0).orElse("yyyy-MM-dd");
                        DateTimeFormatterBuilder parserBuilder = new DateTimeFormatterBuilder().appendPattern(dateFormat);
                        if (dateFormat.contains("w") && !dateFormat.contains("d")) {
                            parserBuilder.parseDefaulting(WeekFields.ISO.dayOfWeek(), 1);
                        }
                        DateTimeFormatter dateTimeFormatter = parserBuilder.toFormatter();
                        filenameRegexBuilder.append("(").append(FilenameGenerator.asDateRegex(dateFormat)).append(")");
                        regexGroupExtractor.add((group, fileInfo) -> fileInfo.addTimeInfo(dateTimeFormatter.parse(group)));

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

                        filenameRegexBuilder.append("([a-zA-Z0-9.-_]*)");
                        regexGroupExtractor.add((group, fileInfo) -> fileInfo.getMdc().put(key, group));
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
            FileInfo fileInfo = new FileInfo();
            for (int group = 1; group <= matcher.groupCount(); group++) {
                regexGroupExtractor.get(group - 1).accept(matcher.group(group), fileInfo);
            }
            return fileInfo.getParsedDateTime();
        } else {
            throw new RuntimeException("Uh oh");
        }
    }

    public String generateName(FileInfo fileInfo) {
        return filenameGenerator.apply(fileInfo);
    }
}
