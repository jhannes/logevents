package org.logevents.observers;

import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.extend.servlets.LogEventSampler;
import org.logevents.observers.batch.LogEventBatch;
import org.slf4j.event.Level;

import java.awt.*;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class SystemTrayLogEventObserverTest {

    private final SystemTrayLogEventObserver systemTrayObserver = new SystemTrayLogEventObserver(new Properties(), "systray");

    @Test
    public void shouldAbbreviateLongCaption() {
        LogEvent event = new LogEventSampler().withFormat("A very long message with more than 30 characters").build();
        assertEquals("A very long message with more th...", systemTrayObserver.getCaption(new LogEventBatch().add(event)));
    }

    @Test
    public void shouldKeepShortMessage() {
        LogEvent event = new LogEventSampler().withFormat("A message with about 30 chars").build();
        assertEquals("A message with about 30 chars", systemTrayObserver.getCaption(new LogEventBatch().add(event)));
    }

    @Test
    public void shouldShowRepetitions() {
        LogEvent event = new LogEventSampler().withFormat("A message with about 30 chars").build();
        assertEquals("A message with about 3... (2 repetitions)",
                systemTrayObserver.getCaption(new LogEventBatch().add(event).add(event)));
    }

    @Test
    public void shouldShowBatch() {
        assertEquals("2 messages", systemTrayObserver.getCaption(new LogEventBatch()
                        .add(new LogEventSampler().build()).add(new LogEventSampler().build())));
    }

    @Test
    public void shouldShowTextWithoutException() {
        LogEvent event = new LogEventSampler().withFormat("An important message").withLevel(Level.WARN)
                .withLoggerName("org.example.FooTestCase").build();
        assertEquals("⚠️ An important message [o.e.FooTestCase]", systemTrayObserver.getText(new LogEventBatch().add(event)));
    }

    @Test
    public void shouldShowTextWithException() {
        LogEvent event = new LogEventSampler().withFormat("An important message")
                .withLevel(Level.ERROR)
                .withThrowable(new RuntimeException("An error occurred"))
                .withLoggerName("org.example.FooTestCase").build();
        assertEquals(
                "\uD83D\uDED1 An error occurred (java.lang.RuntimeException)\nAn important message [o.e.FooTestCase]",
                systemTrayObserver.getText(new LogEventBatch().add(event))
        );
    }

    @Test
    public void shouldReturnIconForLevel() {
        assertEquals(TrayIcon.MessageType.ERROR, systemTrayObserver.getMessageType(Level.ERROR));
        assertEquals(TrayIcon.MessageType.WARNING, systemTrayObserver.getMessageType(Level.WARN));
        assertEquals(TrayIcon.MessageType.INFO, systemTrayObserver.getMessageType(Level.DEBUG));
    }
}