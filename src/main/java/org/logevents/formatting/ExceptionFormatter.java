package org.logevents.formatting;

public class ExceptionFormatter {

    private String[] packageFilter = new String[0];

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
        int ignored = 0;
        int actualLines = 0;
        for (int i = 0; i < uniquePrefix && actualLines < maxLength; i++) {
            if (isIgnored(stackTrace[i])) {
                ignored++;
            } else {
                outputStackFrame(stackTrace[i], indent, builder, ignored);
                actualLines++;
                ignored = 0;
            }
        }
        if (ignored > 0) {
            outputIgnoredLineCount(ignored, indent, builder).append(newLine());
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

    private StringBuilder outputIgnoredLineCount(int ignored, String indent, StringBuilder builder) {
        return builder.append(indent).append("[").append(ignored).append(" skipped]");
    }

    private boolean isIgnored(StackTraceElement frame) {
        for (String filter : this.packageFilter) {
            if (frame.getClassName().startsWith(filter)) {
                return true;
            }
        }
        return false;
    }

    protected void outputStackFrame(StackTraceElement frame, String indent, StringBuilder builder, int ignored) {
        builder.append(indent).append("\tat ").append(frame);
        if (ignored > 0) {
            builder.append(" ");
            outputIgnoredLineCount(ignored, indent, builder);
        }
        builder.append(newLine());
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

    public void setPackageFilter(String[] packageFilter) {
        this.packageFilter = packageFilter;
    }

}
