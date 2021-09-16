package org.logevents.jmx;

import org.logevents.LogEventFactory;
import org.logevents.LoggerConfiguration;
import org.logevents.config.Configuration;
import org.logevents.config.DefaultLogEventConfigurator;
import org.logevents.observers.StatisticsLogEventsObserver;
import org.logevents.status.LogEventStatus;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.Hashtable;
import java.util.Map;

public class LogEventsMBeanFactory {
    private MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
    public static final String JMX_DOMAIN = "org.logevents";

    public void setup(LogEventFactory factory, DefaultLogEventConfigurator configurator, Configuration config) {
        try {

            for (ObjectInstance mbean : mbeanServer.queryMBeans(new ObjectName(JMX_DOMAIN + ":*"), null)) {
                mbeanServer.unregisterMBean(mbean.getObjectName());
            }

            if (config.getBoolean("jmx")) {
                mbeanServer.registerMBean(new LogEventFactoryMXBeanAdaptor(factory), new ObjectName(JMX_DOMAIN, "name", config.optionalString("logevents.jmx.name").orElse("LogEventFactory")));
                mbeanServer.registerMBean(new LogEventConfiguratorMXBeanAdaptor(configurator), new ObjectName(JMX_DOMAIN, "name", this.getClass().getSimpleName()));
                mbeanServer.registerMBean(new LogEventStatusMXBeanAdaptor(LogEventStatus.getInstance()), new ObjectName(JMX_DOMAIN, "name", "LogEventStatus"));

                Hashtable<String, String> table = new Hashtable<>();
                table.put("type", "Logger");
                table.put("name", Logger.ROOT_LOGGER_NAME);

                mbeanServer.registerMBean(new LoggerMXBeanAdaptor(factory, factory.getRootLogger()), new ObjectName(JMX_DOMAIN, table));

                for (Map.Entry<String, LoggerConfiguration> logger : factory.getLoggers().entrySet()) {
                    if (logger.getValue().isConfigured()) {
                        table.put("name", logger.getKey());
                        mbeanServer.registerMBean(new LoggerMXBeanAdaptor(factory, logger.getValue()), new ObjectName(JMX_DOMAIN, table));
                    }
                }

                table.put("type", "Observer");
                for (String observerName : factory.getObserverNames()) {
                    table.put("name", observerName);
                    mbeanServer.registerMBean(new ObserverMXBeanAdaptor(factory, observerName), new ObjectName(JMX_DOMAIN, table));
                }

                if (isStatisticsInstalled(factory)) {
                    table.put("type", "Statistics");
                    for (Level level : Level.values()) {
                        table.put("name", level.toString());
                        mbeanServer.registerMBean(new StatisticsMXBeanAdaptor(level), new ObjectName(JMX_DOMAIN, table));
                    }
                }
            }
        } catch (JMException e) {
            LogEventStatus.getInstance().addError(this, "Failed to register MBean", e);
        }
    }

    private boolean isStatisticsInstalled(LogEventFactory factory) {
        return factory.getRootLogger().getInfoObservers().stream().anyMatch(o -> o instanceof StatisticsLogEventsObserver);
    }
}
