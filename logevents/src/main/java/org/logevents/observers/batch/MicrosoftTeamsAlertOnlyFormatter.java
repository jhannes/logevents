package org.logevents.observers.batch;

import org.logevents.LogEvent;

import java.util.Properties;

/**
 * Use with MicrosoftTeamEventObserver. Only send alerting information to Teams,
 * avoiding potentially sensitive details, but include a link to LogEventsServlet
 * for further exploration
 */
public class MicrosoftTeamsAlertOnlyFormatter extends MicrosoftTeamsMessageFormatter {
    public MicrosoftTeamsAlertOnlyFormatter(Properties properties, String prefix) {
        super(properties, prefix);
    }

    @Override
    protected String formatMessage(LogEvent event) {
        return event.getMessage();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{}";
    }
}
