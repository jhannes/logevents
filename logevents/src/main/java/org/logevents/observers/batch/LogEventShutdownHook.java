package org.logevents.observers.batch;

import org.logevents.status.LogEventStatus;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;

public class LogEventShutdownHook extends Thread {

    public LogEventShutdownHook() {
        setName(getClass().getSimpleName());
    }

    private Set<WeakReference<Runnable>> actions = new HashSet<>();

    public void addAction(Runnable action) {
        LogEventStatus.getInstance().addTrace(this, "Adding shutdown action");
        actions.add(new WeakReference<>(action));
    }

    @Override
    public void run() {
        LogEventStatus.getInstance().addConfig(this, "Flushing up to " + actions.size() + " observers");
        int count = 0;
        for (WeakReference<Runnable> action : actions) {
            Runnable runnable = action.get();
            if (runnable != null) {
                runnable.run();
                count++;
            }
        }
        LogEventStatus.getInstance().addDebug(this, "Flushed " + count + " observers");
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{}";
    }
}

