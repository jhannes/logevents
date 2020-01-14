package org.logevents.observers.file;

import org.logevents.config.Configuration;
import org.logevents.util.pattern.PatternConverterSpec;
import org.logevents.util.pattern.StringScanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class FileNameFormat {
    private final Function<FileInfo, String> filenameGenerator;

    private final Pattern filenameRegex;
    private List<BiConsumer<String, FileInfo>> regexGroupExtractor = new ArrayList<>();
    private Locale locale;

    public FileNameFormat(String filenamePattern, Configuration configuration) {
        this(filenamePattern, configuration, Locale.getDefault(Locale.Category.FORMAT));
    }

    public FileNameFormat(String filenamePattern, Configuration configuration, Locale locale) {
        this.locale = locale;
        StringScanner scanner = new StringScanner(filenamePattern);

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
                        filenameGenerators.add(info -> configuration.getNodeName());
                        filenameRegexBuilder.append(configuration.getNodeName());
                        break;
                    case "marker":
                        filenameGenerators.add(FileInfo::getMarker);
                        filenameRegexBuilder.append("([a-zA-Z0-9.-_]*)");
                        regexGroupExtractor.add((group, fileInfo) -> fileInfo.setMarker(group));
                        break;
                    default:
                        throw new IllegalArgumentException(spec.toString());
                }
            }
        }
        filenameRegex = Pattern.compile(filenameRegexBuilder.toString());

        filenameGenerator = info -> filenameGenerators.stream().map(f -> f.apply(info)).collect(Collectors.joining());
    }

    public List<String> findFileNames() throws IOException {
        String[] split = filenameRegex.pattern().split("/");
        List<String> result = new ArrayList<>();
        findFileNames("", Paths.get("."), split, 0, result);
        return result;
    }

    private void findFileNames(String prefix, Path directory, String[] fileParts, int index, List<String> collectedFiles) throws IOException {
        if (!Files.exists(directory)) {
            return;
        } else if (index == fileParts.length) {
            collectedFiles.add(prefix.substring(0, prefix.length()-1)); // remove trailing "/"
            return;
        }
        if (fileParts[index].matches("^[.$a-zA-Z0-9_-]+")) {
            findFileNames(prefix + fileParts[index] + "/", directory.resolve(fileParts[index]), fileParts, index+1, collectedFiles);
        } else {
            for (Path path : Files.list(directory)
                    .filter(p -> p.getFileName().toString().matches(fileParts[index]))
                    .collect(Collectors.toList())) {
                findFileNames(prefix + path.getFileName().toString() + "/", path, fileParts, index+1, collectedFiles);
            }

        }
    }

    ZonedDateTime parseDate(String filename) {
        return parse(filename).getParsedDateTime();
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
                if (cur == 'M' || cur == 'd' || cur == 'E') {
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

}
