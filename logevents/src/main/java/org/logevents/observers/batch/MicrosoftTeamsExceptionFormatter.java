package org.logevents.observers.batch;

import org.logevents.formatting.CauseFirstExceptionFormatter;

import java.util.Properties;

public class MicrosoftTeamsExceptionFormatter extends CauseFirstExceptionFormatter {
    public MicrosoftTeamsExceptionFormatter(Properties properties, String prefix) {
        super(properties, prefix);
    }

    @Override
    protected String initialIndent() {
        return "* ";
    }

    @Override
    protected String increaseIndent(String indent) {
        return "  " + indent;
    }
}
