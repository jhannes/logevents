package org.logevents;

import java.util.ArrayList;
import java.util.List;

import org.logevents.formatting.ConsoleLogEventFormatter;
import org.logevents.formatting.LogEventFormatter;
import org.logevents.observers.ConsoleLogEventObserver;
import org.slf4j.event.Level;

public class DefaultTestLogEventConfigurator extends DefaultLogEventConfigurator {

    private static class ConsoleLogEventTestFormatter extends ConsoleLogEventFormatter {

        @Override
        public String apply(LogEvent logEvent) {
            return String.format("TEST(%s) %s [%s] [%s] [%s]: %s",
                    getTestMethod(logEvent),
                    logEvent.getZonedDateTime().toLocalTime(),
                    logEvent.getThreadName(),
                    colorizedLevel(logEvent),
                    format.bold(logEvent.getLoggerName()),
                    logEvent.formatMessage())
                    + exceptionFormatter.format(logEvent.getThrowable());
        }

        private Object getTestMethod(LogEvent logEvent) {
            StackTraceElement[] stackTrace = logEvent.getStackTrace();

            int junitRunnerPos = -1;
            for (int i = 0; i < stackTrace.length-1; i++) {
                StackTraceElement stackTraceElement = stackTrace[i];
                if (stackTraceElement.getClassName().equals("org.junit.runners.BlockJUnit4ClassRunner")) {
                    junitRunnerPos = i;
                    break;
                }
            }
            if (junitRunnerPos == -1) return null;

            int invokePos = -1;
            for (int i = junitRunnerPos; i >= 0; i--) {
                StackTraceElement stackTraceElement = stackTrace[i];
                if (stackTraceElement.getClassName().equals("java.lang.reflect.Method")) {
                    invokePos = i;
                    break;
                }
            }
            if (invokePos == -1) return null;

            int testMethod = -1;
            for (int i = invokePos; i >= 0; i--) {
                StackTraceElement stackTraceElement = stackTrace[i];
                if (!stackTraceElement.getClassName().startsWith("java.") && !stackTraceElement.getClassName().startsWith("sun.")) {
                    testMethod = i;
                    break;
                }
            }
            return testMethod != -1 ? getMethodRef(stackTrace[testMethod]) : null;
        }

        private Object getMethodRef(StackTraceElement stackTraceElement) {
            int simpleClassNamePos = stackTraceElement.getClassName().lastIndexOf('.');
            return stackTraceElement.getClassName().substring(simpleClassNamePos+1) + "." + stackTraceElement.getMethodName();
        }

    }

    @Override
    protected List<String> getProfiles() {
        List<String> profiles = new ArrayList<>(super.getProfiles());
        profiles.add("test");
        return profiles;
    }

    @Override
    protected void setDefaultLogging(LogEventFactory factory) {
        factory.setLevel(factory.getRootLogger(), Level.WARN);
        factory.setObserver(factory.getRootLogger(),
                new ConsoleLogEventObserver(createFormatter()),
                false);
    }

    LogEventFormatter createFormatter() {
        return new ConsoleLogEventTestFormatter();
    }
}
