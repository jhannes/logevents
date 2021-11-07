package org.logevents.observers.batch;

import org.junit.Test;
import org.logevents.extend.junit.LogEventSampler;
import org.slf4j.event.Level;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertTrue;

public class MicrosoftTeamsAlertOnlyFormatterTest {

    @Test
    public void shouldCreateMessageWithUnformattedMessage() {
        Map<String, String> properties = new HashMap<>();
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
        assertContains("\uD83D\uDED1 @channel Message {} with argument {}", message.get("text").toString());
        assertContains(detailsUrl, message.get("text").toString());
    }

    private void assertContains(String expected, String actual) {
        assertTrue("Expected to find <" + expected + "> in <" + actual + ">",
                actual.contains(expected));
    }
}
