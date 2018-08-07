package org.logevents.util;

import java.util.Collection;
import java.util.Iterator;

public class CircularBuffer<T> implements Collection<T> {

    private final int capacity;

    private T[] buffer;

    private int size = 0;
    private int start = 0;

    @SuppressWarnings("unchecked")
    public CircularBuffer(int capacity) {
        this.capacity = capacity;
        this.buffer = (T[]) new Object[capacity];
    }

    public CircularBuffer() {
        this(200);
    }

    public T get(int index) {
        return buffer[(start + index) % capacity];
    }

    @Override
    public boolean add(T e) {
        if (size < capacity) {
            buffer[size++] = e;
        } else {
            buffer[start++] = e;
            start %= capacity;
        }
        return true;
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < size;
            }

            @Override
            public T next() {
                return get(index++);
            }
        };
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean contains(Object o) {
        for (T t : buffer) {
            if (t.equals(o)) return true;
        }
        return false;
    }

    @Override
    public Object[] toArray() {
        Object[] result = new Object[size()];
        return toArray(result);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <O> O[] toArray(O[] a) {
        int index = 0;
        for (T t : (Iterable<T>)this) {
            if (index >= a.length) break;
            a[index++] = (O) t;
        }
        return a;
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object object : c) {
            if (!contains(object)) return false;
        }
        return true;
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        c.forEach(o -> add(o));
        return true;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{size=" + size + ",capacity=" + capacity + ",start=" + start + "}";
    }

}
