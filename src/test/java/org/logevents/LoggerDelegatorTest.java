package org.logevents;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.logevents.observers.CircularBufferLogEventObserver;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.event.Level;

public class LoggerDelegatorTest {

    private LoggerDelegator loggerDelegator = new LogEventFactory.RootLoggerDelegator();
    private CircularBufferLogEventObserver observer = new CircularBufferLogEventObserver();

    private Marker marker = MarkerFactory.getMarker(UUID.randomUUID().toString());

    @Before
    public void setupLoggerDelegator() {
        loggerDelegator.setOwnObserver(observer, false);
        loggerDelegator.setLevelThreshold(Level.TRACE);
        loggerDelegator.refresh();
    }

    @Test
    public void shouldRecordEventLocation() {
        loggerDelegator.error("Some message");
        LogEvent event = observer.getEvents().get(0);
        assertEquals(34, event.getCallerLocation().getLineNumber());
        assertEquals("LoggerDelegatorTest.java", event.getCallerLocation().getFileName());
        assertEquals(getClass().getName(), event.getCallerLocation().getClassName());
        assertEquals("shouldRecordEventLocation", event.getCallerLocation().getMethodName());
    }

    @Test
    public void shouldShowLogLevel() {
        loggerDelegator.setLevelThreshold(Level.INFO);
        loggerDelegator.refresh();

        assertTrue(loggerDelegator.isErrorEnabled());
        assertTrue(loggerDelegator.isWarnEnabled());
        assertTrue(loggerDelegator.isInfoEnabled());
        assertFalse(loggerDelegator.isDebugEnabled());
        assertFalse(loggerDelegator.isTraceEnabled());

        assertTrue(loggerDelegator.isErrorEnabled(null));
        assertTrue(loggerDelegator.isWarnEnabled(null));
        assertTrue(loggerDelegator.isInfoEnabled(null));
        assertFalse(loggerDelegator.isDebugEnabled(null));
        assertFalse(loggerDelegator.isTraceEnabled(null));
    }

    @Test
    public void shouldLogError() {
        String message = randomString();
        loggerDelegator.error(message);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(message, event.getMessage());
        assertEquals(Level.ERROR, event.getLevel());
        assertArrayEquals(new Object[0], event.getArgumentArray());
    }

    @Test
    public void shouldLogErrorWithArgument() {
        String message = randomString() + ": {}";
        UUID uuid = UUID.randomUUID();
        loggerDelegator.error(message, uuid);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(message, event.getMessage());
        assertEquals(Level.ERROR, event.getLevel());
        assertArrayEquals(new Object[] { uuid }, event.getArgumentArray());
    }

    @Test
    public void shouldLogErrorWithTwoArgument() {
        String message = randomString() + ": {} and {}";
        UUID uuid = UUID.randomUUID();
        loggerDelegator.error(message, uuid, uuid);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(message, event.getMessage());
        assertEquals(Level.ERROR, event.getLevel());
        assertArrayEquals(new Object[] { uuid, uuid }, event.getArgumentArray());
    }

    @Test
    public void shouldLogErrorWithMultipleArgument() {
        String message = "Here is some message: {}, {} and {}";
        loggerDelegator.error(message, 1, 2, 3);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(message, event.getMessage());
        assertEquals("Here is some message: 1, 2 and 3", event.formatMessage());
        assertEquals(Level.ERROR, event.getLevel());
        assertArrayEquals(new Object[] { 1, 2, 3 }, event.getArgumentArray());
    }

    @Test
    public void shouldLogErrorWithException() {
        String message = randomString();
        Exception e = randomException();
        loggerDelegator.error(message, e);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(message, event.getMessage());
        assertEquals(e, event.getThrowable());
        assertEquals(Level.ERROR, event.getLevel());
        assertArrayEquals(new Object[0], event.getArgumentArray());
    }

    @Test
    public void shouldLogErrorWithExceptionAndArguments() {
        String message = "Here is some message with an exception: {}, {} and {}";
        Exception e = randomException();
        loggerDelegator.error(message, 1, 2, 3, e);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(e, event.getThrowable());
        assertEquals(Level.ERROR, event.getLevel());
        assertEquals("Here is some message with an exception: 1, 2 and 3", event.formatMessage());
    }

    @Test
    public void shouldLogWarn() {
        String message = randomString();
        loggerDelegator.warn(message);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(message, event.getMessage());
        assertEquals(Level.WARN, event.getLevel());
        assertArrayEquals(new Object[0], event.getArgumentArray());
    }

