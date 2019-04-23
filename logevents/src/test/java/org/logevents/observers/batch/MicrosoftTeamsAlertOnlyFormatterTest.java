package org.logevents.observers.batch;

import org.junit.Test;
import org.logevents.extend.servlets.LogEventSampler;
import org.slf4j.event.Level;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.logevents.util.JsonUtil.getObjectList;

public class MicrosoftTeamsAlertOnlyFormatterTest {

    @Test
    @SuppressWarnings("unchecked")
    public void shouldCreateMessageWithUnformattedMessage() {
        Properties properties = new Properties();
        String detailsUrl = "http://localhost/foo/bar";
        properties.put("observer.teams.formatter.detailUrl", detailsUrl);
        MicrosoftTeamsAlertOnlyFormatter formatter = new MicrosoftTeamsAlertOnlyFormatter(
                properties,
                "observer.teams.formatter"
        );

        Map<String, Object> message = formatter.createMessage(new LogEventBatch()
                .add(new LogEventSampler()
                        .withFormat("Message {} with argument {}")
                        .withArgs("one", "two")
                        .withLevel(Level.ERROR)
                        .build()
                ));
        assertEquals("\uD83D\uDED1 Message {} with argument {}", message.get("title"));
        List<Map<String, Object>> potentialAction = (List<Map<String, Object>>)
                getObjectList(message, "sections").get(0).get("potentialAction");
        assertEquals("OpenUri", potentialAction.get(0).get("@type"));
        String targetUri = getObjectList(potentialAction.get(0), "targets").get(0).get("uri").toString();
        assertTrue(targetUri + " should contain " + detailsUrl,
                targetUri.startsWith(detailsUrl));
    }
}