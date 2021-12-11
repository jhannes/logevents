package org.logevents.optional.junit;

public interface LogEventMatcher {
    void apply(LogEventMatcherContext expect);
}
