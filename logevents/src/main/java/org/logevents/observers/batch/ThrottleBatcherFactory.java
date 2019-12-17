package org.logevents.observers.batch;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

public class ThrottleBatcherFactory implements BatcherFactory {
    private final ScheduledExecutorService executor;
    private LogEventShutdownHook shutdownHook;
    private final String throttles;

    public ThrottleBatcherFactory(ScheduledExecutorService executor, LogEventShutdownHook shutdownHook, String throttles) {
        this.executor = executor;
        this.shutdownHook = shutdownHook;
        this.throttles = throttles;
    }

    @Override
    public <T> Batcher<T> createBatcher(Consumer<List<T>> processor) {
        ThrottlingBatcher<T> batcher = new ThrottlingBatcher<>(throttles, processor, executor);
        shutdownHook.addAction(batcher::flush);
        return batcher;
    }

}
