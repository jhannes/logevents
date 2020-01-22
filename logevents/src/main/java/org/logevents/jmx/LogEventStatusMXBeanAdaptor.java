package org.logevents.jmx;

import org.logevents.status.LogEventStatus;
import org.logevents.status.StatusEvent;

import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class LogEventStatusMXBeanAdaptor implements LogEventStatusMXBean {
    private final LogEventStatus status;

    public LogEventStatusMXBeanAdaptor(LogEventStatus status) {
        this.status = status;
    }

    @Override
    public List<String> getHeadMessages() {
        return status.getHeadMessages().stream().map(this::format).collect(Collectors.toList());
    }

    @Override
    public Set<String> getCategories() {
        return status.getAllMessages().stream().map(StatusEvent::getLocation).map(Object::getClass).map(Class::getSimpleName).collect(Collectors.toSet());
    }

    @Override
    public List<String> getErrorMessages() {
        return getMessages(StatusEvent.StatusLevel.ERROR);
    }

    @Override
    public List<String> getInfoMessages() {
        return getMessages(StatusEvent.StatusLevel.INFO);
    }

    private List<String> getMessages(StatusEvent.StatusLevel threshold) {
        return status.getAllMessages().stream()
                .filter(m -> m.getLevel().toInt() >= threshold.toInt())
                .map(this::format)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getMessages(String target) {
        return status.getAllMessages().stream()
                .filter(m -> m.getLocation().getClass().getSimpleName().equals(target))
                .map(this::format)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getMessages(String target, StatusEvent.StatusLevel level) {
        return status.getAllMessages().stream()
                .filter(m -> m.getLocation().getClass().getSimpleName().equals(target))
                .filter(m -> m.getLevel().toInt() >= level.toInt())
                .map(this::format)
                .collect(Collectors.toList());
    }

    private String format(StatusEvent statusEvent) {
        return  statusEvent.getLevel() + " " +
                statusEvent.getTime().atZone(ZoneId.systemDefault()).toLocalTime() + " " +
                statusEvent.formatMessage();
    }
}
