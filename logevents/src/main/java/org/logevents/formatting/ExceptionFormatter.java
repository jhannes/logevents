package org.logevents.formatting;

import org.logevents.config.Configuration;

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
 * observer.x.formatter.sourceCode.1.package=org.logevents
 * observer.x.formatter.sourceCode.1.github=jhannes/logevents
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
    public ExceptionFormatter() {
    }

    public ExceptionFormatter(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public ExceptionFormatter(Configuration configuration) {
        super(configuration);
    }

    protected String increaseIndent(String indent) {
        return indent + "\t";
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
            outputExceptionHeader(suppressedException, "Suppressed: " + prefix, increaseIndent(indent), builder);
            outputStack(suppressedException, increaseIndent(indent), ex, builder);
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
        int ignoredLineCount = 0;
        int actualLines = 0;
        for (int i = 0; i < uniquePrefix && actualLines < maxLength; i++) {
            if (i > 0 && isIgnored(stackTrace[i])) {
                ignoredLineCount++;
            } else {
                outputStackFrame(stackTrace[i], ignoredLineCount, indent, builder);
                actualLines++;
                ignoredLineCount = 0;
            }
        }
        if (ignoredLineCount > 0) {
            builder.append(increaseIndent(indent));
            outputIgnoredFrameCount(ignoredLineCount, builder).append(newLine());
        }
        if (uniquePrefix < stackTrace.length && uniquePrefix < maxLength) {
            outputIgnoredCommonLines(stackTrace.length - uniquePrefix, indent, builder);
        }
    }

    public void outputStackFrame(StackTraceElement frame, int ignored, String indent, StringBuilder builder) {
        builder.append(increaseIndent(indent)).append("at ");
        formatFrame(builder, frame);
        if (ignored > 0) {
            builder.append(" ");
            outputIgnoredFrameCount(ignored, builder);
        }
        if (includePackagingData) {
            builder.append(" ").append(getPackagingData(frame));
        }
        builder.append(newLine());
    }

    protected void formatFrame(StringBuilder builder, StackTraceElement frame) {
        builder.append(frame);
    }

    protected void outputIgnoredCommonLines(int commonLines, String indent, StringBuilder builder) {
        builder.append(increaseIndent(indent)).append("... ").append(commonLines).append(" more").append(newLine());
    }

    protected StringBuilder outputIgnoredFrameCount(int ignored, StringBuilder builder) {
        return builder.append("[").append(ignored).append(" skipped]");
    }
}
