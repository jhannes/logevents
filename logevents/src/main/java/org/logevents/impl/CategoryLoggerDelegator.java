package org.logevents.impl;

import org.logevents.observers.CompositeLogEventObserver;

import java.util.Objects;

class CategoryLoggerDelegator extends LoggerDelegator {

    private LoggerDelegator parentLogger;

    CategoryLoggerDelegator(String name, LoggerDelegator parentLogger) {
        super(name);
        this.parentLogger = Objects.requireNonNull(parentLogger, "parentLogger" + " should not be null");
        refresh();
    }

    @Override
    public void reset() {
        super.reset();
        this.effectiveThreshold = null;
    }

    @Override
    public void refresh() {
        this.effectiveThreshold = this.levelThreshold;
        if (effectiveThreshold == null) {
            this.effectiveThreshold = parentLogger.effectiveThreshold;
        }
        observer = inheritParentObserver
                ? CompositeLogEventObserver.combine(parentLogger.observer, ownObserver)
                : ownObserver;

        refreshEventGenerators(effectiveThreshold, observer);
    }

    @Override
    public boolean hasParent(LoggerDelegator parent) {
        return this.parentLogger == parent;
    }
}
