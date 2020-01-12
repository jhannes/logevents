package org.logevents.observers.file;

import org.logevents.config.Configuration;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import java.util.Objects;
import java.util.zip.GZIPOutputStream;

public class FilenameGenerator {

    private Period retention;
    private Period uncompressedRetention;

    public void setRetention(Period retention) {
        this.retention = retention;
    }

    public void rollover() throws IOException {
        List<String> files = activeLogFileFormat.findFileNames();

        for (String file : files) {
            Path path = Paths.get(file);
            ZonedDateTime fileTime = getFileTime(path).atZone(ZoneId.systemDefault());
            System.err.println("Delete? " + path);
            if (fileTime.toLocalDate().isBefore(LocalDate.now())) {
                System.err.println("Delete " + path);
                Path target = Paths.get(getArchiveName(file, fileTime));
                Files.createDirectories(target.getParent());
                Files.move(path, target);
            } else {
                System.err.println("Retain " + path);
            }
        }

        List<String> archivedFiles = archiveFileNameFormat.findFileNames();
        if (retention != null) {
            for (String archivedFile : archivedFiles) {
                ZonedDateTime archiveDate = archiveFileNameFormat.parseDate(archivedFile);
                if (archiveDate.isBefore(ZonedDateTime.now().minus(retention))) {
                    Files.delete(Paths.get(archivedFile));
                }
            }
        }
        if (uncompressedRetention != null) {
            for (String archivedFile : archivedFiles) {
                ZonedDateTime archiveDate = archiveFileNameFormat.parseDate(archivedFile);
                if (archiveDate.isBefore(ZonedDateTime.now().minus(uncompressedRetention))) {
                    compress(archivedFile);
                }
            }
        }
    }

    private void compress(String archivedFile) throws IOException {
        try (FileInputStream in = new FileInputStream(archivedFile)) {
            try (OutputStream out = new GZIPOutputStream(new FileOutputStream(archivedFile + ".gz"))) {
                transfer(in, out);
            }
        }
        Files.delete(Paths.get(archivedFile));
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

    public void setUncompressedRetention(Period uncompressedRetention) {
        this.uncompressedRetention = uncompressedRetention;
    }


    private static final int DEFAULT_BUFFER_SIZE = 8192;

    // TODO: If we upgrade to Java 9, replace with <code>in.transferTo(out);</code>
    private long transfer(InputStream in, OutputStream out) throws IOException {
        Objects.requireNonNull(out, "out");
        long transferred = 0;
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        int read;
        while ((read = in.read(buffer, 0, DEFAULT_BUFFER_SIZE)) >= 0) {
            out.write(buffer, 0, read);
            transferred += read;
        }
        return transferred;
    }
}


