package org.logevents;

import java.util.ServiceLoader;

/**
 * Implement this interface and specify your implementation class
 * in <tt>META-INF/services/org.logevents.LogEventConfigurator</tt> to
 * automatically load you own configuration with {@link ServiceLoader}
 * at startup. Your implementation may want to inherit from
 * {@link DefaultLogEventConfigurator}
 *
 * @author Johannes Brodwall
 */
public interface LogEventConfigurator {

    void configure(LogEventFactory factory);

}
