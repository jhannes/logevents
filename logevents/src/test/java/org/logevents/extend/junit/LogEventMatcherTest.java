package org.logevents.extend.junit;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;
import org.logevents.LogEvent;
import org.logevents.extend.servlets.LogEventSampler;
import org.slf4j.event.Level;

import javax.mail.MessagingException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LogEventMatcherTest {

    @Test
    public void shouldListDifferingFields() {
        LogEvent event = new LogEventSampler().build();

        LogEventMatcher matcher = expect ->
            expect.level(event.getLevel())
                    .pattern("other message")
                    .exception(MessagingException.class);
        LogEventMatcherContext context = new LogEventMatcherContext(event);
        matcher.apply(context);

        assertEquals(Arrays.asList("level"), context.getMatchedFields());
        assertEquals(Arrays.asList("message", "throwable"), context.getDifferingFields());
    }

    @Test
    public void shouldListMatchedFields() {
        LogEvent event = new LogEventSampler().withThrowable().build();

        LogEventMatcher matcher = expect ->
            expect.level(event.getLevel())
                .logger(event.getLoggerName())
                .pattern(event.getMessage())
                .exception(event.getThrowable().getClass());
        LogEventMatcherContext context = new LogEventMatcherContext(event);
        matcher.apply(context);

        assertEquals(Arrays.asList("level", "logger", "message", "throwable"), context.getMatchedFields());
    }

    @Test
    public void shouldDeclarePartialMatchWhenAllMatch() {
        LogEvent event = new LogEventSampler().withThrowable().build();

        LogEventMatcher matcher = expect ->
                expect.level(event.getLevel())
                        .logger(event.getLoggerName())
                        .pattern(event.getMessage())
                        .exception(event.getThrowable().getClass());

        LogEventMatcherContext context = new LogEventMatcherContext(event, matcher);
        assertTrue(context.isPartialMatch());
        assertTrue(context.isExactMatch());
    }

    @Test
    public void shouldDeclarePartialMatchWhenLevelAndLoggerMatch() {
        LogEvent event = new LogEventSampler().build();

        LogEventMatcher matcher = expect ->
                expect.level(event.getLevel())
                        .logger(event.getLoggerName())
                        .pattern("Other message")
                        .exception(MessagingException.class);

        LogEventMatcherContext context = new LogEventMatcherContext(event, matcher);
        assertTrue(context.isPartialMatch());
        assertFalse(context.isExactMatch());
        Assert.assertThat(context.describePartialMatch(),
                CoreMatchers.containsString("level: [" + event.getLevel() + "]"));
        Assert.assertThat(context.describePartialMatch(),
                CoreMatchers.containsString("logger: [" + event.getLoggerName() + "]"));
    }

    @Test
    public void shouldOutputDifference() {
        LogEvent event = new LogEventSampler().build();

        LogEventMatcher matcher = expect ->
                expect.level(event.getLevel())
                        .logger(event.getLoggerName())
                        .pattern("Other message")
                        .exception(MessagingException.class);

        LogEventMatcherContext context = new LogEventMatcherContext(event, matcher);
        Assert.assertThat(context.diff(),
                CoreMatchers.containsString("message expected: [Other message] but was [" + event.getMessage() + "]"));
        Assert.assertThat(context.diff(),
                CoreMatchers.containsString("throwable expected: [class javax.mail.MessagingException] but was [null]"));
        Assert.assertThat(context.diff(),
                CoreMatchers.not(CoreMatchers.containsString(event.getLoggerName())));
    }

    @Test
    public void shouldNotMatchNullLogEvent() {
        LogEventMatcher matcher = expect ->
                expect.level(Level.WARN).logger(getClass()).pattern("Other message");

        LogEvent nullEvent = new LogEvent(null, null, null, null, new Object[0]);
        LogEventMatcherContext context = new LogEventMatcherContext(nullEvent, matcher);
        Assert.assertThat(context.diff(),
                CoreMatchers.containsString("level expected: [WARN] but was [null]"));
    }

    @Test
    public void shouldDeclareNoMatchWhenLoggerDiffer() {
        LogEvent event = new LogEventSampler().withThrowable().build();

        LogEventMatcher matcher = expect ->
                expect.level(event.getLevel()).logger("other.logger.name");

        LogEventMatcherContext context = new LogEventMatcherContext(event, matcher);
        assertFalse(context.isPartialMatch());
        assertFalse(context.isExactMatch());
    }

    @Test
    public void shouldMatchExceptions() {
        LogEvent event = new LogEventSampler().withThrowable().build();
        assertIsExactMatch(event,
                expect -> expect.exception(event.getThrowable().getClass())
        );
        assertIsExactMatch(event,
                expect -> expect.exception(event.getThrowable().getClass(), event.getThrowable().getMessage())
        );
        assertNotExactMatch(event, expect -> expect.exception(MessagingException.class));
        assertNotExactMatch(new LogEventSampler().build(), expect -> expect.exception(MessagingException.class));
        assertNotExactMatch(event,
                expect -> expect.exception(MessagingException.class, event.getThrowable().getMessage())
        );
        assertNotExactMatch(event,
                expect -> expect.exception(event.getThrowable().getClass(), "Other event message")
        );
        assertNotExactMatch(event, LogEventMatcherContext::noException);
        assertIsExactMatch(new LogEventSampler().build(), LogEventMatcherContext::noException);
    }

    @Test
    public void shouldMatchMessage() {
        LogEvent event = new LogEventSampler().withArgs("a", "b", "c").build();
        assertIsExactMatch(event, expect -> expect.pattern(event.getMessage()));
        assertNotExactMatch(event, expect -> expect.pattern("Other message"));
        assertIsExactMatch(event, expect -> expect.args("a", "b", "c"));
        assertNotExactMatch(event, expect -> expect.args("a", "b"));
        assertNotExactMatch(event, expect -> expect.args("a", "b", "d"));
    }

    @Test
    public void shouldMatchFormattedMessage() {
        LogEvent event = new LogEventSampler().withFormat("Args [{}] [{}] and [{}]").withArgs("a", "b", "c").build();

        assertIsExactMatch(event, expect -> expect.formattedMessage("Args [a] [b] and [c]"));
        assertNotExactMatch(event, expect -> expect.formattedMessage("Args [] [b] and [c]"));
        assertNotExactMatch(event, expect -> expect.formattedMessage("Args [{}] [{}] and [{}]"));
    }

    @Test
    public void shouldMatchArgsWithNulls() {
        LogEvent event = new LogEventSampler().withArgs(null, "b", "c").build();
        LogEventMatcher matcher = expect -> expect.args(null, "b", "c");
        assertIsExactMatch(event, matcher);
        assertNotExactMatch(event, expect -> expect.args("null", "b", "c"));
        assertNotExactMatch(new LogEventSampler().withArgs("null", "b", "c").build(), matcher);
    }

    void assertIsExactMatch(LogEvent event, LogEventMatcher matcher) {
        assertTrue(new LogEventMatcherContext(event, matcher).isExactMatch());
    }

    void assertNotExactMatch(LogEvent event, LogEventMatcher matcher) {
        assertFalse(new LogEventMatcherContext(event, matcher).isExactMatch());
    }

}
