package org.logevents.observers;

import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.config.Configuration;
import org.logevents.extend.servlets.LogEventSampler;
import org.logevents.observers.batch.LogEventBatch;
import org.logevents.observers.batch.Scheduler;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class BatchingLogEventObserverTest {

    private List<Runnable> flushActions = new ArrayList<>();

    private class FlusherScheduler implements Scheduler {
        @Override
        public void scheduleFlush(Duration delay) {

        }

        @Override
        public void setAction(Runnable action) {
            flushActions.add(action);
        }
    }

    private class Observer extends BatchingLogEventObserver {
        List<LogEventBatch> batches = new ArrayList<>();

        public Observer(FlusherScheduler flusherScheduler) {
            super(() -> flusherScheduler);
        }

        public Observer(Properties properties, String prefix) {
            this(new FlusherScheduler());
            Configuration configuration = new Configuration(properties, prefix);

            configureFilter(configuration);
            configureBatching(configuration);
            configuration.checkForUnknownFields();
        }

        @Override
        protected void processBatch(LogEventBatch batch) {
            batches.add(batch);
        }

    }

    @Test
    public void shouldSendSeparateBatchesForThrottledMarkers() {
        Marker myMarker = MarkerFactory.getMarker("MY_MARKER");

        ArrayList<LogEventBatch> batches = new ArrayList<>();
        Observer observer = new Observer(new Properties(), "observer.test");

        Properties properties = new Properties();
        properties.put("observer.test.markers.MY_MARKER.throttle", "PT0.1S PT0.3S");
        observer.configureMarkers(new Configuration(properties, "observer.test"));
        observer.getMarker(myMarker).setBatchProcessor(batches::add);

        LogEvent event = new LogEventSampler().withMarker(myMarker).build();
        observer.logEvent(event);
        flushActions.forEach(Runnable::run);
        assertEquals(Arrays.asList(new LogEventBatch().add(event)), batches);
    }
}
