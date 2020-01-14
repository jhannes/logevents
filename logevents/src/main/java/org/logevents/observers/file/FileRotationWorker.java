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

public class FileRotationWorker {

    private final FileNameFormat archiveFileNameFormat;
    private final FileNameFormat activeLogFileFormat;
    private Period retention;
    private Period uncompressedRetention;

    public void setRetention(Period retention) {
        this.retention = retention;
    }

    public void setUncompressedRetention(Period uncompressedRetention) {
        this.uncompressedRetention = uncompressedRetention;
    }

    public void rollover() throws IOException {
        List<String> files = activeLogFileFormat.findFileNames();

        for (String file : files) {
            Path path = Paths.get(file);
            ZonedDateTime fileTime = getFileTime(path).atZone(ZoneId.systemDefault());
            if (fileTime.toLocalDate().isBefore(LocalDate.now())) {
                Path target = Paths.get(getArchiveName(file, fileTime));
                Files.createDirectories(target.getParent());
                if (Files.exists(target)) {
                    int index = 1;
                    do {
                        target = Paths.get(getArchiveName(file, fileTime) + "." + index++);
                    } while (Files.exists(target));
                }
                Files.move(path, target); // TODO: Do an atomic move to different stores
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

    public FileRotationWorker(String filenamePattern, String archiveFilenamePattern) {
        this.activeLogFileFormat = new FileNameFormat(filenamePattern, new Configuration());
        this.archiveFileNameFormat = new FileNameFormat(archiveFilenamePattern, new Configuration());
    }

    public ZonedDateTime parseArchiveFileTime(String filename) {
        return archiveFileNameFormat.parseDate(filename);
    }

    public String getArchiveName(String filename, ZonedDateTime fileCreationTime) {
        FileInfo fileInfo = activeLogFileFormat.parse(filename);
        if (fileInfo == null) {
            return null;
        }
        fileInfo.setFileCreationTime(fileCreationTime);
        return getArchiveName(fileInfo);
    }

    String getArchiveName(FileInfo fileInfo) {
        return archiveFileNameFormat.generateName(fileInfo);
    }

    public Map<String, String> parseMdcValues(String filename) {
        return activeLogFileFormat.parse(filename).getMdc();
    }

    private static final int DEFAULT_BUFFER_SIZE = 8192;

    // TODO: If we upgrade to Java 9, replace with <code>in.transferTo(out);</code>
    private void transfer(InputStream in, OutputStream out) throws IOException {
        Objects.requireNonNull(out, "out");
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        int read;
        while ((read = in.read(buffer, 0, DEFAULT_BUFFER_SIZE)) >= 0) {
            out.write(buffer, 0, read);
        }
    }

    public String getArchiveName(ZonedDateTime fileTime) {
        return archiveFileNameFormat.generateName(fileTime);
    }
}


