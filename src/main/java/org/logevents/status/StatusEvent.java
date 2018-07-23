package org.logevents.status;

import java.time.Instant;

public class StatusEvent {

    private Object location;
    private String message;
    private Level level;
    private Throwable throwable;
    private Instant time;

    public StatusEvent(Object location, String message, Level level, Throwable throwable) {
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




    public Level getLevel() {
        return level;
    }




    public Throwable getThrowable() {
        return throwable;
    }




    public Instant getTime() {
        return time;
    }




    public static enum Level {
        INFO(20), ERROR(40), FATAL(50);

        private int levelInt;

        Level(int levelInt) {
            this.levelInt = levelInt;
        }

        public int toInt() {
            return levelInt;
        }
    }

    public String formatMessage() {
        return location + ": " + message + (throwable != null ? throwable.toString() : "");
    }

}
