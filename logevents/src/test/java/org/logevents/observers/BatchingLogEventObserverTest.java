package org.logevents.observers;

import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.config.Configuration;
import org.logevents.extend.junit.LogEventSampler;
import org.logevents.observers.batch.LogEventBatch;
import org.logevents.observers.batch.LogEventBatcher;
import org.logevents.observers.batch.LogEventBatcherWithMdc;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.event.Level;

import java.util.Arrays;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class BatchingLogEventObserverTest {

    private class Observer extends AbstractBatchingLogEventObserver {

        public Observer(Properties properties, String prefix) {
            Configuration config = new Configuration(properties, prefix);
            configureBatching(config);
            configureMarkers(config);
            configureFilter(config, Level.TRACE);
        }

        @Override
        protected void processBatch(LogEventBatch batch) {
        }

    }

    @Test
    public void shouldSendSeparateBatchesForThrottledMarkers() {
        Marker myMarker = MarkerFactory.getMarker("MY_MARKER_123");
        Marker otherMarker = MarkerFactory.getMarker("OTHER_MARKER");
        Marker groupMarker = MarkerFactory.getMarker("MDC_GROUP_MARKER");
        groupMarker.add(myMarker);

        Properties properties = new Properties();
        properties.put("observer.test.markers.MY_MARKER_123.idleThreshold", "PT2S");
        properties.put("observer.test.markers.OTHER_MARKER.idleThreshold", "PT2S");
        Observer observer = new Observer(properties, "observer.test");

        LogEvent myFirstEvent = new LogEventSampler().withMarker(myMarker).build();
        LogEvent mySecondEvent = new LogEventSampler().withMarker(myMarker).build();
        LogEvent myGroupEvent = new LogEventSampler().withMarker(groupMarker).build();
        LogEvent otherEvent = new LogEventSampler().withMarker(otherMarker).build();
        LogEvent unknownEvent = new LogEventSampler().withMarker().build();

        observer.logEvent(myFirstEvent);
        observer.logEvent(mySecondEvent);
        observer.logEvent(myGroupEvent);
        observer.logEvent(otherEvent);
        observer.logEvent(unknownEvent);

        assertEquals(Arrays.asList(myFirstEvent, mySecondEvent, myGroupEvent),
                ((LogEventBatcher)observer.getMarkerBatcher(myMarker)).getCurrentBatch());
        assertEquals(Arrays.asList(otherEvent),
                ((LogEventBatcher)observer.getMarkerBatcher(otherMarker)).getCurrentBatch());
        assertEquals(Arrays.asList(unknownEvent),
                ((LogEventBatcher)observer.getDefaultBatcher()).getCurrentBatch());
    }

    @Test
    public void shouldSendSeparateBatchesForMdcThrottledMarkers() {
        Marker myMarker = MarkerFactory.getMarker("MDC_MARKER");
        Marker otherMarker = MarkerFactory.getMarker("OTHER_MARKER");

        Properties properties = new Properties();
        properties.put("observer.test.markers.MDC_MARKER.idleThreshold", "PT2S");
        properties.put("observer.test.markers.MDC_MARKER.mdc", "userId");
        Observer observer = new Observer(properties, "observer.test");

        LogEventSampler sampler = new LogEventSampler().withMarker(myMarker);

        LogEvent firstForAlice = sampler.withMdc("userId", "alice").build();
        LogEvent secondForAlice = sampler.withMdc("userId", "alice").build();
        LogEvent firstForBob = sampler.withMdc("userId", "bob").build();
        LogEvent withoutMdc = new LogEventSampler().withMarker(myMarker).build();
        LogEvent otherEvent = sampler.withMarker(otherMarker).withMdc("userId", "bob").build();

        observer.logEvent(firstForAlice);
        observer.logEvent(firstForBob);
        observer.logEvent(otherEvent);
        observer.logEvent(withoutMdc);
        observer.logEvent(secondForAlice);

        LogEventBatcherWithMdc markerBatcher = (LogEventBatcherWithMdc) observer.getMarkerBatcher(myMarker);
        assertEquals(Arrays.asList(withoutMdc), markerBatcher.getCurrentBatch());
        assertEquals(Arrays.asList(firstForAlice, secondForAlice),
                markerBatcher.getBatcher("alice").getCurrentBatch());
        assertEquals(Arrays.asList(firstForBob),
                markerBatcher.getBatcher("bob").getCurrentBatch());
    }
}
