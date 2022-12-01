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

/**
 * Archives and expires old files based on file name patterns. Contains
 * {@link #activeLogFilenameFormatter} and {@link #archiveFilenameFormatter} with describes the format for the filenames
 * for the current active logfile and archived logfiles respectively.
 *
 * <p>Assumes that each day of logging for the active logfile will yield a unique archived logfile. If either
 * FilenameFormatter contains variables from MDC or Marker, both should contain the same parameter set, or unique
 * archived files cannot be created.</p>
 *
 * <p>Assumes that {@link #nextExecution()} should happen each at midnight according to {@link Configuration#getLocale()}</p>
 */
public class FileRotationWorker {

    /**
     * The filename formatter for the archived log files
     */
    private final FilenameFormatter archiveFilenameFormatter;

    /**
     * The filename formatter for the active log files
     */
    private final FilenameFormatter activeLogFilenameFormatter;

    /**
     * How long should archived logfiles be kept before being deleted? The %date specified in the
     * {@link #archiveFilenameFormatter} is used to calculate when a file should be deleted. If retention
     * is null, files are never deleted
     */
    private Period retention;

    /**
     * Compress archived files after this period. The %date specified in the {@link #archiveFilenameFormatter} is used
     * to calculate when a file should be compressed. If null, files are never compressed
     */
    private Period compressAfter;

    public FileRotationWorker(FilenameFormatter activeLogFilenameFormatter, FilenameFormatter archiveFilenameFormatter) {
        this.activeLogFilenameFormatter = activeLogFilenameFormatter;
        this.archiveFilenameFormatter = archiveFilenameFormatter;
    }

    public FileRotationWorker(String filenamePattern, String archiveFilenamePattern, Configuration configuration) {
        this.activeLogFilenameFormatter = new FilenameFormatter(filenamePattern, configuration);
        this.archiveFilenameFormatter = new FilenameFormatter(archiveFilenamePattern, configuration);
    }

    public FilenameFormatter getActiveLogFilenameFormatter() {
        return activeLogFilenameFormatter;
    }

    public void setRetention(Period retention) {
        this.retention = retention;
    }

    public void setCompressAfter(Period compressAfter) {
        this.compressAfter = compressAfter;
    }

    public void rollover() {
        LogEventStatus.getInstance().addDebug(this, "Performing rollover");
        archiveActiveLogfiles();
        expireArchivedLogfiles();
        compressArchivedLogfiles();
    }

    private void archiveActiveLogfiles() {
        for (String file : activeLogFilenameFormatter.findFileNames()) {
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
        if (compressAfter != null) {
            for (String archivedFile : archiveFilenameFormatter.findFileNames()) {
                if (archivedFile.endsWith(".gz")) continue;
                LogEventStatus.getInstance().addTrace(this, "Checking if file should be compressed: " + archivedFile);
                try {
                    ZonedDateTime archiveDate = archiveFilenameFormatter.parseDate(archivedFile);
                    if (archiveDate.isBefore(ZonedDateTime.now().minus(compressAfter))) {
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
        FileInfo fileInfo = activeLogFilenameFormatter.parse(filename);
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
    public static void transfer(InputStream in, OutputStream out) throws IOException {
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


