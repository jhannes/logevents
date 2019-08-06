package org.logevents.observers.batch;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public interface Scheduler {
    void schedule(Duration delay);

    void setAction(Runnable action);

    boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException;
}
