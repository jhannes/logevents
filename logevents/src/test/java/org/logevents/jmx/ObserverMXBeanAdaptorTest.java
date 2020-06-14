package org.logevents.jmx;

import org.junit.Test;
import org.logevents.LogEventFactory;
import org.logevents.LogEventObserver;
import org.logevents.observers.ConsoleLogEventObserver;

import java.util.HashMap;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ObserverMXBeanAdaptorTest {

    private LogEventFactory factory = new LogEventFactory();
    private HashMap<String, Supplier<? extends LogEventObserver>> observerSuppliers = new HashMap<>();

    @Test
    public void shouldShowCreatedObservers() {
        ConsoleLogEventObserver observer = new ConsoleLogEventObserver();
        observerSuppliers.put("console", () -> observer);
        factory.setObservers(observerSuppliers);
        factory.getObserver("console");

        ObserverMXBeanAdaptor mBean = new ObserverMXBeanAdaptor(factory, "console");
        assertTrue(mBean.isCreated());
        assertEquals(observer.toString(), mBean.getContent());
        assertEquals(observer.getThreshold().toString(), mBean.getThreshold());
    }

    @Test
    public void shouldNotShowPotentialObservers() {
        observerSuppliers.put("console", ConsoleLogEventObserver::new);
        factory.setObservers(observerSuppliers);

        ObserverMXBeanAdaptor mBean = new ObserverMXBeanAdaptor(factory, "console");
        assertFalse(mBean.isCreated());
        assertNull(mBean.getContent());
    }
}