    @Test
    public void shouldLogWarnWithArgument() {
        String message = randomString() + ": {}";
        UUID uuid = UUID.randomUUID();
        loggerDelegator.warn(message, uuid);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(message, event.getMessage());
        assertEquals(Level.WARN, event.getLevel());
        assertArrayEquals(new Object[] { uuid }, event.getArgumentArray());
    }

    @Test
    public void shouldLogWarnWithTwoArgument() {
        String message = randomString() + ": {} and {}";
        UUID uuid = UUID.randomUUID();
        loggerDelegator.warn(message, uuid, uuid);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(message, event.getMessage());
        assertEquals(Level.WARN, event.getLevel());
        assertArrayEquals(new Object[] { uuid, uuid }, event.getArgumentArray());
    }

    @Test
    public void shouldLogWarnWithMultipleArgument() {
        String message = "Here is some message: {}, {} and {}";
        loggerDelegator.warn(message, 1, 2, 3);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(message, event.getMessage());
        assertEquals("Here is some message: 1, 2 and 3", event.formatMessage());
        assertEquals(Level.WARN, event.getLevel());
        assertArrayEquals(new Object[] { 1, 2, 3 }, event.getArgumentArray());
    }

    @Test
    public void shouldLogWarnWithException() {
        String message = randomString();
        Exception e = randomException();
        loggerDelegator.warn(message, e);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(message, event.getMessage());
        assertEquals(e, event.getThrowable());
        assertEquals(Level.WARN, event.getLevel());
        assertArrayEquals(new Object[0], event.getArgumentArray());
    }

    @Test
    public void shouldLogWarnWithExceptionAndArguments() {
        String message = "Here is some message with an exception: {}, {} and {}";
        Exception e = randomException();
        loggerDelegator.warn(message, 1, 2, 3, e);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(e, event.getThrowable());
        assertEquals(Level.WARN, event.getLevel());
        assertEquals("Here is some message with an exception: 1, 2 and 3", event.formatMessage());
    }

    @Test
    public void shouldLogInfo() {
        String message = randomString();
        loggerDelegator.info(message);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(message, event.getMessage());
        assertEquals(Level.INFO, event.getLevel());
        assertArrayEquals(new Object[0], event.getArgumentArray());
    }

    @Test
    public void shouldLogInfoWithArgument() {
        String message = randomString() + ": {}";
        UUID uuid = UUID.randomUUID();
        loggerDelegator.info(message, uuid);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(message, event.getMessage());
        assertEquals(Level.INFO, event.getLevel());
        assertArrayEquals(new Object[] { uuid }, event.getArgumentArray());
    }

    @Test
    public void shouldLogInfoWithTwoArgument() {
        String message = randomString() + ": {} and {}";
        UUID uuid = UUID.randomUUID();
        loggerDelegator.info(message, uuid, uuid);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(message, event.getMessage());
        assertEquals(Level.INFO, event.getLevel());
        assertArrayEquals(new Object[] { uuid, uuid }, event.getArgumentArray());
    }

    @Test
    public void shouldLogInfoWithMultipleArgument() {
        String message = "Here is some message: {}, {} and {}";
        loggerDelegator.info(message, 1, 2, 3);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(message, event.getMessage());
        assertEquals("Here is some message: 1, 2 and 3", event.formatMessage());
        assertEquals(Level.INFO, event.getLevel());
        assertArrayEquals(new Object[] { 1, 2, 3 }, event.getArgumentArray());
    }

    @Test
    public void shouldLogInfoWithException() {
        String message = randomString();
        Exception e = randomException();
        loggerDelegator.info(message, e);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(message, event.getMessage());
        assertEquals(e, event.getThrowable());
        assertEquals(Level.INFO, event.getLevel());
        assertArrayEquals(new Object[0], event.getArgumentArray());
    }

    @Test
    public void shouldLogInfoWithExceptionAndArguments() {
        String message = "Here is some message with an exception: {}, {} and {}";
        Exception e = randomException();
        loggerDelegator.info(message, 1, 2, 3, e);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(e, event.getThrowable());
        assertEquals(Level.INFO, event.getLevel());
        assertEquals("Here is some message with an exception: 1, 2 and 3", event.formatMessage());
    }

    @Test
    public void shouldLogDebug() {
        String message = randomString();
        loggerDelegator.debug(message);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(message, event.getMessage());
        assertEquals(Level.DEBUG, event.getLevel());
        assertArrayEquals(new Object[0], event.getArgumentArray());
    }

