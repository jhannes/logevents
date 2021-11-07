package org.logevents.status;

import org.junit.Rule;
import org.junit.Test;
import org.logevents.config.Configuration;
import org.logevents.extend.junit.LogEventStatusRule;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class LogEventStatusTest {

    private final LogEventStatus instance = LogEventStatus.getInstance();

    @Rule
    public LogEventStatusRule statusRule = new LogEventStatusRule();

    @Test
    public void shouldConfigureLogEventStatusLevelForClass() {
        Map<String, String> properties = new HashMap<>();
        statusRule.setStatusLevel(StatusEvent.StatusLevel.INFO);
        instance.configure(new Configuration(properties, "logevents"));
        assertEquals(StatusEvent.StatusLevel.INFO, instance.getThreshold(this));

        properties.put("logevents.status.LogEventStatusTest", "DEBUG");
        instance.configure(new Configuration(properties, "logevents"));
        assertEquals(StatusEvent.StatusLevel.DEBUG, instance.getThreshold(this));
    }

    @Test
    public void shouldConfigureStatusFromEnvironment() {
        HashMap<String, String> environment = new HashMap<>();
        environment.put("LOGEVENTS_STATUS", "CONFIG");
        instance.configure(new Configuration(new HashMap<>(), "logevents", environment));
        assertEquals(StatusEvent.StatusLevel.CONFIG, instance.getThreshold(this));
    }

    @Test
    public void shouldListTailMessage() {
        instance.clear();
        assertNull(instance.lastMessage());
        instance.addTrace(this, "Dummy message");
        assertEquals("Dummy message", instance.lastMessage().getMessage());
        for (int i=0; i<1000; i++) {
            instance.addTrace(this, "Dummy message");
        }
        instance.addTrace(this, "Tail message");
        assertEquals("Tail message", instance.lastMessage().getMessage());
        instance.clear();
    }
}
