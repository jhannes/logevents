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
import java.util.List;
import java.util.Map;

public class FilenameGenerator {

    private Period retention;

    public void setRetention(Period retention) {
        this.retention = retention;
    }

    public void rollover() throws IOException {
        List<String> files = activeLogFileFormat.findFileNames();

        for (String file : files) {
            Path path = Paths.get(file);
            ZonedDateTime fileTime = getFileTime(path).atZone(ZoneId.systemDefault());
            if (fileTime.toLocalDate().isBefore(LocalDate.now())) {
                Path target = Paths.get(getArchiveName(file, fileTime));
                Files.createDirectories(target.getParent());
                Files.move(path, target);
            }
        }

        if (retention != null) {
            List<String> archivedFiles = archiveFileNameFormat.findFileNames();
            for (String archivedFile : archivedFiles) {
                ZonedDateTime archiveDate = archiveFileNameFormat.parseDate(archivedFile);
                if (archiveDate.isBefore(ZonedDateTime.now().minus(retention))) {
                    Files.delete(Paths.get(archivedFile));
                }
            }
        }
    }

    private Instant getFileTime(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class).creationTime().toInstant();
    }

    private final FileNameFormat archiveFileNameFormat;
    private final FileNameFormat activeLogFileFormat;

    public FilenameGenerator(String filenamePattern, String archiveFilenamePattern) {
        if (filenamePattern != null) {
            this.activeLogFileFormat = new FileNameFormat(filenamePattern);
        } else {
            this.activeLogFileFormat = null;
        }

        if (archiveFilenamePattern != null) {
            this.archiveFileNameFormat = new FileNameFormat(archiveFilenamePattern);
        } else {
            this.archiveFileNameFormat = null;
        }
    }

    String getApplicationName() {
        return new Configuration().getApplicationName();
    }


    public ZonedDateTime parseArchiveFileTime(String filename) {
        return archiveFileNameFormat.parseDate(filename);
    }

    public String getArchiveName(String filename, ZonedDateTime fileCreationTime) {
        Map<String, String> mdcMap = activeLogFileFormat.parse(filename).getMdc();
        if (mdcMap == null) {
            return null;
        }
        return getArchiveName(fileCreationTime, mdcMap);
    }

    String getArchiveName(ZonedDateTime fileCreationTime, Map<String, String> mdcMap) {
        return archiveFileNameFormat.generateName(new FileInfo(mdcMap, fileCreationTime));
    }

    public Map<String, String> parseMdcValues(String filename) {
        return activeLogFileFormat.parse(filename).getMdc();
    }
}


