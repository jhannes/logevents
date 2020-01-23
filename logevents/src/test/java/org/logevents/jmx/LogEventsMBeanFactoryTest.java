package org.logevents.jmx;

import org.junit.Test;
import org.logevents.LogEventFactory;
import org.logevents.LogEventObserver;
import org.logevents.config.Configuration;
import org.logevents.config.DefaultLogEventConfigurator;
import org.logevents.observers.CircularBufferLogEventObserver;
import org.slf4j.event.Level;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LogEventsMBeanFactoryTest {

    private MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
    private LogEventFactory factory = new LogEventFactory();
    private LogEventsMBeanFactory logEventsMBeanFactory = new LogEventsMBeanFactory();
    private Properties properties = new Properties();
    private Configuration config = new Configuration(properties, "logevents");

    @Test
    public void shouldRegisterConfiguredLoggersAsMBeans() throws MalformedObjectNameException {
        factory.setLevel(factory.getLogger("org.logevents"), Level.WARN);
        factory.setLevel(factory.getLogger("com.example"), Level.DEBUG);
        factory.setObserver("com.example.foo", new CircularBufferLogEventObserver());
        factory.getLogger("com.example.foo.bar");

        properties.put("logevents.jmx", "true");
        logEventsMBeanFactory.setup(factory, new DefaultLogEventConfigurator(null), config);

        Set<ObjectInstance> mBeans = mbeanServer.queryMBeans(new ObjectName("org.logevents:type=Logger,name=*"), null);

        assertEquals(new HashSet<>(Arrays.asList("ROOT", "org.logevents", "com.example", "com.example.foo")),
                mBeans.stream().map(o -> o.getObjectName().getKeyProperty("name")).collect(Collectors.toSet()));

        Set<String> classNames = new HashSet<>();
        for (ObjectInstance mBean : mBeans) {
            classNames.add(mBean.getClassName());
        }
        assertEquals(new HashSet<>(Arrays.asList(LoggerMXBeanAdaptor.class.getName())), classNames);
    }

    @Test
    public void shouldRegisterObservers() throws MalformedObjectNameException {
        HashMap<String, Supplier<? extends LogEventObserver>> observerSuppliers = new HashMap<>();
        observerSuppliers.put("observer1", CircularBufferLogEventObserver::new);
        observerSuppliers.put("observer2", CircularBufferLogEventObserver::new);
        factory.setObservers(observerSuppliers);

        properties.put("logevents.jmx", "true");
        logEventsMBeanFactory.setup(factory, new DefaultLogEventConfigurator(null), config);

        Set<ObjectInstance> mBeans = mbeanServer.queryMBeans(new ObjectName("org.logevents:type=Observer,name=*"), null);

        assertEquals(new HashSet<>(Arrays.asList("observer1", "observer2")),
                mBeans.stream().map(o -> o.getObjectName().getKeyProperty("name")).collect(Collectors.toSet()));
    }

    @Test
    public void shouldUnregisterMBeans() throws MalformedObjectNameException {
        properties.put("logevents.jmx", "true");
        logEventsMBeanFactory.setup(factory, new DefaultLogEventConfigurator(null), config);
        assertFalse(mbeanServer.queryMBeans(new ObjectName("org.logevents:*"), null).isEmpty());

        properties.remove("logevents.jmx");
        logEventsMBeanFactory.setup(factory, new DefaultLogEventConfigurator(null), config);
        assertTrue(mbeanServer.queryMBeans(new ObjectName("org.logevents:*"), null).isEmpty());
    }
}