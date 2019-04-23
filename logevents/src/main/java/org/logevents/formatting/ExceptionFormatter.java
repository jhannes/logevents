package org.logevents.formatting;

import java.util.Properties;

/**
 * Presents the exception of a Log Event as a nicely formatted textual
 * representation. Supports filtering stack traces by package and
 * (for {@link org.logevents.observers.batch.SlackExceptionFormatter})
 * including a link to the corresponding source code.
 * <p>
 * Example configuration
 *
 * <pre>
 * observer.x.formatter.exceptionFormatter={@link CauseFirstExceptionFormatter}
 * observer.x.formatter.exceptionFormatter.packageFilter=sun.www, com.example.uninteresting
 * </pre>
 *
 * You can also specify package filters for all observers:
 * <pre>
 * observer.*.packageFilter=sun.www, com.example.uninteresting
 * </pre>
 *
 * @author Johannes Brodwall
 */
public class ExceptionFormatter extends AbstractExceptionFormatter {
    public ExceptionFormatter(Properties properties, String prefix) {
        super(properties, prefix);
    }

    public ExceptionFormatter() {
    }

    public String format(Throwable ex) {
        if (ex == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        outputException(ex, null, "", "", builder);
        return builder.toString();
    }

    protected void outputException(Throwable ex, Throwable enclosing, String prefix, String indent, StringBuilder builder) {
        outputExceptionHeader(ex, prefix, indent, builder);

        outputStack(ex, indent, enclosing, builder);

        for (Throwable suppressedException : ex.getSuppressed()) {
            outputException(suppressedException, ex, "Suppressed: ", indent + "\t", builder);
        }

        Throwable cause = ex.getCause();
        if (cause != null) {
            outputException(cause, ex, "Caused by: ", indent, builder);
        }
    }

    protected void outputExceptionHeader(Throwable ex, String prefix, String indent, StringBuilder builder) {
        builder.append(indent).append(prefix).append(ex.toString()).append(newLine());
    }

    protected void outputStack(Throwable ex, String indent, Throwable enclosing, StringBuilder builder) {
        int uniquePrefix = uniquePrefix(ex, enclosing);
        StackTraceElement[] stackTrace = ex.getStackTrace();
        int ignored = 0;
        int actualLines = 0;
        for (int i = 0; i < uniquePrefix && actualLines < maxLength; i++) {
            if (isIgnored(stackTrace[i])) {
                ignored++;
            } else {
                outputStackFrame(stackTrace[i], ignored, indent, builder);
                actualLines++;
                ignored = 0;
            }
        }
        if (ignored > 0) {
            outputIgnoredLineCount(ignored, indent, builder).append(newLine());
        }
        if (uniquePrefix < stackTrace.length && uniquePrefix < maxLength) {
            outputIgnoredCommonLines(stackTrace.length - uniquePrefix, indent, builder);
        }
    }

    protected void outputStackFrame(StackTraceElement frame, int ignored, String indent, StringBuilder builder) {
        builder.append(indent).append("\tat ").append(frame);
        if (ignored > 0) {
            builder.append(" ");
            outputIgnoredLineCount(ignored, indent, builder);
        }
        if (includePackagingData) {
            builder.append(" ").append(getPackagingData(frame));
        }
        builder.append(newLine());
    }

    protected void outputIgnoredCommonLines(int commonLines, String indent, StringBuilder builder) {
        builder.append(indent).append("\t... ").append(commonLines).append(" more").append(newLine());
    }

    protected StringBuilder outputIgnoredLineCount(int ignored, String indent, StringBuilder builder) {
        return builder.append(indent).append("[").append(ignored).append(" skipped]");
    }
}
