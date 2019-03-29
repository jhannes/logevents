package org.logevents.observers.batch;

import org.slf4j.event.Level;

import java.util.Map;

public interface JsonLogEventsBatchFormatter {
    Map<String, Object> createMessage(LogEventBatch batch);


    static String emojiiForLevel(Level level) {
        switch (level) {
            case ERROR: return "\uD83D\uDED1"; // 🛑
            case WARN: return "\u26a0\ufe0f"; // ⚠️
            case INFO: return "\u2139\ufe0f"; // ℹ️
            case DEBUG: return "\uD83D\uDD0E"; // 🔎
            case TRACE: return "\ud83d\udd2c"; // 🔬
            default: return level.name();
        }
    }

}
