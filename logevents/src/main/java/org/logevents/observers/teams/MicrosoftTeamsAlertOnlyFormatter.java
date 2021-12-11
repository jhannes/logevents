package org.logevents.observers.teams;

import org.logevents.LogEvent;

import java.util.Map;

/**
 * Use with MicrosoftTeamEventObserver. Only send alerting information to Teams,
 * avoiding potentially sensitive details, but include a link to LogEventsServlet
 * for further exploration
 */
public class MicrosoftTeamsAlertOnlyFormatter extends MicrosoftTeamsMessageFormatter {
    public MicrosoftTeamsAlertOnlyFormatter(Map<String, String> properties, String prefix) {
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
