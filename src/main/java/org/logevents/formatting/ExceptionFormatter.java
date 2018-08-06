package org.logevents.formatting;

public class ExceptionFormatter {

    private static String newLine() {
        return System.getProperty("line.separator");
    }

    public String format(Throwable ex, Integer length) {
        StringBuilder builder = new StringBuilder();

        outputException(ex, length, "", "", new StackTraceElement[0], builder);

        return builder.toString();
    }

    private void outputException(Throwable ex, Integer maxLength, String prefix, String indent, StackTraceElement[] enclosingTrace, StringBuilder builder) {
        builder.append(indent).append(prefix).append(ex.toString()).append(newLine());

        StackTraceElement[] stackTrace = ex.getStackTrace();
        int commonStackStart = findCommonStart(enclosingTrace, stackTrace);

        int uniquePrefix = stackTrace.length - commonStackStart;
        for (int i = 0; i < uniquePrefix && i < maxLength; i++) {
            outputStackFrame(stackTrace[i], indent, builder);
        }
        if (uniquePrefix < stackTrace.length && uniquePrefix < maxLength) {
            builder.append(indent).append("\t... ").append(commonStackStart).append(" more").append(newLine());
        }

        for (Throwable suppressedException : ex.getSuppressed()) {
            outputException(suppressedException, maxLength, "Suppressed: ", indent + "\t", ex.getStackTrace(), builder);
        }

        Throwable cause = ex.getCause();
        if (cause != null) {
            outputException(cause, maxLength, "Caused by: ", indent, ex.getStackTrace(), builder);
        }
    }

    protected StringBuilder outputStackFrame(StackTraceElement frame, String indent, StringBuilder builder) {
        return builder.append(indent).append("\tat ").append(frame).append(newLine());
    }

    private int findCommonStart(StackTraceElement[] enclosingTrace, StackTraceElement[] trace) {
        int i = 0;
        while (i < enclosingTrace.length && i < trace.length) {
            if (!trace[trace.length-1-i].equals(enclosingTrace[enclosingTrace.length - 1 - i])) {
                return i;
            }
            i++;
        }
        return i;
    }

}
