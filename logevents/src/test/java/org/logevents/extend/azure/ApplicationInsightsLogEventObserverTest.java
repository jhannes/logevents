package org.logevents.extend.azure;

import com.microsoft.applicationinsights.telemetry.ExceptionTelemetry;
import com.microsoft.applicationinsights.telemetry.SeverityLevel;
import com.microsoft.applicationinsights.telemetry.TraceTelemetry;
import org.junit.Rule;
import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.config.Configuration;
import org.logevents.extend.junit.LogEventStatusRule;
import org.logevents.extend.junit.LogEventSampler;
import org.logevents.status.StatusEvent;
import org.slf4j.event.Level;

import static org.junit.Assert.assertEquals;

public class ApplicationInsightsLogEventObserverTest {

    @Rule
    public LogEventStatusRule logEventStatusRule = new LogEventStatusRule(StatusEvent.StatusLevel.ERROR);


    @Test
    public void shouldGenerateTelemetryWithoutException() {
        ApplicationInsightsLogEventObserver observer = new ApplicationInsightsLogEventObserver(new Configuration());
        LogEvent logEvent = new LogEventSampler().withLevel(Level.INFO).build();
        TraceTelemetry telemetry = (TraceTelemetry) observer.toTelemetry(logEvent);
        assertEquals(SeverityLevel.Information, telemetry.getSeverityLevel());
        assertEquals(logEvent.getMessage(), telemetry.getMessage());
    }

    @Test
    public void shouldGenerateTelemetryException() {
        ApplicationInsightsLogEventObserver observer = new ApplicationInsightsLogEventObserver(new Configuration());
        LogEvent logEvent = new LogEventSampler().withLevel(Level.WARN).withThrowable().build();
        ExceptionTelemetry telemetry = (ExceptionTelemetry) observer.toTelemetry(logEvent);
        assertEquals(SeverityLevel.Warning, telemetry.getSeverityLevel());
        assertEquals(logEvent.getLoggerName(), telemetry.getContext().getProperties().get("LoggerName"));
        assertEquals(logEvent.getThrowable(), telemetry.getThrowable());
    }



}
