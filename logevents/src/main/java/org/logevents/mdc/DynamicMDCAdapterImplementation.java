package org.logevents.mdc;

import org.slf4j.helpers.BasicMDCAdapter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class DynamicMDCAdapterImplementation extends BasicMDCAdapter implements DynamicMDCAdapter {

    private final InheritableThreadLocal<Map<String, Supplier<DynamicMDC>>> dynamicMdcCollections = new InheritableThreadLocal<Map<String, Supplier<DynamicMDC>>>() {
        @Override
        protected Map<String, Supplier<DynamicMDC>> childValue(Map<String, Supplier<DynamicMDC>> parentValue) {
            if (parentValue == null) {
                return null;
            }
            return new HashMap<>(parentValue);
        }
    };

    @Override
    public Cleanup putDynamic(String key, Supplier<DynamicMDC> supplier) {
        if (dynamicMdcCollections.get() == null) {
            dynamicMdcCollections.set(new HashMap<>());
        }
        dynamicMdcCollections.get().put(key, supplier);
        return new Cleanup() {
            @Override
            protected void doCleanup() {
                if (dynamicMdcCollections.get() != null) {
                    dynamicMdcCollections.get().remove(key);
                }
            }
        };
    }

    @Override
    public Map<String, String> getCopyOfContextMap() {
        Map<String, String> result = super.getCopyOfContextMap();
        if (dynamicMdcCollections.get() == null) {
            return result;
        }

        HashMap<String, String> dynamicContext = new HashMap<>();
        DynamicMDC.collect(dynamicContext, getCopyOfDynamicContext());
        if (result == null) {
            return dynamicContext.isEmpty() ? null : dynamicContext;
        }
        result.putAll(dynamicContext);
        return result;
    }

    @Override
    public Map<String, String> getCopyOfStaticContextMap() {
        Map<String, String> result = super.getCopyOfContextMap();
        return result != null ? result : Collections.emptyMap();
    }

    @Override
    public Map<String, DynamicMDC> getCopyOfDynamicContext() {
        if (dynamicMdcCollections.get() == null) {
            return null;
        }
        HashMap<String, DynamicMDC> result = new HashMap<>();
        for (Map.Entry<String, Supplier<DynamicMDC>> entry : dynamicMdcCollections.get().entrySet()) {
            DynamicMDC value = entry.getValue().get();
            if (value != null) {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    @Override
    public void setContextMap(Map<String, String> contextMap) {
        super.setContextMap(contextMap);
        dynamicMdcCollections.remove();
    }

    @Override
    public void clear() {
        super.clear();
        Map<String, Supplier<DynamicMDC>> collections = this.dynamicMdcCollections.get();
        if (collections != null) {
            collections.clear();
            this.dynamicMdcCollections.remove();
        }
    }
}
