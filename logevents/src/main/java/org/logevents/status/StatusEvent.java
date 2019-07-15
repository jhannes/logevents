package org.logevents.status;

import java.time.Instant;
import java.util.Objects;

public class StatusEvent {

    private final Thread thread;
    private Object location;
    private String message;
    private StatusLevel level;
    private Throwable throwable;
    private Instant time;

    public StatusEvent(Object location, String message, StatusLevel level, Throwable throwable) {
        this.thread = Thread.currentThread();
        this.location = location;
        this.message = message;
        this.level = level;
        this.throwable = throwable;
        this.time = Instant.now();
    }

    public Object getLocation() {
        return location;
    }

    public String getMessage() {
        return message;
    }

    public StatusLevel getLevel() {
        return level;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public Instant getTime() {
        return time;
    }

    public String formatMessage() {
        return location + ": " + message + (throwable != null ? " " + throwable.toString() : "");
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + formatMessage() + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StatusEvent that = (StatusEvent) o;
        return Objects.equals(location, that.location) &&
                Objects.equals(message, that.message) &&
                level == that.level &&
                exceptionEquals(throwable, that.throwable);
    }

    boolean exceptionEquals(Throwable self, Throwable other) {
        if (self == null || other == null) {
            return other == self;
        }
        return Objects.equals(self.getMessage(), other.getMessage()) &&
                Objects.equals(self.getClass(), other.getClass());
    }

    @Override
    public int hashCode() {
        return Objects.hash(location, message, level);
    }

    public Thread getThread() {
        return thread;
    }

    public enum StatusLevel {
        TRACE(10), DEBUG(20), CONFIG(30), INFO(40), ERROR(50), FATAL(60), NONE(100);

        private int levelInt;

        StatusLevel(int levelInt) {
            this.levelInt = levelInt;
        }

        public int toInt() {
            return levelInt;
        }
    }


}
