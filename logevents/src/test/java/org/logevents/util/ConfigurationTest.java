package org.logevents.util;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ConfigurationTest {
    private Properties properties = new Properties();
    private String prefix = "observer.foo";
    private Configuration configuration = new Configuration(properties, prefix);

    @Test
    public void shouldGiveGoodErrorForMalformedUrl() {
        properties.put("observer.foo.url", "htt://invalid.url");
        assertConfigurationError(() -> configuration.getUrl("url"),
                "observer.foo.url value htt://invalid.url: unknown protocol: htt");
    }

    @Test
    public void shouldGiveGoodErrorForMalformedDuration() {
        properties.put("observer.foo.timeout", "5 minutes");
        assertConfigurationError(() -> configuration.getDuration("timeout"),
                "observer.foo.timeout value 5 minutes: Text cannot be parsed to a Duration");
    }

    @Test
    public void shouldGiveGoodErrorMissingRequiredString() {
        properties.put("observer.foo.baz", "something else");
        assertConfigurationError(() -> configuration.getString("bar"),
                "Missing required key <observer.foo.bar> in <[observer.foo.baz]>");
    }

    private static class PrivateClass extends Thread {

    }

    @Test
    public void shouldGiveGoodErrorMessageOnPrivateClass() {
        properties.put("observer.foo.thread", PrivateClass.class.getName());
        assertConfigurationErrorContains(() -> configuration.createInstance("thread", Thread.class),
                "Can't create observer.foo.thread", "NoSuchMethodException", "PrivateClass");
    }

    public static class ClassWithoutValidConstructor extends Thread {
        public ClassWithoutValidConstructor(@SuppressWarnings("unused") String name) {
        }
    }

    @Test
    public void shouldGiveGoodErrorMessageOnClassWithoutConstructor() {
        properties.put("observer.foo.thread", ClassWithoutValidConstructor.class.getName());
        assertConfigurationErrorContains(() -> configuration.createInstance("thread", Thread.class),
                "Can't create observer.foo.thread", "NoSuchMethodException");
    }

    public static class ClassWhichThrowsExceptionInConstructor extends Thread {
        public ClassWhichThrowsExceptionInConstructor() {
            throw new IllegalArgumentException("A custom error message");
        }
    }

    @Test
    public void shouldGiveGoodErrorMessageOnExceptionInConstructor() {
        properties.put("observer.foo.thread", ClassWhichThrowsExceptionInConstructor.class.getName());
        LogEventConfigurationException ex = assertConfigurationErrorContains(() -> configuration.createInstance("thread", Thread.class),
                "Exception when creating observer.foo.thread");
        assertTrue("expected to find <" + "A custom error message" + "> in string " + ex.getCause().getMessage(),
                ex.getCause().getMessage().contains("A custom error message"));
    }

    @Test
    public void shouldGiveGoodErrorMessageOnKnownClass() {
        properties.put("observer.foo.thread", "org.example.NonExistantClass");
        assertConfigurationErrorContains(() -> configuration.createInstance("thread", Thread.class),
                "Can't create observer.foo.thread=org.example.NonExistantClass", "ClassNotFoundException");
    }

    @Test
    public void shouldGiveGoodErrorMessageMissingConfig() {
        assertConfigurationErrorContains(() -> ConfigUtil.create("observer.foo.thread", "org.example", properties),
                "Missing configuration for class in observer.foo.thread");
    }

    @Test
    public void shouldWarnOnUnusedProperties() {
        properties.put("observer.foo.name", "test");
        properties.put("observer.foo.value", "test");
        properties.put("observer.foo.unknown", "test");
        properties.put("observer.foo.also.unknown", "test");

        configuration.getString("name");
        configuration.getStringList("value");
        configuration.optionalString("amount");

        assertConfigurationError(() -> configuration.checkForUnknownFields(),
                "Unknown configuration options: [also, unknown] for observer.foo. Expected options: [amount, name, value]");
    }

    @Test
    public void shouldListProperties() {
        properties.put("observer.test.markers.foo.name", "ignored");
        properties.put("observer.test.markers.foo.value", "ignored");
        properties.put("observer.test.markers.bar.name", "ignored");
        properties.put("observer.test.markers.baz", "ignored");
        Configuration configuration = new Configuration(properties, "observer.test");

        assertEquals(new HashSet<>(Arrays.asList("foo", "bar", "baz")),
                configuration.listProperties("markers"));

    }

    private void assertConfigurationError(Runnable r, String expected) {
        try {
            r.run();
            fail();
        } catch (LogEventConfigurationException e) {
            assertEquals(expected, e.getMessage());
        }
    }

    private LogEventConfigurationException assertConfigurationErrorContains(Runnable r, String... expecteds) {
        try {
            r.run();
            fail();
            return null;
        } catch (LogEventConfigurationException e) {
            for (String expected : expecteds) {
                assertTrue("expected to find <" + expected + "> in string " + e.getMessage(),
                        e.getMessage().contains(expected));
            }
            return e;
        }
    }
}
