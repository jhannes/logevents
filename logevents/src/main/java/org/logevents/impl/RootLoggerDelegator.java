package org.logevents.impl;

import org.logevents.observers.NullLogEventObserver;
import org.slf4j.event.Level;

class RootLoggerDelegator extends LoggerDelegator {

    RootLoggerDelegator() {
        super("ROOT");
        ownObserver = new NullLogEventObserver();
        levelThreshold = Level.INFO;
        refresh();
    }

    @Override
    public void reset() {
        super.reset();
        levelThreshold = Level.INFO;
    }

    @Override
    public void refresh() {
        this.effectiveThreshold = this.levelThreshold;
        this.observer = this.ownObserver;
        refreshEventGenerators(effectiveThreshold, observer);
    }

    @Override
    public boolean hasParent(LoggerDelegator parent) {
        return false;
    }
}
