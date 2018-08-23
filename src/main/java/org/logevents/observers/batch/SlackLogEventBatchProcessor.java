package org.logevents.observers.batch;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import org.logevents.status.LogEventStatus;
import org.logevents.util.Configuration;
import org.logevents.util.JsonUtil;
import org.logevents.util.NetUtils;

public class SlackLogEventBatchProcessor implements LogEventBatchProcessor {

    private Optional<String> username = Optional.empty();
    private Optional<String> channel = Optional.empty();
    private URL slackUrl;
    private SlackLogEventsFormatter slackLogEventsFormatter;

    public SlackLogEventBatchProcessor(URL url) {
        this.slackUrl = url;
        setSlackLogEventsFormatter(new SlackLogEventsFormatter());
    }

    public SlackLogEventBatchProcessor(Properties properties, String prefix) throws MalformedURLException {
        Configuration configuration = new Configuration(properties, prefix);
        username = configuration.optionalString("username");
        channel = configuration.optionalString("channel");
        slackUrl = configuration.optionalUrl("slackUrl").orElse(null);
        setSlackLogEventsFormatter(configuration.createInstance("slackLogEventsFormatter", SlackLogEventsFormatter.class));

        LogEventStatus.getInstance().addInfo(this, "Configured " + prefix);
    }

    public void setSlackLogEventsFormatter(SlackLogEventsFormatter slackLogEventsFormatter) {
        this.slackLogEventsFormatter = slackLogEventsFormatter;
    }

    public void setUsername(String username) {
        this.username = Optional.ofNullable(username);
    }

    public void setChannel(String channel) {
        this.channel = Optional.ofNullable(channel);
    }

    @Override
    public void processBatch(List<LogEventGroup> batch) {
        if (slackUrl == null) {
            return;
        }
        Map<String, Object> slackMessage;
        try {
            slackMessage = slackLogEventsFormatter.createSlackMessage(batch, username, channel);
        } catch (Exception e) {
            LogEventStatus.getInstance().addFatal(this, "Runtime error generating slack message", e);
            return;
        }
        try {
            NetUtils.postJson(slackUrl, JsonUtil.toIndentedJson(slackMessage));
        } catch (IOException e) {
            LogEventStatus.getInstance().addError(this, "Failed to send slack message", e);
            return;
        }
    }

}
