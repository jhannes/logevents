package org.logevents;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Properties;

import org.junit.Test;
import org.slf4j.event.Level;

public class DefaultLogEventConfiguratorTest {

    private LogEventFactory factory = new LogEventFactory();
    private DefaultLogEventConfigurator configurator = new DefaultLogEventConfigurator();
    private Properties properties = new Properties();

    @Test
    public void shouldSetRootLevelFromProperties() {
        properties.setProperty("root", "TRACE");
        String oldObserver = factory.getRootLogger().getObserver();

        configurator.configure(factory, properties);

        assertTrue(factory.getLoggers() + " should be empty", factory.getLoggers().isEmpty());
        assertEquals(Level.TRACE, factory.getRootLogger().getLevelThreshold());
        assertEquals(oldObserver, factory.getRootLogger().getObserver());
    }

    @Test
    public void shouldSetRootObserverFromProperties() {
        properties.setProperty("root", "DEBUG file");
        properties.setProperty("observer.file", "DateRollingLogEventObserver");
        properties.setProperty("observer.file.filename", "logs/application.log");

        configurator.configure(factory, properties);
        assertEquals(Level.DEBUG, factory.getRootLogger().getLevelThreshold());
        assertEquals(
                "DateRollingLogEventObserver{"
                + "destination=DateRollingFileDestination{application.log},"
                + "formatter=TTLLEventLogFormatter}",
                factory.getRootLogger().getObserver());
    }

    @Test
    public void shouldSetLoggerObserverFromProperties() {
        properties.setProperty("logger.org.example", "ERROR buffer1,buffer2");
        properties.setProperty("observer.buffer1", "CircularBufferLogEventObserver");
        properties.setProperty("observer.buffer2", "CircularBufferLogEventObserver");

        configurator.configure(factory, properties);
        assertEquals(Level.ERROR, factory.getLogger("org.example").getLevelThreshold());
        assertEquals(
                "CompositeLogEventObserver{"
                + "[CircularBufferLogEventObserver{size=0}, CircularBufferLogEventObserver{size=0}]}",
                factory.getLogger("org.example").getObserver());
    }

    // TODO: Test that file without directory is handled okay


}
