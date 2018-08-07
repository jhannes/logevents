package org.logevents.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class JsonUtilTest {

    @Test
    public void shouldOutputObject() {
        assertJsonOutput("{}", new HashMap<>());
    }

    @Test
    public void shouldOutputSimpleFields() {
        Map<String, Object> jsonObject = new LinkedHashMap<>();
        jsonObject.put("firstName", "Darth");
        jsonObject.put("lastName", "Vader");
        jsonObject.put("age", 29);
        jsonObject.put("sith", true);
        jsonObject.put("weakness", null);
        assertJsonOutput("{\"firstName\": \"Darth\",\"lastName\": \"Vader\",\"age\": 29,\"sith\": true,\"weakness\": null}", jsonObject);
    }

    @Test
    public void shouldEscapedStrings() {
        Map<String, Object> jsonObject = new LinkedHashMap<>();
        jsonObject.put("string", "newline\n, carriage return \r, tab\t, bell\b, quote\", backslash \\");
        assertJsonOutput("{\"string\": \"newline\\n, carriage return \\r, tab\\t, bell\\b, quote\\\", backslash \\\"}", jsonObject);
    }

    @Test
    public void shouldOutputLists() {
        Map<String, Object> jsonObject = new LinkedHashMap<>();
        jsonObject.put("numbers", Arrays.asList(1, 2, 3));
        assertJsonOutput("{\"numbers\": [1,2,3]}", jsonObject);
    }

    @Test
    public void shouldIndentOutput() {
        Map<String, Object> jsonObject = new LinkedHashMap<>();
        jsonObject.put("numbers", Arrays.asList(1, 2));
        Assert.assertEquals("{\n  \"numbers\": [\n    1,\n    2\n  ]\n}",
                JsonUtil.toIndentedJson(jsonObject));
    }

    @Test(expected=IllegalArgumentException.class)
    public void shouldThrowOnUnknownType() {
        Map<String, Object> jsonObject = new LinkedHashMap<>();
        jsonObject.put("object", new Object());
        JsonUtil.toIndentedJson(jsonObject);
    }

    private void assertJsonOutput(String expected, Map<String, Object> jsonObject) {
        Assert.assertEquals(expected, new JsonUtil("", "").toJson(jsonObject));
    }

}
