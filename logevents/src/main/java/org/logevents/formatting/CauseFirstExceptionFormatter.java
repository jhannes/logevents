package org.logevents.formatting;

import org.logevents.config.Configuration;

import java.util.Map;

public class CauseFirstExceptionFormatter extends ExceptionFormatter {

    public CauseFirstExceptionFormatter(Map<String, String> properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public CauseFirstExceptionFormatter(Configuration configuration) {
        super(configuration);
    }

    @Override
    protected void outputException(Throwable ex, Throwable enclosing, String prefix, String indent, StringBuilder builder) {
        if (ex.getCause() == null) {
            outputExceptionHeader(ex, prefix, indent, builder);
        } else {
            outputException(ex.getCause(), ex, prefix, indent, builder);
            outputExceptionHeader(ex, "Wrapped by: ", indent, builder);
        }

        outputStack(ex, indent, enclosing, builder);

        for (Throwable suppressed : ex.getSuppressed()) {
            outputException(suppressed, ex, "Suppressed: ", indent + "\t", builder);
        }
    }

}