    @Test
    public void shouldLogDebugWithArgument() {
        String message = randomString() + ": {}";
        UUID uuid = UUID.randomUUID();
        loggerDelegator.debug(message, uuid);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(message, event.getMessage());
        assertEquals(Level.DEBUG, event.getLevel());
        assertArrayEquals(new Object[] { uuid }, event.getArgumentArray());
    }

    @Test
    public void shouldLogDebugWithTwoArgument() {
        String message = randomString() + ": {} and {}";
        UUID uuid = UUID.randomUUID();
        loggerDelegator.debug(message, uuid, uuid);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(message, event.getMessage());
        assertEquals(Level.DEBUG, event.getLevel());
        assertArrayEquals(new Object[] { uuid, uuid }, event.getArgumentArray());
    }

    @Test
    public void shouldLogDebugWithMultipleArgument() {
        String message = "Here is some message: {}, {} and {}";
        loggerDelegator.debug(message, 1, 2, 3);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(message, event.getMessage());
        assertEquals("Here is some message: 1, 2 and 3", event.formatMessage());
        assertEquals(Level.DEBUG, event.getLevel());
        assertArrayEquals(new Object[] { 1, 2, 3 }, event.getArgumentArray());
    }

    @Test
    public void shouldLogDebugWithException() {
        String message = randomString();
        Exception e = randomException();
        loggerDelegator.debug(message, e);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(message, event.getMessage());
        assertEquals(e, event.getThrowable());
        assertEquals(Level.DEBUG, event.getLevel());
        assertArrayEquals(new Object[0], event.getArgumentArray());
    }

    @Test
    public void shouldLogDebugWithExceptionAndArguments() {
        String message = "Here is some message with an exception: {}, {} and {}";
        Exception e = randomException();
        loggerDelegator.debug(message, 1, 2, 3, e);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(e, event.getThrowable());
        assertEquals(Level.DEBUG, event.getLevel());
        assertEquals("Here is some message with an exception: 1, 2 and 3", event.formatMessage());
    }

    @Test
    public void shouldLogTrace() {
        String message = randomString();
        loggerDelegator.trace(message);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(message, event.getMessage());
        assertEquals(Level.TRACE, event.getLevel());
        assertArrayEquals(new Object[0], event.getArgumentArray());
    }

    @Test
    public void shouldLogTraceWithArgument() {
        String message = randomString() + ": {}";
        UUID uuid = UUID.randomUUID();
        loggerDelegator.trace(message, uuid);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(message, event.getMessage());
        assertEquals(Level.TRACE, event.getLevel());
        assertArrayEquals(new Object[] { uuid }, event.getArgumentArray());
    }

    @Test
    public void shouldLogTraceWithTwoArgument() {
        String message = randomString() + ": {} and {}";
        UUID uuid = UUID.randomUUID();
        loggerDelegator.trace(message, uuid, uuid);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(message, event.getMessage());
        assertEquals(Level.TRACE, event.getLevel());
        assertArrayEquals(new Object[] { uuid, uuid }, event.getArgumentArray());
    }

    @Test
    public void shouldLogTraceWithMultipleArgument() {
        String message = "Here is some message: {}, {} and {}";
        loggerDelegator.trace(message, 1, 2, 3);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(message, event.getMessage());
        assertEquals("Here is some message: 1, 2 and 3", event.formatMessage());
        assertEquals(Level.TRACE, event.getLevel());
        assertArrayEquals(new Object[] { 1, 2, 3 }, event.getArgumentArray());
    }

    @Test
    public void shouldLogTraceWithException() {
        String message = randomString();
        Exception e = randomException();
        loggerDelegator.trace(message, e);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(message, event.getMessage());
        assertEquals(e, event.getThrowable());
        assertEquals(Level.TRACE, event.getLevel());
        assertArrayEquals(new Object[0], event.getArgumentArray());
    }

    @Test
    public void shouldLogTraceWithExceptionAndArguments() {
        String message = "Here is some message with an exception: {}, {} and {}";
        Exception e = randomException();
        loggerDelegator.trace(message, 1, 2, 3, e);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(e, event.getThrowable());
        assertEquals(Level.TRACE, event.getLevel());
        assertEquals("Here is some message with an exception: 1, 2 and 3", event.formatMessage());
    }

