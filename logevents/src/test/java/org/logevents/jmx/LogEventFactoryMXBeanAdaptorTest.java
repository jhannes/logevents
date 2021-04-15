package org.logevents.jmx;

import org.junit.Test;
import org.logevents.LogEventFactory;
import org.logevents.LogEventObserver;
import org.logevents.observers.CircularBufferLogEventObserver;
import org.logevents.observers.ConsoleLogEventObserver;
import org.slf4j.event.Level;

import java.util.Arrays;
import java.util.HashMap;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;

public class LogEventFactoryMXBeanAdaptorTest {

    private LogEventFactory factory = new LogEventFactory();
    private LogEventFactoryMXBeanAdaptor mBean = new LogEventFactoryMXBeanAdaptor(factory);

    @Test
    public void shouldShowConfiguredLoggers() {
        factory.setLevel(factory.getLogger("org.logevents"), Level.WARN);
        factory.setLevel(factory.getLogger("com.example"), Level.DEBUG);
        factory.setObserver("com.example.foo", new CircularBufferLogEventObserver());
        factory.getLogger("com.example.foo.bar");

        assertEquals(Arrays.asList("com.example", "com.example.foo", "org.logevents"),
                mBean.getConfiguredLoggers());
    }

    @Test
    public void shouldGetAllLoggers() {
        factory.setLevel(factory.getLogger("org.logevents"), Level.WARN);
        factory.setLevel(factory.getLogger("com.example"), Level.DEBUG);
        factory.setObserver("com.example.foo", new CircularBufferLogEventObserver());
        factory.getLogger("com.example.foo.bar");

        assertEquals(Arrays.asList("com", "com.example", "com.example.foo", "com.example.foo.bar", "org", "org.logevents"),
                mBean.getAllLoggers());
    }

    @Test
    public void shouldListAllObservers() {
        HashMap<String, Supplier<? extends LogEventObserver>> observerSuppliers = new HashMap<>();
        observerSuppliers.put("console", ConsoleLogEventObserver::new);
        observerSuppliers.put("observer1", CircularBufferLogEventObserver::new);
        factory.setObservers(observerSuppliers);

        assertEquals(Arrays.asList("console", "observer1"), mBean.getObservers());
    }

    @Test
    public void shouldShowEffectiveLevel() {
        factory.setLevel(factory.getRootLogger(), Level.ERROR);
        factory.setLevel(factory.getLogger("org.logevents"), Level.WARN);
        factory.setLevel(factory.getLogger("com.example"), Level.DEBUG);

        assertEquals("LevelThresholdFilter{ERROR}", mBean.getEffectiveFilter("org.neworg").toString());
        assertEquals("LevelThresholdFilter{WARN}", mBean.getEffectiveFilter("org.logevents").toString());
        assertEquals("LevelThresholdFilter{DEBUG}", mBean.getEffectiveFilter("com.example.something").toString());
    }

    @Test
    public void shouldShowObservers() {
        CircularBufferLogEventObserver observer = new CircularBufferLogEventObserver();
        factory.setObserver("com.example.foo", observer);
        assertEquals(observer.toString(), mBean.getObserver("com.example.foo.bar"));
    }
}
