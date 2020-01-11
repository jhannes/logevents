package org.logevents.observers.file;

import org.logevents.config.Configuration;
import org.logevents.util.pattern.PatternConverterSpec;
import org.logevents.util.pattern.StringScanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class LogFileParser {

    private final Pattern filenameRegex;
    private List<BiConsumer<String, FileInfo>> regexGroupExtractor = new ArrayList<>();

    public LogFileParser(FilenameGenerator filenameGenerator, String filenamePattern) {
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
                        filenameRegexBuilder.append("(").append(FilenameGenerator.asDateRegex(dateFormat)).append(")");
                        regexGroupExtractor.add((group, fileInfo) -> {});
                        break;
                    case "application":
                        filenameRegexBuilder.append(filenameGenerator.getApplicationName());
                        break;
                    case "X":
                    case "mdc":
                        String[] parts = spec.getParameters().get(0).split(":-");
                        String key = parts[0];
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
    }

    public FileInfo parseMdcValues(String filename) {
        Matcher matcher = filenameRegex.matcher(filename);
        if (matcher.matches()) {
            FileInfo fileInfo = new FileInfo();
            for (int group = 1; group <= matcher.groupCount(); group++) {
                regexGroupExtractor.get(group-1).accept(matcher.group(group), fileInfo);
            }
            return fileInfo;
        }
        return null;
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
}
