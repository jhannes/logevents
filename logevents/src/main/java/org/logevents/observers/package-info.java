/**
 * <p>Standard observers included in LogEvents that can be created in the configuration
 * file with <code>observer.myobs=ConsoleLogEventObserver</code> (for example).
 * Observers outside this package must be created with full package name
 * (e.g. <code>observer.myobs=org.logevents.observers.ConsoleLogEventObserver</code>)</p>
 *
 * <p>The most important observers to be aware of are
 * {@link org.logevents.observers.ConsoleLogEventObserver},
 * {@link org.logevents.observers.FileLogEventObserver},
 * {@link org.logevents.observers.DatabaseLogEventObserver} as well as
 * {@link org.logevents.observers.AbstractBatchingLogEventObserver}, which is
 * the superclass of {@link org.logevents.observers.MicrosoftTeamsLogEventObserver},
 * {@link org.logevents.observers.SmtpLogEventObserver} and {@link org.logevents.observers.SlackLogEventObserver}</p>
 */
package org.logevents.observers;