    @Test
    public void shouldLogErrorWithMarker() {
        String message = randomString();
        loggerDelegator.error(marker, message);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(marker, event.getMarker());
        assertEquals(message, event.getMessage());
        assertEquals(Level.ERROR, event.getLevel());
        assertArrayEquals(new Object[0], event.getArgumentArray());
    }

    @Test
    public void shouldLogErrorWithArgumentWithMarker() {
        String message = randomString() + ": {}";
        UUID uuid = UUID.randomUUID();
        loggerDelegator.error(marker, message, uuid);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(marker, event.getMarker());
        assertEquals(message, event.getMessage());
        assertEquals(Level.ERROR, event.getLevel());
        assertArrayEquals(new Object[] { uuid }, event.getArgumentArray());
    }

    @Test
    public void shouldLogErrorWithTwoArgumentWithMarker() {
        String message = randomString() + ": {} and {}";
        UUID uuid = UUID.randomUUID();
        loggerDelegator.error(marker, message, uuid, uuid);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(marker, event.getMarker());
        assertEquals(message, event.getMessage());
        assertEquals(Level.ERROR, event.getLevel());
        assertArrayEquals(new Object[] { uuid, uuid }, event.getArgumentArray());
    }

    @Test
    public void shouldLogErrorWithMultipleArgumentWithMarker() {
        String message = "Here is some message: {}, {} and {}";
        loggerDelegator.error(marker, message, 1, 2, 3);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(marker, event.getMarker());
        assertEquals(message, event.getMessage());
        assertEquals("Here is some message: 1, 2 and 3", event.formatMessage());
        assertEquals(Level.ERROR, event.getLevel());
        assertArrayEquals(new Object[] { 1, 2, 3 }, event.getArgumentArray());
    }

    @Test
    public void shouldLogErrorWithExceptionWithMarker() {
        String message = randomString();
        Exception e = randomException();
        loggerDelegator.error(marker, message, e);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(marker, event.getMarker());
        assertEquals(message, event.getMessage());
        assertEquals(e, event.getThrowable());
        assertEquals(Level.ERROR, event.getLevel());
        assertArrayEquals(new Object[0], event.getArgumentArray());
    }

    @Test
    public void shouldLogErrorWithExceptionAndArgumentsWithMarker() {
        String message = "Here is some message with an exception: {}, {} and {}";
        Exception e = randomException();
        loggerDelegator.error(marker, message, 1, 2, 3, e);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(marker, event.getMarker());
        assertEquals(e, event.getThrowable());
        assertEquals(Level.ERROR, event.getLevel());
        assertEquals("Here is some message with an exception: 1, 2 and 3", event.formatMessage());
    }

    @Test
    public void shouldLogWarnWithMarker() {
        String message = randomString();
        loggerDelegator.warn(marker, message);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(marker, event.getMarker());
        assertEquals(message, event.getMessage());
        assertEquals(Level.WARN, event.getLevel());
        assertArrayEquals(new Object[0], event.getArgumentArray());
    }

    @Test
    public void shouldLogWarnWithArgumentWithMarker() {
        String message = randomString() + ": {}";
        UUID uuid = UUID.randomUUID();
        loggerDelegator.warn(marker, message, uuid);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(marker, event.getMarker());
        assertEquals(message, event.getMessage());
        assertEquals(Level.WARN, event.getLevel());
        assertArrayEquals(new Object[] { uuid }, event.getArgumentArray());
    }

    @Test
    public void shouldLogWarnWithTwoArgumentWithMarker() {
        String message = randomString() + ": {} and {}";
        UUID uuid = UUID.randomUUID();
        loggerDelegator.warn(marker, message, uuid, uuid);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(marker, event.getMarker());
        assertEquals(message, event.getMessage());
        assertEquals(Level.WARN, event.getLevel());
        assertArrayEquals(new Object[] { uuid, uuid }, event.getArgumentArray());
    }

    @Test
    public void shouldLogWarnWithMultipleArgumentWithMarker() {
        String message = "Here is some message: {}, {} and {}";
        loggerDelegator.warn(marker, message, 1, 2, 3);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(marker, event.getMarker());
        assertEquals(message, event.getMessage());
        assertEquals("Here is some message: 1, 2 and 3", event.formatMessage());
        assertEquals(Level.WARN, event.getLevel());
        assertArrayEquals(new Object[] { 1, 2, 3 }, event.getArgumentArray());
    }

