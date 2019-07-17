package org.logevents.extend.junit;

public interface LogEventMatcher {
    void apply(LogEventMatcherContext expect);
}
