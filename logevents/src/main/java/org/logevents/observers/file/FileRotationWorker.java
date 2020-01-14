package org.logevents.observers.file;

import org.logevents.config.Configuration;
import org.logevents.status.LogEventStatus;

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
import java.util.Objects;
import java.util.zip.GZIPOutputStream;

public class FileRotationWorker {

    private final FilenameFormatter archiveFilenameFormatter;
    private final FilenameFormatter activeLogFileFormat;
    private Period retention;
    private Period uncompressedRetention;

    public FileRotationWorker(FilenameFormatter activeLogFileFormat, FilenameFormatter archiveFilenameFormatter) {
        this.activeLogFileFormat = activeLogFileFormat;
        this.archiveFilenameFormatter = archiveFilenameFormatter;
    }

    public FileRotationWorker(String filenamePattern, String archiveFilenamePattern) {
        this.activeLogFileFormat = new FilenameFormatter(filenamePattern, new Configuration());
        this.archiveFilenameFormatter = new FilenameFormatter(archiveFilenamePattern, new Configuration());
    }

    public void setRetention(Period retention) {
        this.retention = retention;
    }

    public void setUncompressedRetention(Period uncompressedRetention) {
        this.uncompressedRetention = uncompressedRetention;
    }

    public void rollover() {
        LogEventStatus.getInstance().addDebug(this, "Performing rollover");
        archiveActiveLogfiles();
        expireArchivedLogfiles();
        compressArchivedLogfiles();
    }

    private void archiveActiveLogfiles() {
        for (String file : activeLogFileFormat.findFileNames()) {
            LogEventStatus.getInstance().addTrace(this, "Checking if file should be archived: " + file);
            try {
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
                    LogEventStatus.getInstance().addDebug(this, "Archived: " + file + " to " + target);
                    Files.move(path, target); // TODO: Do an atomic move to different stores
                }
            } catch (IOException e) {
                LogEventStatus.getInstance().addError(this, "Failed to archive active logfile " + file, e);
            }
        }
    }

    private void compressArchivedLogfiles() {
        if (uncompressedRetention != null) {
            for (String archivedFile : archiveFilenameFormatter.findFileNames()) {
                LogEventStatus.getInstance().addTrace(this, "Checking if file should be compressed: " + archivedFile);
                try {
                    ZonedDateTime archiveDate = archiveFilenameFormatter.parseDate(archivedFile);
                    if (archiveDate.isBefore(ZonedDateTime.now().minus(uncompressedRetention))) {
                        LogEventStatus.getInstance().addDebug(this, "Compressing " + archivedFile);
                        compress(archivedFile);
                    }
                } catch (IOException e) {
                    LogEventStatus.getInstance().addError(this, "Failed to compress archived logfile " + archivedFile, e);
                }
            }
        }
    }

    private void expireArchivedLogfiles() {
        if (retention != null) {
            for (String archivedFile : archiveFilenameFormatter.findFileNames()) {
                LogEventStatus.getInstance().addTrace(this, "Checking if file should be expired: " + archivedFile);
                try {
                    ZonedDateTime archiveDate = archiveFilenameFormatter.parseDate(archivedFile);
                    if (archiveDate.isBefore(ZonedDateTime.now().minus(retention))) {
                        LogEventStatus.getInstance().addDebug(this, "Expiring " + archivedFile);
                        Files.delete(Paths.get(archivedFile));
                    }
                } catch (IOException e) {
                    LogEventStatus.getInstance().addError(this, "Failed to expire archived logfile " + archivedFile, e);
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

    public String getArchiveName(String filename, ZonedDateTime fileCreationTime) {
        FileInfo fileInfo = activeLogFileFormat.parse(filename);
        if (fileInfo == null) {
            return null;
        }
        fileInfo.setFileCreationTime(fileCreationTime);
        return getArchiveName(fileInfo);
    }

    String getArchiveName(FileInfo fileInfo) {
        return archiveFilenameFormatter.generateName(fileInfo);
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
        return archiveFilenameFormatter.generateName(fileTime);
    }

    public ZonedDateTime nextExecution() {
        return LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault());
    }

    @Override
    public String toString() {
        return "FileRotationWorker{archiveFilenameFormatter=" + archiveFilenameFormatter + ",retention=" + retention + '}';
    }
}


