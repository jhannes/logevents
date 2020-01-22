package org.logevents.jmx;

import org.logevents.status.StatusEvent;

import java.util.List;
import java.util.Set;

public interface LogEventStatusMXBean {
    List<String> getHeadMessages();

    Set<String> getCategories();

    List<String> getErrorMessages();

    List<String> getInfoMessages();

    List<String> getMessages(String target);

    List<String> getMessages(String target, StatusEvent.StatusLevel level);
}
