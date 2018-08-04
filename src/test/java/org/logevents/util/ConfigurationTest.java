package org.logevents.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Properties;

import org.junit.Test;

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
                "Can't create observer.foo.thread", "can not access a member of class", "with modifiers \"private\"", "PrivateClass");
    }

    public static class ClassWithoutValidConstructor extends Thread {
        public ClassWithoutValidConstructor(@SuppressWarnings("unused") String name) {
        }
    }

    @Test
    public void shouldGiveGoodErrorMessageOnClassWithoutConstructor() {
        properties.put("observer.foo.thread", ClassWithoutValidConstructor.class.getName());
        assertConfigurationErrorContains(() -> configuration.createInstance("thread", Thread.class),
                "Can't create observer.foo.thread", "InstantiationException");
    }

    public static class ClassWhichThrowsExceptionInConstructor extends Thread {
        public ClassWhichThrowsExceptionInConstructor() {
            throw new IllegalArgumentException("A custom error message");
        }
    }

    @Test
    public void shouldGiveGoodErrorMessageOnExceptionInConstuctor() {
        properties.put("observer.foo.thread", ClassWhichThrowsExceptionInConstructor.class.getName());
        assertConfigurationErrorContains(() -> configuration.createInstance("thread", Thread.class),
                "Exception when creating observer.foo.thread", "A custom error message");
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

    private void assertConfigurationError(Runnable r, String expected) {
        try {
            r.run();
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals(expected, e.getMessage());
        }
    }

    private void assertConfigurationErrorContains(Runnable r, String... expecteds) {
        try {
            r.run();
            fail();
        } catch (IllegalArgumentException e) {
            for (String expected : expecteds) {
                assertTrue("expected to find <" + expected + "> in string " + e.getMessage(),
                        e.getMessage().contains(expected));
            }
        }
    }
}
