package org.logevents.observers.batch;

import java.time.Duration;

public interface Scheduler {
    void schedule(Duration delay);

    void setAction(Runnable action);
}
