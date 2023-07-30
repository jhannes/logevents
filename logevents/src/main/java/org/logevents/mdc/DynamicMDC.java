package org.logevents.mdc;

import org.logevents.config.MdcFilter;
import org.logevents.formatters.exceptions.ExceptionFormatter;
import org.logevents.util.JsonUtil;

import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

public interface DynamicMDC {


    static Map<String, DynamicMDC> getCopyOfDynamicContext() {
        return DynamicMDCAdapter.adapter.getCopyOfDynamicContext();
    }

    static Map<String, String> getCopyOfStaticContext() {
        return DynamicMDCAdapter.adapter.getCopyOfStaticContextMap();
    }

    static DynamicMDCAdapter.Cleanup putDynamic(String key, Supplier<DynamicMDC> supplier) {
        return DynamicMDCAdapter.adapter.putDynamic(key, supplier);
    }

    static DynamicMDCAdapter.Cleanup putMap(String key, Supplier<Map<String, String>> supplier) {
        return putDynamic(key, () -> ofMap(supplier));
    }

    static DynamicMDCAdapter.Cleanup putEntry(String key, Supplier<String> supplier) {
        return putMap(key, () -> Collections.singletonMap(key, supplier.get()));
    }

    static DynamicMDC ofMap(Supplier<Map<String, String>> supplier) {
        Map<String, String> map = supplier.get();
        return map::entrySet;
    }

    Iterable<? extends Map.Entry<String, String>> entrySet();

    @SuppressWarnings("unchecked")
    default void populateJsonEvent(Map<String, Object> jsonPayload, MdcFilter mdcFilter, ExceptionFormatter exceptionFormatter) {
        Map<String, Object> mdc = (Map<String, Object>) JsonUtil.getField(jsonPayload, "mdc");
        populate(mdc, mdcFilter);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    default void populate(Map mdc, MdcFilter mdcFilter) {
        for (Map.Entry<String, String> mdcEntry : entrySet()) {
            if (mdcFilter.isKeyIncluded(mdcEntry.getKey())) {
                mdc.put(mdcEntry.getKey(), mdcEntry.getValue());
            }
        }
    }

    static void collect(Map<String, String> result, Map<String, DynamicMDC> mdcContextCollections) {
        for (DynamicMDC collection : mdcContextCollections.values()) {
            collection.populate(result, MdcFilter.INCLUDE_ALL);
        }
    }
}
