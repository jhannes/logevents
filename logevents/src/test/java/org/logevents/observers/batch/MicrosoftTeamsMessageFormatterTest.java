package org.logevents.observers.batch;


import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.util.JsonUtil;
import org.slf4j.event.Level;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MicrosoftTeamsMessageFormatterTest {

    private String loggerName = getClass().getName();

    @Test
    public void shouldIncludeLevelInTeamsMessage() {
        LogEventBatch batch = new LogEventBatch();
        batch.add(new LogEvent(loggerName, Level.WARN, "A lesser important message", new Object[0]));
        batch.add(new LogEvent(loggerName, Level.ERROR, null, "A more important message", new Object[0]));
        batch.add(new LogEvent(loggerName, Level.ERROR, null, "A more important message", new Object[0]));
        batch.add(new LogEvent(loggerName, Level.ERROR, "Yet another message", new Object[0]));

        Map<String, Object> teamsMessage = new MicrosoftTeamsMessageFormatter().createMessage(batch);
        Map<String, Object> detailsSection = JsonUtil.getObject(JsonUtil.getList(teamsMessage, "sections"), 0);

        String level = null;
        for (Map<String, Object> fact : ((List<Map<String, Object>>) detailsSection.get("facts"))) {
            if (fact.get("name").equals("Level")) {
                level = (String)fact.get("value");
            }
        }
        assertEquals("ERROR", level);
    }

    private void assertContains(String expected, String actual) {
        assertTrue("Expected <" + actual + "> to contain <" + expected + ">",
                actual.contains(expected));
    }

}