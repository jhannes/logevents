package org.logevents.observers.batch;

import org.logevents.LogEvent;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
    protected List<Map<String, Object>> createSections(LogEventBatch batch) {
        return Arrays.asList(createOverviewSection(batch));
    }

    @Override
    protected String formatMessage(LogEvent event) {
        return event.getMessage();
    }
}
