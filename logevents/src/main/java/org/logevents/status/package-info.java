/**
 * Reporting of internal events such as configuration loading and errors from
 * logevents. {@link org.logevents.status.StatusEvent}s are collected in
 * {@link org.logevents.status.LogEventStatus} and can be retrieved with
 * {@link org.logevents.status.LogEventStatus#getMessages}
 * (which stores the first 1000 messages) and
 * {@link org.logevents.status.LogEventStatus#lastMessage}.
 * In order to debug LogEvents, either put <code>status=[DEBUG|TRACE|CONFIG]</code>
 * in your <code>logevents.properties</code> or start your Java-process
 * with <code>-Dlogevents.status=[DEBUG|TRACE|CONFIG]</code>
 */
package org.logevents.status;
