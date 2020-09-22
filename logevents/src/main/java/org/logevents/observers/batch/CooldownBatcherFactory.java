package org.logevents.observers.batch;

import org.logevents.config.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

public class CooldownBatcherFactory implements BatcherFactory {
    private final ScheduledExecutorService executor;
    private LogEventShutdownHook shutdownHook;
    private final Optional<Duration> idleThreshold;
    private final Optional<Duration> cooldownTime;
    private final Optional<Duration> maximumWaitTime;

    public CooldownBatcherFactory(ScheduledExecutorService executor, LogEventShutdownHook shutdownHook, Configuration configuration, String prefix) {
        this.executor = executor;
        this.shutdownHook = shutdownHook;
        this.idleThreshold = configuration.optionalString(prefix + "idleThreshold").map(Duration::parse);
        this.cooldownTime = configuration.optionalString(prefix + "cooldownTime").map(Duration::parse);
        this.maximumWaitTime = configuration.optionalString(prefix + "maximumWaitTime").map(Duration::parse);
    }

    public CooldownBatcherFactory(ScheduledExecutorService executor, LogEventShutdownHook shutdownHook) {
        this.executor = executor;
        this.shutdownHook = shutdownHook;
        this.idleThreshold = Optional.empty();
        this.cooldownTime = Optional.empty();
        this.maximumWaitTime = Optional.empty();
    }

    @Override
    public <T> Batcher<T> createBatcher(Consumer<List<T>> processor) {
        CooldownBatcher<T> batcher = new CooldownBatcher<>(processor, executor);
        configureBatcher(batcher);
        shutdownHook.addAction(batcher::shutdown);
        return batcher;
    }

    private <T> void configureBatcher(CooldownBatcher<T> batcher) {
        idleThreshold.ifPresent(batcher::setIdleThreshold);
        maximumWaitTime.ifPresent(batcher::setMaximumWaitTime);
        cooldownTime.ifPresent(batcher::setCooldownTime);
    }
}
