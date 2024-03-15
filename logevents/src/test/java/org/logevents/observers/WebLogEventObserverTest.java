package org.logevents.observers;

import org.junit.Rule;
import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.optional.junit.LogEventSampler;
import org.logevents.optional.junit.LogEventStatusRule;
import org.logevents.status.StatusEvent;
import org.logevents.util.JsonParser;
import org.logevents.util.JsonUtil;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class WebLogEventObserverTest {

    @Rule
    public LogEventStatusRule logEventStatusRule = new LogEventStatusRule(StatusEvent.StatusLevel.ERROR);

    @Test
    public void shouldFetchLogEvents() throws IOException {
        LogEventBuffer.clear();
        Map<String, String> properties = new HashMap<>();
        properties.put("observer.web.httpPort", "0");
        properties.put("observer.web.openIdIssuer", "https://accounts.google.com");
        properties.put("observer.web.clientId", "dummy");
        properties.put("observer.web.clientSecret", "dummy");
        properties.put("observer.web.requiredClaim.email", "my@example.com");

        WebLogEventObserver observer = new WebLogEventObserver(properties, "observer.web");
        LogEvent logEvent = new LogEventSampler().build();
        observer.logEvent(logEvent);

        HttpURLConnection connection = (HttpURLConnection) new URL(observer.getServerUrl() + "/events").openConnection();
        connection.setRequestProperty("Cookie", observer.createSessionCookie("johannes"));

        Map<String, Object> objects = JsonParser.parseObject(connection);
        String loggedMessage = JsonUtil.getObjectList(objects, "events").get(0).get("messageTemplate").toString();
        assertEquals(logEvent.getMessage(), loggedMessage);
    }

    @Test
    public void shouldRejectFakeSessionCookie() throws Exception {
        Map<String, String> properties = new HashMap<>();
        properties.put("observer.web.httpPort", "0");
        properties.put("observer.web.openIdIssuer", "https://accounts.google.com");
        properties.put("observer.web.clientId", "dummy");
        properties.put("observer.web.clientSecret", "dummy");
        properties.put("observer.web.requiredClaim.email", "my@example.com");

        WebLogEventObserver observer = new WebLogEventObserver(properties, "observer.web");
        LogEvent logEvent = new LogEventSampler().build();
        observer.logEvent(logEvent);

        HttpURLConnection connection = (HttpURLConnection) new URL(observer.getServerUrl() + "/events").openConnection();
        connection.setRequestProperty("Cookie", "logevents.session=dsgse93922");

        assertEquals(401, connection.getResponseCode());
    }
}
