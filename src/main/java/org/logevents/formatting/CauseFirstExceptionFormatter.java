package org.logevents.formatting;

import java.util.Properties;

public class CauseFirstExceptionFormatter extends ExceptionFormatter {

    public CauseFirstExceptionFormatter(Properties properties, String prefix) {
        super(properties, prefix);
    }

    @Override
    protected void outputException(Throwable ex, Throwable enclosing, Integer length, String prefix, String indent, StringBuilder builder) {
        if (ex.getCause() == null) {
            outputExceptionHeader(ex, prefix, indent, builder);
        } else {
            outputException(ex.getCause(), ex, length, prefix, indent, builder);
            outputExceptionHeader(ex, "Wrapped by: ", indent, builder);
        }

        outputStack(ex, length, indent, enclosing, builder);

        for (Throwable suppressed : ex.getSuppressed()) {
            outputException(suppressed, ex, length, "Suppressed: ", indent + "\t", builder);
        }
    }

}
