package org.logevents;

import org.logevents.config.DefaultLogEventConfigurator;
import org.logevents.core.LogEventFilter;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.util.Map;
import java.util.ServiceLoader;

/**
 * Implement this interface and specify your implementation class
 * in <code>META-INF/services/org.logevents.LogEventConfigurator</code> to
 * automatically load you own configuration with {@link ServiceLoader}
 * at startup. Your implementation may want to inherit from
 * {@link DefaultLogEventConfigurator}.
 *
 * <h2>How to implement your own configuration</h2>
 *
 * <ol>
 *     <li>At any point in your code, you can access get {@link LogEventFactory#getInstance()} and
 *     call {@link LogEventFactory#addObserver(Logger, LogEventObserver)} to add an observer
 *     that you created programmatically to a logger or {@link LogEventFactory#setFilter(Logger, LogEventFilter)}
 *     or {@link LogEventFactory#setLevel(Logger, Level)} to adjust what is being logged</li>
 *     <li>You can add your own subclass of DefaultLogEventConfigurator with a service loader
 *     and override {@link DefaultLogEventConfigurator#createGlobalObservers(LogEventFactory, Map, Map)}
 *     to programmatically add observers to the root logger</li>
 *     <li>You can override {@link DefaultLogEventConfigurator#createObservers(Map, Map, Map)} to
 *     create named observers that you can refer from <code>logger.org.example=...</code>
 *     configuration in logevents.properties</li>
 *     <li>You can also override {@link DefaultLogEventConfigurator#configureLoggers(LogEventFactory, Map, Map)}
 *     to programmatically modify which observers and filters are applied to loggers</li>
 *     <li>You can override {@link DefaultLogEventConfigurator#loadConfigurationProperties()} to
 *     add configuration properties from your own sources</li>
 *     <li>You can fully implement LogEventConfigurator to take full control of your
 *     configuration</li>
 * </ol>
 *
 * @author Johannes Brodwall
 */
public interface LogEventConfigurator {

    void configure(LogEventFactory factory);

}
