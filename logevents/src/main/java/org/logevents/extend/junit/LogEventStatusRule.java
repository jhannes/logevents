package org.logevents.extend.junit;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.logevents.status.LogEventStatus;
import org.logevents.status.StatusEvent;

public class LogEventStatusRule implements TestRule {

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                StatusEvent.StatusLevel oldThreshold = LogEventStatus.getInstance().getThreshold(null);
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
