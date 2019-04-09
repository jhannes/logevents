package org.logevents.observers.batch;


import org.junit.Test;
import org.logevents.extend.servlets.LogEventSampler;
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
        batch.add(new LogEventSampler().build());
        batch.add(new LogEventSampler().build());
        batch.add(new LogEventSampler().withLevel(Level.ERROR).build());
        batch.add(new LogEventSampler().build());

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