package org.logevents.optional.junit;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.logevents.status.LogEventStatus;
import org.logevents.status.StatusEvent;
import org.logevents.status.StatusEvent.StatusLevel;

public class LogEventStatusExtension implements BeforeEachCallback, AfterEachCallback {

    private StatusEvent.StatusLevel level;
    private StatusLevel oldThreshold;

    public LogEventStatusExtension(StatusEvent.StatusLevel level) {
        this.level = level;
    }

    public LogEventStatusExtension() {
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        oldThreshold = LogEventStatus.getInstance().getThreshold(null);
        if (level != null) {
            LogEventStatus.getInstance().setThreshold(level);
        }
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        LogEventStatus.getInstance().setThreshold(oldThreshold);
    }

    public void setStatusLevel(StatusEvent.StatusLevel level) {
        System.setProperty("logevents.status", level.name());
    }
}
