package org.logevents.config;

import org.logevents.LogEvent;
import org.logevents.formatting.ConsoleLogEventFormatter;
import org.logevents.observers.ConsoleLogEventObserver;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class DefaultTestLogEventConfigurator extends DefaultLogEventConfigurator {

    private static class ConsoleLogEventTestFormatter extends ConsoleLogEventFormatter {

        public ConsoleLogEventTestFormatter(Properties properties, String prefix) {
            this(new Configuration(properties, prefix));
        }

        public ConsoleLogEventTestFormatter(Configuration configuration) {
            List<String> defaultPackageFilter = Arrays.asList(
                    "org.junit.runners",
                    "org.junit.internal.runners",
                    "jdk.internal.reflect",
                    "com.intellij.junit4",
                    "com.intellij.rt.execution.junit"
            );
            List<String> packageFilter = new ArrayList<>(configuration.getPackageFilter());
            packageFilter.addAll(defaultPackageFilter);

            exceptionFormatter.setPackageFilter(packageFilter);
        }

        @Override
        public String apply(LogEvent e) {
            return String.format("%s %s [%s] [%s] [%s]: %s\n",
                    format.underline("TEST(" + getTestMethodName(e.getStackTrace()) + ")"),
                    e.getLocalTime(),
                    e.getThreadName(),
                    colorizedLevel(e),
                    format.bold(e.getLoggerName()),
                    e.getMessage(messageFormatter))
                    + exceptionFormatter.format(e.getThrowable());
        }

    }

    @Override
    protected ConsoleLogEventObserver createConsoleLogEventObserver(Configuration configuration) {
        return new ConsoleLogEventObserver(new ConsoleLogEventTestFormatter(configuration));
    }

    @Override
    protected Level getDefaultRootLevel() {
        return Level.WARN;
    }

    public static String getTestMethodName(StackTraceElement[] stackTrace) {
        return getMethodRef(getTestMethod(stackTrace));
    }

    public static StackTraceElement getTestMethod(StackTraceElement[] stackTrace) {
        List<String> junitExecutors = Arrays.asList(
                "org.junit.runners.BlockJUnit4ClassRunner",
                "org.junit.runners.ParentRunner",
                "org.junit.platform.engine.support.hierarchical.NodeTestTask"
        );

        // TODO: org.junit.runners.statements.RunBefores -> java.lang.reflect.Method -> not jdk.internal.reflect or java.reflect
        int junitRunnerPos = -1;
        for (int i = 0; i < stackTrace.length-1; i++) {
            StackTraceElement stackTraceElement = stackTrace[i];
            if (junitExecutors.contains(stackTraceElement.getClassName())) {
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

        int testMethodIndex = -1;
        for (int i = invokePos; i >= 0; i--) {
            StackTraceElement stackTraceElement = stackTrace[i];
            if (!stackTraceElement.getClassName().startsWith("java.") &&!stackTraceElement.getClassName().startsWith("jdk.") && !stackTraceElement.getClassName().startsWith("sun.")) {
                testMethodIndex = i;
                break;
            }
        }
        return testMethodIndex != -1 ? stackTrace[testMethodIndex] : null;
    }

    private static String getMethodRef(StackTraceElement callerLocation) {
        if (callerLocation == null) {
            return null;
        }
        String className = callerLocation.getClassName();
        className = className.substring(className.lastIndexOf(".")+1);
        return className + "." + callerLocation.getMethodName() + "(" + callerLocation.getFileName() + ":" + callerLocation.getLineNumber() + ")";
    }
}
