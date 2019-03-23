package org.logevents.observers;

import org.logevents.observers.batch.Scheduler;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ExecutorScheduler implements Scheduler {
    private ScheduledExecutorService executor;
    private Runnable action;

    public ExecutorScheduler(ScheduledExecutorService executor) {
        this.executor = executor;
    }

    @Override
    public void schedule(Duration delay) {
        executor.schedule(action, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void setAction(Runnable action) {
        this.action = action;
    }
}
