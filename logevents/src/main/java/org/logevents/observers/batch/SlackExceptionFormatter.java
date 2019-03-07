package org.logevents.observers.batch;

import org.logevents.formatting.CauseFirstExceptionFormatter;

import java.util.Properties;

public class SlackExceptionFormatter extends CauseFirstExceptionFormatter {

    public SlackExceptionFormatter() {
        super(new Properties(), "");
    }

    @Override
    protected void outputStackFrame(StackTraceElement frame, int ignored, String indent, StringBuilder builder) {
        String sourceLink = getSourceLink(frame);
        if (sourceLink == null) {
            builder.append(indent).append("\tat ").append(frame);
        } else {
            builder.append(indent).append("\tat ").append("<").append(sourceLink)
            .append("|").append(frame.getClassName()).append(".").append(frame.getMethodName())
            .append(" 🔗>");
        }
        if (ignored > 0) {
            builder.append(" ");
            outputIgnoredLineCount(ignored, indent, builder);
        }
        builder.append(newLine());
    }
}
