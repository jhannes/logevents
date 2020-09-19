package org.logevents.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class DaemonThreadFactory implements ThreadFactory {
    private final ThreadFactory defaultFactory = Executors.defaultThreadFactory();
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String name;
    private final int priority;

    public DaemonThreadFactory(String name, int priority) {
        this.name = name;
        this.priority = priority;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread thread = defaultFactory.newThread(r);
        thread.setName(name + "-" + threadNumber.getAndIncrement());
        thread.setDaemon(true);
        thread.setPriority(priority);
        return thread;
    }
}
