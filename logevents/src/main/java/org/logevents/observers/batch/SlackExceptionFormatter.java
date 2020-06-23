package org.logevents.observers.batch;

import org.logevents.config.Configuration;
import org.logevents.formatting.CauseFirstExceptionFormatter;

public class SlackExceptionFormatter extends CauseFirstExceptionFormatter {

    public SlackExceptionFormatter(Configuration configuration) {
        super(configuration);
    }

    @Override
    protected void outputStackFrame(StackTraceElement frame, int ignored, String indent, StringBuilder builder) {
        String sourceLink = sourceCodeLookup.getSourceLink(frame);
        if (sourceLink == null) {
            builder.append(indent).append("\tat ").append(frame);
        } else {
            builder.append(indent).append("\tat ").append("<").append(sourceLink)
            .append("|").append(frame.getClassName()).append(".").append(frame.getMethodName())
            .append(" 🔗>");
        }
        if (ignored > 0) {
            builder.append(" ");
            outputIgnoredLineCount(ignored, builder);
        }
        builder.append(newLine());
    }
}
