package org.logevents.formatting;

public class CauseFirstExceptionFormatter extends ExceptionFormatter {

    @Override
    protected void outputException(Throwable ex, Throwable enclosing, Integer length, String prefix, String indent, StringBuilder builder) {
        if (ex.getCause() == null) {
            builder.append(indent).append(prefix).append(ex.toString()).append(newLine());
        } else {
            outputException(ex.getCause(), ex, length, prefix, indent, builder);
            builder.append(indent).append("Wrapped by: ").append(ex.toString()).append(newLine());
        }

        outputStack(ex, length, indent, enclosing, builder);

        for (Throwable suppressed : ex.getSuppressed()) {
            outputException(suppressed, ex, length, "Suppressed: ", indent + "\t", builder);
        }

    }

}
