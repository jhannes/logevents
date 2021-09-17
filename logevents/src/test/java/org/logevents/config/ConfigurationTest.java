package org.logevents.config;

import junit.framework.TestCase;
import org.junit.Test;
import org.logevents.formatting.MdcFilter;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ConfigurationTest {
    private final Properties properties = new Properties();
    private final String prefix = "observer.foo";
    private final Configuration configuration = new Configuration(properties, prefix);

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
        assertConfigurationErrorContains(() -> ConfigUtil.create("observer.foo.thread", "org.example", Optional.empty(), properties),
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

        assertConfigurationError(configuration::checkForUnknownFields,
                "Unknown configuration options: [also, unknown] for observer.foo. Expected options: [amount, name, value]");
    }
    
    @Test
    public void shouldAcceptUnusedEmptyProperties() {
        properties.put("observer.foo.name", "");
        configuration.checkForUnknownFields();
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

    @Test
    public void shouldDetermineJarName() {
        Properties properties = new Properties();

        assertEquals("junit", Configuration.determineJarName(TestCase.class.getName()));
        assertEquals(currentWorkingDirectory(), Configuration.determineJarName(String.class.getName()));
        assertEquals(currentWorkingDirectory(), Configuration.determineJarName(getClass().getName()));
    }

    @Test
    public void shouldCalculateApplicationNameFromJarName() {
        String prefix = "/usr/local/apps/myApp/";
        assertEquals("my-little-app", Configuration.toApplicationName(prefix + "my-little-app.jar"));
        assertEquals("random-app", Configuration.toApplicationName(prefix + "random-app-1.2.jar"));
        assertEquals("random-app", Configuration.toApplicationName(prefix + "random-app-1.2.11.jar"));
        assertEquals("logevents-demo", Configuration.toApplicationName(prefix + "logevents-demo-1.2.11-SNAPSHOT.jar"));
        assertEquals("logevents-demo", Configuration.toApplicationName(prefix + "logevents-demo-1.2.11-alfa2.jar"));
    }

    @Test
    public void shouldCalculatePackageFilter() {
        properties.put("observer.test.packageFilter", "com.sun,javax.servlet  , com.foo");
        properties.put("observer.*.packageFilter", "junit");

        assertEquals(Arrays.asList("com.sun", "javax.servlet", "com.foo"),
                new Configuration(properties, "observer.test").getPackageFilter());
        assertEquals(Arrays.asList("junit"),
                new Configuration(properties, "observer.random").getPackageFilter());
        assertEquals(Arrays.asList(Configuration.DEFAULT_PACKAGE_FILTER),
                new Configuration(new Properties(), "observer.test").getPackageFilter());
    }

    @Test
    public void shouldCalculateMdcFilter() {
        properties.put("observer.test.includedMdcKeys", "username, operation");

        MdcFilter mdcFilterForObserver = new Configuration(properties, "observer.test").getMdcFilter();
        assertTrue(mdcFilterForObserver.isKeyIncluded("username"));
        assertFalse(mdcFilterForObserver.isKeyIncluded("anyOtherKey"));
    }

    @Test
    public void shouldCalculcateEmptyMdcFilter() {
        properties.put("observer.other.includedMdcKeys", "");
        MdcFilter mdcFilterWithNone = new Configuration(properties, "observer.other").getMdcFilter();
        assertFalse(mdcFilterWithNone.isKeyIncluded("anyKey"));
    }

    @Test
    public void shouldCalculcateMdcFilterWithExclusions() {
        properties.put("observer.other.excludedMdcKeys", "userName");
        MdcFilter mdcFilterWithNone = new Configuration(properties, "observer.other").getMdcFilter();
        assertFalse(mdcFilterWithNone.isKeyIncluded("userName"));
        assertTrue(mdcFilterWithNone.isKeyIncluded("random"));
    }

    @Test
    public void shouldUseDefaultMdcFilter() {
        properties.put("observer.*.includedMdcKeys", "ipAddress");
        MdcFilter mdcFilterWithDefault = new Configuration(properties, "observer.random").getMdcFilter();
        assertTrue(mdcFilterWithDefault.isKeyIncluded("ipAddress"));
        assertFalse(mdcFilterWithDefault.isKeyIncluded("anyKey"));
    }

    @Test
    public void shouldUseDefaultMdcFilterForEnvironment() {
        HashMap<String, String> environment = new HashMap<>();
        environment.put("LOGEVENTS_INCLUDEDMDCKEYS", "ipAddress");
        MdcFilter mdcFilterWithDefault = new Configuration(new Properties(), "observer.random", environment).getMdcFilter();
        assertTrue(mdcFilterWithDefault.isKeyIncluded("ipAddress"));
    }

    @Test
    public void shouldConfigureApplicationNameEnvironment() {
        HashMap<String, String> environment = new HashMap<>();
        String appName = "my-application";
        environment.put("LOGEVENTS_APPLICATIONNAME", appName);
        Configuration configuration = new Configuration(new Properties(), "observer.buffer", environment);
        assertEquals(configuration.getApplicationName(), appName);
    }

    private static String currentWorkingDirectory() {
        return Paths.get("").toAbsolutePath().getFileName().toString();
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
