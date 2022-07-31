package org.logeventsdemo.junit5;

import org.junit.jupiter.api.Test;
import org.logevents.LogEventFormatter;
import org.logevents.config.Configuration;
import org.logevents.config.DefaultTestLogEventConfigurator;
import org.logevents.optional.junit.LogEventSampler;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleTestFormatterTest {

    @Test
    void shouldShowTestName() throws InterruptedException {
        AtomicReference<String> formattedMessage = new AtomicReference<>();
        LogEventFormatter formatter = new DefaultTestLogEventConfigurator()
                .createConsoleLogEventObserver(new Configuration())
                .getFormatter();
        Thread thread = new Thread(() -> formattedMessage.set(formatter.apply(new LogEventSampler().build())));
        thread.start();
        thread.join();

        assertTrue(formattedMessage.get().contains("TEST(ConsoleTestFormatterTest.shouldShowTestName(ConsoleTestFormatterTest.java:"),
                formattedMessage.get() + " should start with test name");

    }

}
