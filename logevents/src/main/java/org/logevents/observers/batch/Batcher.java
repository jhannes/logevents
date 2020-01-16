package org.logevents.observers.batch;

import java.util.List;

/**
 * Collects objects of type T according to defined rules and flushes them to a Consumer on
 * appropriate intervals. Used to limit the noise on "hot" channels such as email or slack
 */
public interface Batcher<T> extends Runnable {
    void accept(T o);

    List<T> getCurrentBatch();
}
