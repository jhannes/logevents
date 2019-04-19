package org.logevents.observers.batch;

import org.logevents.LogEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Use with SlackLogEventObserver. Only send alerting information to Slack,
 * but include a link to LogEventsServlet for further exploration
 */
public class SlackAlertOnlyFormatter extends SlackLogEventsFormatter {

    public SlackAlertOnlyFormatter() {
    }

    public SlackAlertOnlyFormatter(Optional<String> username, Optional<String> channel) {
        super(username, channel);
    }

    @Override
    protected String formatMessage(LogEvent logEvent) {
        return logEvent.getMessage();
    }

    @Override
    protected List<Map<String, Object>> createAttachments(LogEventBatch batch) {
        LogEvent event = batch.firstHighestLevelLogEventGroup().headMessage();
        ArrayList<Map<String, Object>> result = new ArrayList<>();
        result.add(createDetailsAttachment(event));
        return result;
    }
}
