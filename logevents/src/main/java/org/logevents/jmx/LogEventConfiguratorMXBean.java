package org.logevents.jmx;

import javax.management.openmbean.OpenDataException;
import java.util.List;

public interface LogEventConfiguratorMXBean {

    List<String> getConfigurationSources();

    List<String> getConfigurationValues() throws OpenDataException;

}
