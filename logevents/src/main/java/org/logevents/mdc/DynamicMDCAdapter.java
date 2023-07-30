package org.logevents.mdc;

import org.slf4j.MDC;
import org.slf4j.spi.MDCAdapter;

import java.io.Closeable;
import java.util.Map;
import java.util.function.Supplier;

public interface DynamicMDCAdapter extends MDCAdapter {

    DynamicMDCAdapter adapter = MDC.getMDCAdapter() instanceof DynamicMDCAdapter
            ? (DynamicMDCAdapter) MDC.getMDCAdapter()
            : new DynamicMDCBasicDelegator(MDC.getMDCAdapter());

    Map<String, String> getCopyOfStaticContextMap();

    Map<String, DynamicMDC> getCopyOfDynamicContext();

    Cleanup putDynamic(String key, Supplier<DynamicMDC> supplier);

    abstract class Cleanup implements Closeable {

        private boolean complete = true;

        public void close() {
            if (complete) {
                doCleanup();
            }
        }

        protected abstract void doCleanup();

        public Cleanup retainIfIncomplete() {
            this.complete = false;
            return this;
        }

        public void complete() {
            this.complete = true;
        }
    }
}