    @Test
    public void shouldLogWarnWithExceptionWithMarker() {
        String message = randomString();
        Exception e = randomException();
        loggerDelegator.warn(marker, message, e);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(marker, event.getMarker());
        assertEquals(message, event.getMessage());
        assertEquals(e, event.getThrowable());
        assertEquals(Level.WARN, event.getLevel());
        assertArrayEquals(new Object[0], event.getArgumentArray());
    }

    @Test
    public void shouldLogWarnWithExceptionAndArgumentsWithMarker() {
        String message = "Here is some message with an exception: {}, {} and {}";
        Exception e = randomException();
        loggerDelegator.warn(marker, message, 1, 2, 3, e);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(marker, event.getMarker());
        assertEquals(e, event.getThrowable());
        assertEquals(Level.WARN, event.getLevel());
        assertEquals("Here is some message with an exception: 1, 2 and 3", event.formatMessage());
    }

    @Test
    public void shouldLogInfoWithMarker() {
        String message = randomString();
        loggerDelegator.info(marker, message);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(marker, event.getMarker());
        assertEquals(message, event.getMessage());
        assertEquals(Level.INFO, event.getLevel());
        assertArrayEquals(new Object[0], event.getArgumentArray());
    }

    @Test
    public void shouldLogInfoWithArgumentWithMarker() {
        String message = randomString() + ": {}";
        UUID uuid = UUID.randomUUID();
        loggerDelegator.info(marker, message, uuid);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(marker, event.getMarker());
        assertEquals(message, event.getMessage());
        assertEquals(Level.INFO, event.getLevel());
        assertArrayEquals(new Object[] { uuid }, event.getArgumentArray());
    }

    @Test
    public void shouldLogInfoWithTwoArgumentWithMarker() {
        String message = randomString() + ": {} and {}";
        UUID uuid = UUID.randomUUID();
        loggerDelegator.info(marker, message, uuid, uuid);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(marker, event.getMarker());
        assertEquals(message, event.getMessage());
        assertEquals(Level.INFO, event.getLevel());
        assertArrayEquals(new Object[] { uuid, uuid }, event.getArgumentArray());
    }

    @Test
    public void shouldLogInfoWithMultipleArgumentWithMarker() {
        String message = "Here is some message: {}, {} and {}";
        loggerDelegator.info(marker, message, 1, 2, 3);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(marker, event.getMarker());
        assertEquals(message, event.getMessage());
        assertEquals("Here is some message: 1, 2 and 3", event.formatMessage());
        assertEquals(Level.INFO, event.getLevel());
        assertArrayEquals(new Object[] { 1, 2, 3 }, event.getArgumentArray());
    }

    @Test
    public void shouldLogInfoWithExceptionWithMarker() {
        String message = randomString();
        Exception e = randomException();
        loggerDelegator.info(marker, message, e);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(marker, event.getMarker());
        assertEquals(message, event.getMessage());
        assertEquals(e, event.getThrowable());
        assertEquals(Level.INFO, event.getLevel());
        assertArrayEquals(new Object[0], event.getArgumentArray());
    }

    @Test
    public void shouldLogInfoWithExceptionAndArgumentsWithMarker() {
        String message = "Here is some message with an exception: {}, {} and {}";
        Exception e = randomException();
        loggerDelegator.info(marker, message, 1, 2, 3, e);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(marker, event.getMarker());
        assertEquals(e, event.getThrowable());
        assertEquals(Level.INFO, event.getLevel());
        assertEquals("Here is some message with an exception: 1, 2 and 3", event.formatMessage());
    }

    @Test
    public void shouldLogDebugWithMarker() {
        String message = randomString();
        loggerDelegator.debug(marker, message);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(marker, event.getMarker());
        assertEquals(message, event.getMessage());
        assertEquals(Level.DEBUG, event.getLevel());
        assertArrayEquals(new Object[0], event.getArgumentArray());
    }

    @Test
    public void shouldLogDebugWithArgumentWithMarker() {
        String message = randomString() + ": {}";
        UUID uuid = UUID.randomUUID();
        loggerDelegator.debug(marker, message, uuid);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(marker, event.getMarker());
        assertEquals(message, event.getMessage());
        assertEquals(Level.DEBUG, event.getLevel());
        assertArrayEquals(new Object[] { uuid }, event.getArgumentArray());
    }

    @Test
    public void shouldLogDebugWithTwoArgumentWithMarker() {
        String message = randomString() + ": {} and {}";
        UUID uuid = UUID.randomUUID();
        loggerDelegator.debug(marker, message, uuid, uuid);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(marker, event.getMarker());
        assertEquals(message, event.getMessage());
        assertEquals(Level.DEBUG, event.getLevel());
        assertArrayEquals(new Object[] { uuid, uuid }, event.getArgumentArray());
    }

