package org.logevents.util;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class JsonUtilTest {

    @Test
    public void shouldOutputObject() {
        assertJsonOutput("{}", new HashMap<>());
    }

    @Test
    public void shouldOutputSimpleFields() throws IOException {
        Map<String, Object> jsonObject = new LinkedHashMap<>();
        jsonObject.put("firstName", "Darth");
        jsonObject.put("lastName", "Vader");
        jsonObject.put("age", 29L);
        jsonObject.put("sith", true);
        jsonObject.put("weakness", null);
        assertJsonOutput("{\"firstName\": \"Darth\",\"lastName\": \"Vader\",\"age\": 29,\"sith\": true,\"weakness\": null}", jsonObject);

        String s = new JsonUtil("", "").toJson(jsonObject);
        assertEquals(JsonParser.parse(s), jsonObject);
    }

    @Test
    public void shouldEscapeStrings() {
        Map<String, Object> jsonObject = new LinkedHashMap<>();
        jsonObject.put("string", "newline\n, carriage return \r, tab\t, bell\b, quote\", backslash \\");
        assertJsonOutput("{\"string\": \"newline\\n, carriage return \\r, tab\\t, bell\\b, quote\\\", backslash \\\\\"}", jsonObject);
    }

    @Test
    public void shouldOutputLists() throws IOException {
        Map<String, Object> jsonObject = new LinkedHashMap<>();
        jsonObject.put("numbers", Arrays.asList(1L, 2L, 3L));
        assertJsonOutput("{\"numbers\": [1,2,3]}", jsonObject);

        String s = new JsonUtil("", "").toJson(jsonObject);
        assertEquals(JsonParser.parse(s), new HashMap<>(jsonObject));
    }

    @Test
    public void shouldIndentOutput() {
        Map<String, Object> jsonObject = new LinkedHashMap<>();
        jsonObject.put("numbers", Arrays.asList(1, 2));
        assertEquals("{\n  \"numbers\": [\n    1,\n    2\n  ]\n}",
                JsonUtil.toIndentedJson(jsonObject));
    }

    @Test(expected=IllegalArgumentException.class)
    public void shouldThrowOnUnknownType() {
        Map<String, Object> jsonObject = new LinkedHashMap<>();
        jsonObject.put("object", new Object());
        JsonUtil.toIndentedJson(jsonObject);
    }

    @Test
    public void shouldParseEscapes() throws IOException {
        String escapedText = "\"quote=\\\" backslash=\\\\ slash=\\/ bell=\\b feed=\\f newline=\\n tab=\\t\"";
        String parsedText = (String) JsonParser.parse(escapedText);
        assertEquals(
                "quote=\" backslash=\\ slash=/ bell=\b feed=\f newline=\n tab=\t",
                parsedText
        );
        assertEquals(escapedText,
                new JsonUtil("", "").toJson(parsedText));
    }

    @Test
    public void shouldParseNumbers() {
        Map<String, Object> jsonObject = new HashMap<>();
        jsonObject.put("longNumber", 123L);
        jsonObject.put("floatNumber", 123.25);
        Map<String, Object> stringifiedObject = JsonParser.parseObject(JsonUtil.toIndentedJson(jsonObject));

        assertEquals(jsonObject, stringifiedObject);
    }

    private void assertJsonOutput(String expected, Map<String, Object> jsonObject) {
        assertEquals(expected, new JsonUtil("", "").toJson(jsonObject));
    }

}
