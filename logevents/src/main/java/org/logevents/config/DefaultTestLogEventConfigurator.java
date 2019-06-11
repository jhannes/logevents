package org.logevents.config;

import org.logevents.LogEvent;
import org.logevents.formatting.ConsoleLogEventFormatter;
import org.logevents.observers.ConsoleLogEventObserver;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class DefaultTestLogEventConfigurator extends DefaultLogEventConfigurator {

    private static class ConsoleLogEventTestFormatter extends ConsoleLogEventFormatter {

        public ConsoleLogEventTestFormatter() {
            exceptionFormatter.setPackageFilter(new String[] {
                    "org.junit.runners",
                    "org.junit.internal.runners",
                    "jdk.internal.reflect",
                    "com.intellij.junit4",
                    "com.intellij.rt.execution.junit"
            });
        }

        @Override
        public String apply(LogEvent e) {
            return String.format("%s %s [%s] [%s] [%s]: %s\n",
                    format.underline("TEST(" + getTestMethod(e) + ")"),
                    e.getLocalTime(),
                    e.getThreadName(),
                    colorizedLevel(e),
                    format.bold(e.getLoggerName()),
                    messageFormatter.format(e.getMessage(), e.getArgumentArray()))
                    + exceptionFormatter.format(e.getThrowable());
        }

        private Object getTestMethod(LogEvent logEvent) {
            StackTraceElement[] stackTrace = logEvent.getStackTrace();

            // TODO: org.junit.runners.statements.RunBefores -> java.lang.reflect.Method -> not jdk.internal.reflect or java.reflect
            int junitRunnerPos = -1;
            for (int i = 0; i < stackTrace.length-1; i++) {
                StackTraceElement stackTraceElement = stackTrace[i];
                if (stackTraceElement.getClassName().equals("org.junit.runners.BlockJUnit4ClassRunner") || stackTraceElement.getClassName().equals("org.junit.runners.ParentRunner")) {
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
                if (!stackTraceElement.getClassName().startsWith("java.") &&!stackTraceElement.getClassName().startsWith("jdk.") && !stackTraceElement.getClassName().startsWith("sun.")) {
                    testMethod = i;
                    break;
                }
            }
            return testMethod != -1 ? getMethodRef(stackTrace[testMethod]) : null;
        }

        private Object getMethodRef(StackTraceElement callerLocation) {
            String className = callerLocation.getClassName();
            className = className.substring(className.lastIndexOf(".")+1);
            return className + "." + callerLocation.getMethodName() + "(" + callerLocation.getFileName() + ":" + callerLocation.getLineNumber() + ")";
        }

    }

    @Override
    protected List<String> getProfiles() {
        List<String> profiles = new ArrayList<>(super.getProfiles());
        profiles.add("test");
        return profiles;
    }

    @Override
    protected ConsoleLogEventObserver createConsoleLogEventObserver(Properties configuration) {
        return new ConsoleLogEventObserver(new ConsoleLogEventTestFormatter());
    }

    @Override
    protected Level getDefaultRootLevel() {
        return Level.WARN;
    }
}
