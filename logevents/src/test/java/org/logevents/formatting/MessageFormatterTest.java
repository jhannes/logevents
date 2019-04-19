package org.logevents.formatting;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.logevents.status.LogEventStatus;
import org.logevents.status.StatusEvent;

import static org.junit.Assert.assertEquals;

public class MessageFormatterTest {

    private static StatusEvent.StatusLevel oldThreshold;
    private MessageFormatter messageFormatter = new MessageFormatter();

    @Test
    public void shouldOutputHardcodedMessage() {
        assertEquals("This is a message", messageFormatter.format("This is a message"));
    }

    @Test
    public void shouldInterleaveExpectedArguments() {
        assertEquals("This is a nice message about a sordid affair",
                messageFormatter.format("This is a {} message about a {} affair", "nice", "sordid"));
    }

    @Test
    public void shouldOutputNullArg() {
        assertEquals("In this message [null] is missing",
                messageFormatter.format("In this message [{}] is missing", new Object[] { null }));
    }

    @Test
    public void shouldDisplayPrimitiveArrays() {
        assertEquals("This is a message with number [1, 2, 4] included",
                messageFormatter.format(
                        "This is a message with number {} included",
                        new Object[] { new int[] {1, 2, 4} }
                ));
    }

    @Test
    public void shouldLeaveCurliesForMissingArguments() {
        assertEquals("Here is a nice message with one {} to space as well as another {}",
                messageFormatter.format("Here is a {} message with one {} to space as well as another {}", "nice"));
    }

    @Test
    public void shouldOmitExtraArguments() {
        assertEquals("This message will only print argument one and leave the rest out",
                messageFormatter.format("This message will only print argument {} and leave the rest out", "one", "two", "three"));
    }

    @Test
    public void shouldEscapeCharacters() {
        assertEquals("Message where the first {} and second {} is escaped with \\backslash",
                messageFormatter.format("Message where the first \\{} and second \\{} is escaped with \\\\{}", "backslash"));
    }

    @Test
    public void shouldCatchExceptionsInToString() {
        Object faulty = new Object() {
            @Override
            public String toString() {
                throw new IllegalArgumentException("Test");
            }
        };
        assertEquals("A message where [FAILED toString()] throws",
                messageFormatter.format("A message where {} throws", faulty));
    }

    @Test
    public void shouldSuppressDuplicateNestedArray() {
        Object[] nestedArray = new Object[] { null, "alice", "bob", "charlie" };
        nestedArray[0] = nestedArray;
        Object[] argumentArray = new Object[] { "Test", nestedArray, "Something else" };
        assertEquals("A message where [Test, [[...], alice, bob, charlie], Something else] is fairly interesting",
                messageFormatter.format("A message where {} is fairly interesting",
                        new Object[] { argumentArray })
                );
    }


    @BeforeClass
    public static void turnOffStatusLogging() {
        oldThreshold = LogEventStatus.getInstance().setThreshold(StatusEvent.StatusLevel.NONE);
    }

    @AfterClass
    public static void restoreStatusLogging() {
        LogEventStatus.getInstance().setThreshold(oldThreshold);
    }
}
