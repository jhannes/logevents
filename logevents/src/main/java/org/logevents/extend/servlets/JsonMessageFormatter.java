package org.logevents.extend.servlets;

import org.logevents.formatting.AbstractMessageFormatter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonMessageFormatter extends AbstractMessageFormatter<List<Map<String, Object>>> {

    public List<Map<String, Object>> format(String messageFormat, Object[] args) {
        List<Map<String, Object>> result = new ArrayList<>();
        format(result, messageFormat, args);
        return result;
    }

    @Override
    protected void outputArgument(List<Map<String, Object>> result, Object arg) {
        HashMap<String, Object> messageFragment = new HashMap<>();
        messageFragment.put("text", toString(arg));
        messageFragment.put("type", "argument");
        result.add(messageFragment);
    }

    @Override
    protected void outputConstant(List<Map<String, Object>> result, CharSequence source, int start, int end) {
        HashMap<String, Object> messageFragment = new HashMap<>();
        messageFragment.put("text", source.subSequence(start, end));
        messageFragment.put("type", "constant");
        result.add(messageFragment);
    }
}
