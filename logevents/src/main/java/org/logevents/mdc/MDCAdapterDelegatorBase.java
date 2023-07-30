package org.logevents.mdc;

import org.slf4j.spi.MDCAdapter;

import java.util.Deque;
import java.util.Map;

public abstract class MDCAdapterDelegatorBase implements MDCAdapter {
    protected final MDCAdapter delegate;

    public MDCAdapterDelegatorBase(MDCAdapter delegate) {
        this.delegate = delegate;
    }

    @Override
    public void put(String s, String s1) {
        delegate.put(s, s1);
    }

    @Override
    public String get(String s) {
        return delegate.get(s);
    }

    @Override
    public void remove(String s) {
        delegate.remove(s);
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public Map<String, String> getCopyOfContextMap() {
        return delegate.getCopyOfContextMap();
    }

    @Override
    public void setContextMap(Map<String, String> map) {
        delegate.setContextMap(map);
    }

    @Override
    public void clearDequeByKey(String key) {
        delegate.clearDequeByKey(key);
    }

    @Override
    public void pushByKey(String key, String value) {
        delegate.pushByKey(key, value);
    }

    @Override
    public String popByKey(String key) {
        return delegate.popByKey(key);
    }

    @Override
    public Deque<String> getCopyOfDequeByKey(String key) {
        return delegate.getCopyOfDequeByKey(key);
    }
}
