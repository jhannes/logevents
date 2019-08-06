package org.logevents.observers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

import org.logevents.status.LogEventStatus;

/**
 * Output to a given file.
 *
 * @author Johannes Brodwall
 *
 */
class FileDestination {

    protected Path logDirectory;
    private FileChannel channel;
    private Path openedPath;
    private Instant circuitBrokenUntil;
    private int successiveErrors;

    public FileDestination(Path logDirectory) {
        this.logDirectory = logDirectory;
        if (this.logDirectory == null) {
            this.logDirectory = Paths.get(".");
        }
    }

    public synchronized void writeEvent(String filename, String message) {
        if (isCircuitBroken()) {
            return;
        }
        Path path = logDirectory.resolve(filename);
        try {
            FileChannel channel = getChannel(path);
            ByteBuffer src = ByteBuffer.wrap(message.getBytes());
            try(FileLock ignored = channel.tryLock()) {
                channel.write(src);
            }
            successiveErrors = 0;
        } catch (IOException e) {
            LogEventStatus.getInstance().addError(this, e.getMessage(), e);
            checkIfCircuitShouldBreak();
        }
    }

    FileChannel getChannel(Path path) throws IOException {
        if (channel == null) {
            try {
                Files.createDirectories(this.logDirectory);
            } catch (IOException e) {
                LogEventStatus.getInstance().addFatal(this, "Can't create directory " + logDirectory, e);
            }
            openedPath = path;
            channel = FileChannel.open(openedPath, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
        } else if (!openedPath.equals(path)) {
            try {
                channel.close();
            } catch (IOException ignored) {
            }
            openedPath = path;
            channel = FileChannel.open(openedPath, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
        }
        return channel;
    }


    private void checkIfCircuitShouldBreak() {
        if (successiveErrors++ >= getCircuitBreakThreshold()) {
            setCircuitBrokenUntil(Instant.now().plusSeconds(10));
            try {
                if (channel != null) channel.close();
            } catch (IOException ignored) {
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

    public int getCircuitBreakThreshold() {
        return 10;
    }

    void setCircuitBrokenUntil(Instant circuitBrokenUntil) {
        this.circuitBrokenUntil = circuitBrokenUntil;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{dir=" + this.logDirectory + "}";
    }
}
