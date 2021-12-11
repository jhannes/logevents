package org.logevents.observers.slack;

import org.logevents.config.Configuration;
import org.logevents.formatters.exceptions.CauseFirstExceptionFormatter;

public class SlackExceptionFormatter extends CauseFirstExceptionFormatter {

    public SlackExceptionFormatter(Configuration configuration) {
        super(configuration);
    }

    @Override
    protected void formatFrame(StringBuilder builder, StackTraceElement frame) {
        String sourceLink = sourceCodeLookup.getSourceLink(frame);
        if (sourceLink == null) {
            builder.append(frame);
        } else {
            builder.append("<").append(sourceLink)
                    .append("|").append(frame.getClassName()).append(".").append(frame.getMethodName())
                    .append(" 🔗>");
        }
    }
}
