package org.logevents.observers.batch;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

// TODO: The abstraction here is a bit off - there should probably be a factory that takes
//  Runnable action and returns a Scheduler, but that may be harder to test
public class ExecutorScheduler implements Scheduler {
    private ScheduledExecutorService executor;
    private LogEventShutdownHook shutdownHook;
    private Runnable action;
    private ScheduledFuture<?> scheduledTask;

    public ExecutorScheduler(ScheduledExecutorService executor, LogEventShutdownHook shutdownHook) {
        this.executor = executor;
        this.shutdownHook = shutdownHook;
    }

    @Override
    public void schedule(Duration delay) {
        if (scheduledTask != null && !scheduledTask.isDone()) {
            scheduledTask.cancel(false);
        }
        scheduledTask = executor.schedule(action, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return executor.awaitTermination(timeout, unit);
    }

    @Override
    public void setAction(Runnable action) {
        this.action = action;
        this.shutdownHook.addAction(action);
    }
}
