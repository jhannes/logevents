package org.logevents.formatting;

public class ExceptionFormatter {

    private static String newLine() {
        return System.getProperty("line.separator");
    }

    public String format(Throwable ex, Integer length) {
        StringBuilder builder = new StringBuilder();

        outputException(ex, length, builder);

        return builder.toString();
    }

    private void outputException(Throwable ex, Integer length, StringBuilder builder) {
        builder.append(ex.toString()).append(newLine());
        outputStackTrace(ex, length, builder);
        while (ex.getCause() != null) {
            ex = ex.getCause();
            builder.append("Caused by: ").append(ex.toString()).append(newLine());
            outputStackTrace(ex, length, builder);
        }
    }

    private void outputStackTrace(Throwable ex, Integer length, StringBuilder builder) {
        int lines = 0;
        for (StackTraceElement frame : ex.getStackTrace()) {
            if (++lines > length) break;
            builder.append("\tat ").append(frame).append(newLine());
        }
    }

}