    @Test
    public void shouldLogDebugWithMultipleArgumentWithMarker() {
        String message = "Here is some message: {}, {} and {}";
        loggerDelegator.debug(marker, message, 1, 2, 3);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(marker, event.getMarker());
        assertEquals(message, event.getMessage());
        assertEquals("Here is some message: 1, 2 and 3", event.formatMessage());
        assertEquals(Level.DEBUG, event.getLevel());
        assertArrayEquals(new Object[] { 1, 2, 3 }, event.getArgumentArray());
    }

    @Test
    public void shouldLogDebugWithExceptionWithMarker() {
        String message = randomString();
        Exception e = randomException();
        loggerDelegator.debug(marker, message, e);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(marker, event.getMarker());
        assertEquals(message, event.getMessage());
        assertEquals(e, event.getThrowable());
        assertEquals(Level.DEBUG, event.getLevel());
        assertArrayEquals(new Object[0], event.getArgumentArray());
    }

    @Test
    public void shouldLogDebugWithExceptionAndArgumentsWithMarker() {
        String message = "Here is some message with an exception: {}, {} and {}";
        Exception e = randomException();
        loggerDelegator.debug(marker, message, 1, 2, 3, e);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(marker, event.getMarker());
        assertEquals(e, event.getThrowable());
        assertEquals(Level.DEBUG, event.getLevel());
        assertEquals("Here is some message with an exception: 1, 2 and 3", event.formatMessage());
    }

    @Test
    public void shouldLogTraceWithMarker() {
        String message = randomString();
        loggerDelegator.trace(marker, message);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(marker, event.getMarker());
        assertEquals(message, event.getMessage());
        assertEquals(Level.TRACE, event.getLevel());
        assertArrayEquals(new Object[0], event.getArgumentArray());
    }

    @Test
    public void shouldLogTraceWithArgumentWithMarker() {
        String message = randomString() + ": {}";
        UUID uuid = UUID.randomUUID();
        loggerDelegator.trace(marker, message, uuid);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(marker, event.getMarker());
        assertEquals(message, event.getMessage());
        assertEquals(Level.TRACE, event.getLevel());
        assertArrayEquals(new Object[] { uuid }, event.getArgumentArray());
    }

    @Test
    public void shouldLogTraceWithTwoArgumentWithMarker() {
        String message = randomString() + ": {} and {}";
        UUID uuid = UUID.randomUUID();
        loggerDelegator.trace(marker, message, uuid, uuid);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(marker, event.getMarker());
        assertEquals(message, event.getMessage());
        assertEquals(Level.TRACE, event.getLevel());
        assertArrayEquals(new Object[] { uuid, uuid }, event.getArgumentArray());
    }

    @Test
    public void shouldLogTraceWithMultipleArgumentWithMarker() {
        String message = "Here is some message: {}, {} and {}";
        loggerDelegator.trace(marker, message, 1, 2, 3);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(marker, event.getMarker());
        assertEquals(message, event.getMessage());
        assertEquals("Here is some message: 1, 2 and 3", event.formatMessage());
        assertEquals(Level.TRACE, event.getLevel());
        assertArrayEquals(new Object[] { 1, 2, 3 }, event.getArgumentArray());
    }

    @Test
    public void shouldLogTraceWithExceptionWithMarker() {
        String message = randomString();
        Exception e = randomException();
        loggerDelegator.trace(marker, message, e);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(marker, event.getMarker());
        assertEquals(message, event.getMessage());
        assertEquals(e, event.getThrowable());
        assertEquals(Level.TRACE, event.getLevel());
        assertArrayEquals(new Object[0], event.getArgumentArray());
    }

    @Test
    public void shouldLogTraceWithExceptionAndArgumentsWithMarker() {
        String message = "Here is some message with an exception: {}, {} and {}";
        Exception e = randomException();
        loggerDelegator.trace(marker, message, 1, 2, 3, e);
        LogEvent event = observer.getEvents().get(0);
        assertEquals(marker, event.getMarker());
        assertEquals(e, event.getThrowable());
        assertEquals(Level.TRACE, event.getLevel());
        assertEquals("Here is some message with an exception: 1, 2 and 3", event.formatMessage());
    }

    private Exception randomException() {
        return new IOException("Something happened with " + randomString());
    }

    private String randomString() {
        return UUID.randomUUID().toString();
    }

}
