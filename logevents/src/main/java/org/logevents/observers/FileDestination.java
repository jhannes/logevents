package org.logevents.observers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.time.Instant;

import org.logevents.status.LogEventStatus;

/**
 * Output to a given file.
 *
 * @author Johannes Brodwall
 *
 */
class FileDestination {

    private FileChannelTracker fileChannelTracker = new FileChannelTracker();
    private Instant circuitBrokenUntil;
    private int successiveErrors;

    public synchronized void writeEvent(Path path, String message) {
        if (isCircuitBroken()) {
            return;
        }
        FileChannel channel = null;
        try {
            channel = fileChannelTracker.getChannel(path, Instant.now());
            ByteBuffer src = ByteBuffer.wrap(message.getBytes());
            try(FileLock ignored = channel.tryLock()) {
                channel.write(src);
            }
            successiveErrors = 0;
        } catch (IOException e) {
            LogEventStatus.getInstance().addError(this, e.getMessage(), e);
            checkIfCircuitShouldBreak(channel);
        }
    }

    private void checkIfCircuitShouldBreak(FileChannel channel) {
        if (successiveErrors++ >= getCircuitBreakThreshold()) {
            setCircuitBrokenUntil(Instant.now().plusSeconds(10));
            try {
                if (channel != null)  {
                    channel.close();
                }
            } catch (IOException ignored) {
            }
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
        return getClass().getSimpleName();
    }
}
