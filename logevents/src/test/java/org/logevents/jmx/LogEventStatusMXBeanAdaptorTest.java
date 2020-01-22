package org.logevents.jmx;

import org.junit.Before;
import org.junit.Test;
import org.logevents.status.LogEventStatus;
import org.logevents.status.StatusEvent;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class LogEventStatusMXBeanAdaptorTest {

    private LogEventStatus status = new LogEventStatus();
    private LogEventStatusMXBeanAdaptor mBean = new LogEventStatusMXBeanAdaptor(status);

    @Before
    public void setUp() {
        status.setThreshold(StatusEvent.StatusLevel.NONE);
    }

    @Test
    public void shouldShowInfoMessages() {
        status.addError(this, "Something went wrong", new IOException());
        status.addInfo(this, "Something important");
        status.addDebug(this, "Not so important");

        List<String> expected = Arrays.asList(
                "ERROR LogEventStatusMXBeanAdaptorTest: Something went wrong java.io.IOException",
                "INFO LogEventStatusMXBeanAdaptorTest: Something important"
        );
        assertEquals(expected, removeTimestamp(mBean.getInfoMessages()));
    }

    @Test
    public void shouldShowErrorMessages() {
        status.addError(this, "Something went wrong", new IOException());
        status.addInfo(this, "Something important");

        List<String> expected = Arrays.asList("ERROR LogEventStatusMXBeanAdaptorTest: Something went wrong java.io.IOException");
        assertEquals(expected, removeTimestamp(mBean.getErrorMessages()));
    }

    @Test
    public void shouldShowHeadMessage() {
        status.addError(this, "Something went wrong", new IOException());
        status.addInfo(this, "Something important");
        status.addDebug(this, "Not so important");

        List<String> expected = Arrays.asList(
                "ERROR LogEventStatusMXBeanAdaptorTest: Something went wrong java.io.IOException",
                "INFO LogEventStatusMXBeanAdaptorTest: Something important",
                "DEBUG LogEventStatusMXBeanAdaptorTest: Not so important"
        );
        assertEquals(expected, removeTimestamp(mBean.getHeadMessages()));
    }

    @Test
    public void shouldShowCategories() {
        status.addInfo(this, "Something interesting");
        status.addInfo(status, "Something interesting");

        assertEquals(new HashSet<>(Arrays.asList("LogEventStatusMXBeanAdaptorTest", "LogEventStatus")), mBean.getCategories());
    }

    @Test
    public void shouldFilterByCategoryAndLevel() {
        status.addInfo(this, "Something interesting");
        status.addDebug(this, "Something boring");
        status.addInfo(status, "Something irrelevant");

        List<String> expected = Arrays.asList("INFO LogEventStatusMXBeanAdaptorTest: Something interesting");
        assertEquals(expected, removeTimestamp(mBean.getMessages(this.getClass().getSimpleName(), StatusEvent.StatusLevel.INFO)));
    }

    @Test
    public void shouldFilterByLevel() {
        status.addDebug(this, "Something boring");
        status.addDebug(status, "Something irrelevant");

        List<String> expected = Arrays.asList("DEBUG LogEventStatusMXBeanAdaptorTest: Something boring");
        assertEquals(expected, removeTimestamp(mBean.getMessages(this.getClass().getSimpleName())));
    }

    private List<String> removeTimestamp(List<String> messages) {
        return messages.stream().map(this::removeTimestamp).collect(Collectors.toList());
    }

    private String removeTimestamp(String s) {
        return s.replaceFirst("([A-Z]+) ([0-9:.]+) (.+) \\[.*", "$1 $3");
    }
}
