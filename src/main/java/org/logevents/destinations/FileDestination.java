package org.logevents.destinations;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Properties;

import org.logevents.status.LogEventStatus;
import org.logevents.util.Configuration;

/**
 * Output to a given file.
 *
 * @author Johannes Brodwall
 *
 */
public class FileDestination implements LogEventDestination {

    protected Path logDirectory;
    private FileChannel channel;
    private Path openedPath;
    private Instant circuitBrokenUntil;
    private int successiveErrors;
    private Path fileName;

    public FileDestination(String filename) {
        this(Paths.get(filename));
    }

    public FileDestination(Configuration configuration) {
        this(configuration.getString("filename"));
    }

    public FileDestination(Properties configuration, String prefix) {
        this(new Configuration(configuration, prefix));
    }

    public FileDestination(Path path) {
        this(path.getParent(), path.getFileName());
    }

    public FileDestination(Path parent, Path fileName) {
        this.logDirectory = parent;
        this.fileName = fileName;
        if (logDirectory == null) {
            logDirectory = Paths.get(".");
        } else {
            try {
                Files.createDirectories(logDirectory);
            } catch (IOException e) {
                LogEventStatus.getInstance().addFatal(this, "Can't create directory " + logDirectory, e);
            }
        }
    }

    @Override
    public synchronized void writeEvent(String message) {
        if (isCircuitBroken()) {
            return;
        }
        try {
            if (channel == null) {
                openedPath = getPath();
                channel = FileChannel.open(openedPath, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
            } else if (!openedPath.equals(getPath())) {
                try {
                    channel.close();
                } catch (IOException e) {
                }
                openedPath = getPath();
                channel = FileChannel.open(openedPath, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
            }
            ByteBuffer src = ByteBuffer.wrap(message.getBytes());
            try(FileLock lock = channel.tryLock()) {
                channel.write(src);
            }
            successiveErrors = 0;
        } catch (IOException e) {
            LogEventStatus.getInstance().addError(this, e.getMessage(), e);
            checkIfCircuitShouldBreak();
        }
    }


    private void checkIfCircuitShouldBreak() {
        if (successiveErrors++ >= getCircuitBreakThreshold()) {
            setCircuitBrokenUntil(Instant.now().plusSeconds(10));
            try {
                if (channel != null) channel.close();
            } catch (IOException e) {
            }
            channel = null;
        }
    }

    private boolean isCircuitBroken() {
        if (circuitBrokenUntil == null) {
            return false;
        } else if (circuitBrokenUntil.isBefore(Instant.now())) {
            // restore
            setCircuitBrokenUntil(null);
            return false;
        } else {
            return true;
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + getPath().getFileName() + "}";
    }

    public Path getPath() {
        return logDirectory.resolve(fileName);
    }

    public synchronized void setPath(Path path) {
        this.logDirectory = path.getParent();
        this.fileName = path.getFileName();
    }

    public int getCircuitBreakThreshold() {
        return 10;
    }

    void setCircuitBrokenUntil(Instant circuitBrokenUntil) {
        this.circuitBrokenUntil = circuitBrokenUntil;
    }
}
