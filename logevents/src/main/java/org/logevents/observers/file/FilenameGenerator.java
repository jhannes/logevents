package org.logevents.observers.file;

import org.logevents.config.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;
import java.util.Map;

public class FilenameGenerator {

    private Period retention;

    public void setRetention(Period retention) {
        this.retention = retention;
    }

    public void rollover() throws IOException {
        List<String> files = logFileNameFormat.findFileNames();

        for (String file : files) {
            Path path = Paths.get(file);
            ZonedDateTime fileTime = getFileTime(path).atZone(ZoneId.systemDefault());
            if (fileTime.toLocalDate().isBefore(LocalDate.now())) {
                Path target = Paths.get(getArchiveName(file, fileTime));
                Files.createDirectories(target.getParent());
                Files.move(path, target);
            }
        }
    }

    private Instant getFileTime(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class).creationTime().toInstant();
    }

    private final ArchiveFileParser archiveFileNameFormat;
    private final LogFileParser logFileNameFormat;

    public FilenameGenerator(String filenamePattern, String archiveFilenamePattern) {
        if (filenamePattern != null) {
            this.logFileNameFormat = new LogFileParser(this, filenamePattern);
        } else {
            this.logFileNameFormat = null;
        }

        if (archiveFilenamePattern != null) {
            this.archiveFileNameFormat = new ArchiveFileParser(archiveFilenamePattern);
        } else {
            this.archiveFileNameFormat = null;
        }
    }

    String getApplicationName() {
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
        Map<String, String> mdcMap = logFileNameFormat.parseMdcValues(filename).getMdc();
        if (mdcMap == null) {
            return null;
        }
        return getArchiveName(fileCreationTime, mdcMap);
    }

    String getArchiveName(ZonedDateTime fileCreationTime, Map<String, String> mdcMap) {
        return archiveFileNameFormat.generateName(new FileInfo(mdcMap, fileCreationTime));
    }

    public Map<String, String> parseMdcValues(String filename) {
        return logFileNameFormat.parseMdcValues(filename).getMdc();
    }
}


