package org.logevents.extend.azure;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.telemetry.ExceptionTelemetry;
import com.microsoft.applicationinsights.telemetry.SeverityLevel;
import com.microsoft.applicationinsights.telemetry.Telemetry;
import com.microsoft.applicationinsights.telemetry.TraceTelemetry;
import org.logevents.LogEvent;
import org.logevents.config.Configuration;
import org.logevents.formatting.MessageFormatter;
import org.logevents.observers.AbstractFilteredLogEventObserver;
import org.logevents.status.LogEventStatus;
import org.slf4j.event.Level;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Log to Application Insights on Azure. Will read APPINSIGHTS_INSTRUMENTATIONKEY environment variable
 * set by Azure Webapps if Application Insights is turned on, or
 * <code>observer.applicationInsights.instrumentationKey=</code> if set. If no instrumentation key,
 * {@link ApplicationInsightsLogEventObserver} will simply drop messages, which means that the same
 * configuration can be used for environments with and without ApplicationInsights set up.
 * This class uses an optional dependency on com.microsoft.azure:applicationinsights-core,
 * which you must include in order to use it.
 */
public class ApplicationInsightsLogEventObserver extends AbstractFilteredLogEventObserver {

    private TelemetryClient telemetryClient;

    public ApplicationInsightsLogEventObserver(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public ApplicationInsightsLogEventObserver(Configuration configuration) {
        this(configuration.optionalString("instrumentationKey")
                .orElseGet(() -> System.getenv("APPINSIGHTS_INSTRUMENTATIONKEY")));
        configureFilter(configuration);
        configuration.checkForUnknownFields();
    }

    public ApplicationInsightsLogEventObserver(String instrumentationKey) {
        if (instrumentationKey == null) {
            LogEventStatus.getInstance().addInfo(this, "Disabled - missing APPINSIGHTS_INSTRUMENTATIONKEY");
        } else {
            LogEventStatus.getInstance().addConfig(this, "Logging to ApplicationInsights");
            telemetryClient = new TelemetryClient();
            telemetryClient.getContext().setInstrumentationKey(instrumentationKey);
        }
    }

    @Override
    protected boolean shouldLogEvent(LogEvent logEvent) {
        return telemetryClient != null && super.shouldLogEvent(logEvent);
    }

    @Override
    protected void doLogEvent(LogEvent event) {
        Telemetry telemetry = toTelemetry(event);
        telemetryClient.track(telemetry);
    }

    Telemetry toTelemetry(LogEvent event) {
        Telemetry telemetry;
        if (event.getThrowable() != null) {
            ExceptionTelemetry exceptionTelemetry = new ExceptionTelemetry(event.getThrowable());
            exceptionTelemetry.setSeverityLevel(getLevel(event.getLevel()));
            telemetry = exceptionTelemetry;
        } else {
            String message = new MessageFormatter().format(event.getMessage(), event.getArgumentArray());
            telemetry = new TraceTelemetry(message, getLevel(event.getLevel()));
        }
        telemetry.getContext().getProperties().putAll(getCustomParameters(event));
        telemetry.setTimestamp(new Date(event.getTimeStamp()));
        return telemetry;
    }

    private SeverityLevel getLevel(Level level) {
        switch (level) {
            case ERROR: return SeverityLevel.Error;
            case WARN: return SeverityLevel.Warning;
            case INFO: return SeverityLevel.Information;
            default: return SeverityLevel.Verbose;
        }
    }

    private Map<String, String> getCustomParameters(LogEvent event) {
        Map<String, String> customParameters = new HashMap<>();

        customParameters.put("LoggerName", event.getLoggerName());
        customParameters.put("LoggingLevel", event.getLevel().toString());
        customParameters.put("ThreadName", event.getThreadName());
        if(event.getMarker() != null) {
            customParameters.put("Marker", event.getMarker().toString());
        }

        customParameters.putAll(event.getMdcProperties());
        return customParameters;
    }
}
