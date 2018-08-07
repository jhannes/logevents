package org.logevents.observers.batch;

import java.util.Properties;

import org.logevents.formatting.CauseFirstExceptionFormatter;

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
            builder.append("<").append(sourceLink)
            .append("|").append(frame.getClassName()).append(".").append(frame.getMethodName())
            .append(">");
        }
        if (ignored > 0) {
            builder.append(" ");
            outputIgnoredLineCount(ignored, indent, builder);
        }
        builder.append(newLine());
    }

    protected String getSourceLink(StackTraceElement stackTraceElement) {
        if (stackTraceElement.getClassName().startsWith("org.logevents")) {
            return "https://github.com/jhannes/logevents/tree/master/src/main/java/"
                    + stackTraceElement.getClassName().replaceAll("\\.", "/") + ".java#L" + stackTraceElement.getLineNumber();
        }
        return null;
    }


}
