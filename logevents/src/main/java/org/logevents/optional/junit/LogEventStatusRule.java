package org.logevents.optional.junit;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.logevents.status.LogEventStatus;
import org.logevents.status.StatusEvent;

public class LogEventStatusRule implements TestRule {

    private StatusEvent.StatusLevel level;

    public LogEventStatusRule(StatusEvent.StatusLevel level) {
        this.level = level;
    }

    public LogEventStatusRule() {
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                StatusEvent.StatusLevel oldThreshold = LogEventStatus.getInstance().getThreshold(null);
                if (level != null) {
                    LogEventStatus.getInstance().setThreshold(level);
                }
                try {
                    base.evaluate();
                } finally {
                    LogEventStatus.getInstance().setThreshold(oldThreshold);
                }
            }
        };
    }

    public void setStatusLevel(StatusEvent.StatusLevel level) {
        System.setProperty("logevents.status", level.name());
    }
}
