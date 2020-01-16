package org.logevents;

import org.logevents.config.DefaultLogEventConfigurator;

import java.util.ServiceLoader;

/**
 * Implement this interface and specify your implementation class
 * in <code>META-INF/services/org.logevents.LogEventConfigurator</code> to
 * automatically load you own configuration with {@link ServiceLoader}
 * at startup. Your implementation may want to inherit from
 * {@link DefaultLogEventConfigurator}
 *
 * @author Johannes Brodwall
 */
public interface LogEventConfigurator {

    void configure(LogEventFactory factory);

}
