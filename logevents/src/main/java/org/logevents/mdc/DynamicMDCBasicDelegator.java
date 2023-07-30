package org.logevents.mdc;

import org.slf4j.spi.MDCAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class DynamicMDCBasicDelegator extends MDCAdapterDelegatorBase implements DynamicMDCAdapter {

    public DynamicMDCBasicDelegator(MDCAdapter delegate) {
        super(delegate);
    }

    @Override
    public Cleanup putDynamic(String key, Supplier<DynamicMDC> supplier) {
        DynamicMDC values = supplier.get();
        if (values != null) {
            List<String> keys = new ArrayList<>();
            for (Map.Entry<String, String> mdcEntry : values.entrySet()) {
                put(mdcEntry.getKey(), mdcEntry.getValue());
                keys.add(mdcEntry.getKey());
            }
            return new Cleanup() {
                @Override
                protected void doCleanup() {
                    keys.forEach(s -> remove(s));
                }
            };
        }
        return new Cleanup() {
            @Override
            protected void doCleanup() {
                remove(key);
            }
        };
    }

    @Override
    public Map<String, String> getCopyOfStaticContextMap() {
        Map<String, String> result = getCopyOfContextMap();
        return result != null ? result : Collections.emptyMap();
    }

    @Override
    public Map<String, DynamicMDC> getCopyOfDynamicContext() {
        return null;
    }
}
