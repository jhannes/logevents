package org.logevents.observers.batch;

import org.logevents.LogEvent;
import org.logevents.config.Configuration;
import org.logevents.formatting.ExceptionFormatter;

import java.util.Map;

public class MicrosoftTeamsExceptionFormatter extends ExceptionFormatter {
    private final int frameClassLength;

    public MicrosoftTeamsExceptionFormatter(Map<String, String> properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public MicrosoftTeamsExceptionFormatter(Configuration configuration) {
        packageFilter = configuration.getPackageFilter();
        includePackagingData = configuration.getBoolean("includePackagingData");
        maxLength = configuration.optionalInt("maxLength").orElse(Integer.MAX_VALUE);
        configureSourceCode(configuration);
        this.frameClassLength = configuration.optionalInt("frameClassLength").orElse(60);
        configuration.checkForUnknownFields();
    }

    @Override
    protected String increaseIndent(String indent) {
        return indent.isEmpty() ? "* " : "  " + indent;
    }

    @Override
    public String format(Throwable ex) {
        if (ex == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        String indent = "";

        outputStack(ex, indent, null, builder);

        for (Throwable suppressedException : ex.getSuppressed()) {
            outputException(suppressedException, ex, "Suppressed: ", increaseIndent(indent), builder);
        }

        Throwable cause = ex.getCause();
        if (cause != null) {
            outputException(cause, ex, "Caused by: ", increaseIndent(indent), builder);
        }
        return builder.toString();
    }

    @Override
    protected void formatFrame(StringBuilder builder, StackTraceElement frame) {
        String sourceLink = sourceCodeLookup.getSourceLink(frame);
        if (sourceLink == null) {
            builder.append(getFrameText(frame));
        } else {
            builder.append("[").append(getFrameText(frame)).append("](").append(sourceLink).append(")");
        }
    }

    private String getFrameText(StackTraceElement frame) {
        return htmlEscape(
                LogEvent.getAbbreviatedClassName(frame.getClassName(), frameClassLength)
                + "." + frame.getMethodName() + "(" + frame.getFileName() + ":" + frame.getLineNumber() + ")"
        );
    }

    private String htmlEscape(Object frame) {
        return frame.toString()
                .replaceAll("&", "&amp;")
                .replaceAll("<", "&lt;")
                .replaceAll(">", "&gt;");
    }
}
