package org.logevents.observers;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.extend.junit.LogEventRule;
import org.logevents.util.CircularBuffer;

import java.util.Collection;
import java.util.Iterator;

/**
 * Collects log events in an internal circular buffer. When the buffer is filled
 * the first messages in the buffer are dropped. This is useful for reporting
 * log events with a servlet or similar or for internal testing.
 * @see LogEventRule for more on testing.
 *
 * @author Johannes Brodwall
 */
public class CircularBufferLogEventObserver implements LogEventObserver, Collection<LogEvent> {

    @Override
    public boolean add(LogEvent e) {
        return circularBuffer.add(e);
    }

    private CircularBuffer<LogEvent> circularBuffer = new CircularBuffer<>();

    @Override
    public Iterator<LogEvent> iterator() {
        return circularBuffer.iterator();
    }

    @Override
    public int size() {
        return circularBuffer.size();
    }

    @Override
    public boolean isEmpty() {
        return circularBuffer.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return circularBuffer.contains(o);
    }

    @Override
    public Object[] toArray() {
        return circularBuffer.toArray();
    }

    @Override
    public <O> O[] toArray(O[] a) {
        return circularBuffer.toArray(a);
    }

    @Override
    public boolean remove(Object o) {
        return circularBuffer.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return circularBuffer.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends LogEvent> c) {
        return circularBuffer.addAll(c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return circularBuffer.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return circularBuffer.retainAll(c);
    }

    @Override
    public void clear() {
        circularBuffer.clear();
    }

    @Override
    public void logEvent(LogEvent logEvent) {
        logEvent.getCallerLocation();
        this.circularBuffer.add(logEvent);
    }

    public CircularBuffer<LogEvent> getEvents() {
        return circularBuffer;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{size=" + circularBuffer.size() + "}";
    }

    public String singleMessage() {
        if (circularBuffer.size() != 1) {
            throw new IllegalStateException("Expected 1 message, but was " + circularBuffer.size());
        }
        return circularBuffer.get(0).getMessage();
    }
}
