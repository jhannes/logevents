package org.logevents.util;

import java.util.List;
import java.util.Map;

/**
 * A utility class for minimal JSON functionality. Prints a
 * Map&lt;String, Object&gt; as JSON, supporting Map, Iterable, String,
 * Number and Boolean.
 *
 * This class is stand alone and only depends on Java. It can be copied-and-
 * pasted into existing project.
 *
 * This class is independently shared under the BSD license 2.0.
 *
 * @author johannes@brodwall.com (Johannes Brodwall)
 *
 */
public class JsonUtil {

    private final String newline;
    private final String indent;

    public JsonUtil(String indent, String newline) {
        this.newline = newline;
        this.indent = indent;
    }

    public static String toIndentedJson(Map<String, ? extends Object> jsonObject) {
        return new JsonUtil("  ", "\n").toJson(jsonObject);
    }

    public String toJson(Map<String, ? extends Object> jsonObject) {
        StringBuilder result = new StringBuilder();
        objectToJson(jsonObject, result, "");
        return result.toString();
    }

    private void toJson(Map<String, Object> json, StringBuilder result, String currentIndent) {
        result.append("{").append(newline);
        boolean first = true;
        for (Map.Entry<String, Object> entry : json.entrySet()) {
            if (!first) result.append(",").append(newline);
            result.append(currentIndent + indent + "\"").append(jsonEscape(entry.getKey())).append("\": ");
            objectToJson(entry.getValue(), result, currentIndent + indent);
            first = false;
        }
        result.append(newline).append(currentIndent).append("}");
    }

    private void toJson(Iterable<? extends Object> value, StringBuilder result, String currentIndent) {
        result.append("[").append(newline);
        boolean first = true;
        for (Object entry : value) {
            if (!first) result.append(",").append(newline);
            result.append(currentIndent + indent);
            objectToJson(entry, result, currentIndent + indent);
            first = false;
        }
        result.append(newline).append(currentIndent).append("]");
    }

    @SuppressWarnings("unchecked")
    private void objectToJson(Object value, StringBuilder result, String currentIndent) {
        if (value instanceof Map) {
            toJson((Map<String,Object>)value, result, currentIndent);
        } else if (value instanceof List) {
            toJson((Iterable<? extends Object>)value, result, currentIndent);
        } else if (value instanceof CharSequence) {
            toJson((CharSequence)value, result);
        } else if (value instanceof Number) {
            result.append(value.toString());
        } else if (value instanceof Boolean) {
            result.append(value.toString());
        } else if (value == null) {
            result.append("null");
        } else {
            throw new IllegalArgumentException("Unsupported JSON element " + value.getClass().getName());
        }
    }

    public static Object getField(Map<String, Object> object, String fieldName) {
        if (!object.containsKey(fieldName)) {
            throw new IllegalArgumentException("Unknown field <" + fieldName + "> in " + object.keySet());
        }
        return object.get(fieldName);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getObject(List<?> list, int index) {
        return (Map<String, Object>) list.get(index);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getObject(Map<String, Object> object, String fieldName) {
        return (Map<String, Object>) getField(object, fieldName);
    }

    @SuppressWarnings("unchecked")
    public static List<Object> getList(Map<String, Object> object, String fieldName) {
        return (List<Object>) getField(object, fieldName);
    }

    private static void toJson(CharSequence value, StringBuilder result) {
        result.append("\"").append(jsonEscape(value)).append("\"");
    }

    private static String jsonEscape(CharSequence key) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            switch (key.charAt(i)) {
            case '\\':
                result.append("\\");
                break;
            case '\n':
                result.append("\\n");
                break;
            case '\r':
                result.append("\\r");
                break;
            case '\t':
                result.append("\\t");
                break;
            case '\b':
                result.append("\\b");
                break;
            case '\"':
                result.append("\\\"");
                break;
            default:
                result.append(key.charAt(i));
                break;
            }
        }
        return result.toString();
    }

}
