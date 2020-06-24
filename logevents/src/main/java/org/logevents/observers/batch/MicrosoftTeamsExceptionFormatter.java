package org.logevents.observers.batch;

import org.logevents.formatting.CauseFirstExceptionFormatter;

import java.util.Properties;

public class MicrosoftTeamsExceptionFormatter extends CauseFirstExceptionFormatter {
    public MicrosoftTeamsExceptionFormatter(Properties properties, String prefix) {
        super(properties, prefix);
    }

    @Override
    protected String initialIndent() {
        return "* ";
    }

    @Override
    protected String increaseIndent(String indent) {
        return "  " + indent;
    }

    @Override
    protected void formatFrame(StringBuilder builder, StackTraceElement frame) {
        String sourceLink = sourceCodeLookup.getSourceLink(frame);
        if (sourceLink == null) {
            builder.append(htmlEscape(frame));
        } else {
            builder.append("[").append(htmlEscape(frame)).append("](").append(sourceLink).append(")");
        }
    }

    private String htmlEscape(Object frame) {
        return frame.toString()
                .replaceAll("&", "&amp;")
                .replaceAll("<", "&lt;")
                .replaceAll(">", "&gt;");
    }
}
