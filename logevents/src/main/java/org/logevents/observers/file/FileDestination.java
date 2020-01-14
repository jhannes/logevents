package org.logevents.observers.file;

import org.logevents.status.LogEventStatus;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Output to a given file.
 *
 * @author Johannes Brodwall
 *
 */
public class FileDestination {

    private FileChannelTracker fileTracker;
    private Instant circuitBrokenUntil;
    private int successiveErrors;

    public FileDestination(boolean lockOnWrite) {
        fileTracker = new FileChannelTracker(lockOnWrite);
    }

    public void writeEvent(Path path, String message) {
        if (isCircuitBroken()) {
            return;
        }
        try {
            fileTracker.writeToFile(path, message);
            successiveErrors = 0;
        } catch (IOException e) {
            LogEventStatus.getInstance().addError(this, e.getMessage(), e);
            checkIfCircuitShouldBreak();
        }
    }

    private void checkIfCircuitShouldBreak() {
        if (successiveErrors++ >= getCircuitBreakThreshold()) {
            setCircuitBrokenUntil(Instant.now().plusSeconds(10));
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

    public void reset() {
        fileTracker.reset();
    }
}
