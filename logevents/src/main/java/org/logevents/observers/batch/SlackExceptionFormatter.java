package org.logevents.observers.batch;

import org.logevents.config.Configuration;
import org.logevents.formatting.CauseFirstExceptionFormatter;

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
