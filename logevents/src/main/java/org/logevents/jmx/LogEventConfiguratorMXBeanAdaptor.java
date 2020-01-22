package org.logevents.jmx;

import org.logevents.config.DefaultLogEventConfigurator;

import java.util.List;
import java.util.stream.Collectors;

public class LogEventConfiguratorMXBeanAdaptor implements LogEventConfiguratorMXBean {
    private final DefaultLogEventConfigurator configurator;

    public LogEventConfiguratorMXBeanAdaptor(DefaultLogEventConfigurator configurator) {
        this.configurator = configurator;
    }

    @Override
    public List<String> getConfigurationSources() {
        return configurator.getConfigurationSources();
    }

    @Override
    public List<String> getConfigurationValues() {
        return configurator.loadConfigurationProperties().entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .sorted()
                .collect(Collectors.toList());
    }
}
