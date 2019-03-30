package org.logevents.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;

import static org.logevents.util.NetUtils.readAsString;

public class JsonParser {

    public static Object parse(Reader reader) throws IOException {
        JsonParser jsonParser = new JsonParser(reader);
        return jsonParser.parseValue();
    }

    public static Object parse(String input) throws IOException {
        return parse(new StringReader(input));
    }

    public static Object parse(InputStream input) throws IOException {
        return parse(new InputStreamReader(input));
    }

    public static Object parse(URL url) throws IOException {
        return parse(url.openConnection());
    }

    public static Object parse(URLConnection connection) throws IOException {
        HttpURLConnection httpConnection = (HttpURLConnection) connection;
        if (httpConnection.getResponseCode() < 400) {
            try (InputStream input = connection.getInputStream()) {
                return parse(input);
            }
        } else {
            throw new IOException("Failed to POST to " + connection.getURL() + ", status code: " + httpConnection.getResponseCode()
                    + ": " + readAsString(httpConnection.getErrorStream()));
        }
    }

    public static Object parseFromBase64encodedString(String base64encodedJson) throws IllegalArgumentException, IOException {
        return parse(new String(Base64.getUrlDecoder().decode(base64encodedJson)));
    }

    private Reader reader;
    private char lastRead;
    private boolean finished;

    private JsonParser(Reader reader) throws IOException {
        this.reader = reader;
        readNext();
    }

    private void readNext() throws IOException {
        int read = reader.read();
        if (read == -1) {
            finished = true;
            return;
        }
        this.lastRead = (char) read;
    }

    private Object parseValue() throws IOException {
        int c;
        while (!finished) {
            switch (lastRead) {
                case '{':
                    return parseObject();
                case '[':
                    return parseArray();
                case '"':
                    return parseStringValue();
                case 't':
                case 'f':
                    return parseBooleanValue();
                case 'n':
                    return parseNullValue();
            }
            if (lastRead == '-' || Character.isDigit(lastRead)) {
                return parseNumberValue();
            }
            if (!(Character.isWhitespace(lastRead))) {
                throw new IllegalStateException("Unexpected character '" + lastRead + "'");
            }
            readNext();
        }
        return null;
    }

    private Number parseNumberValue() throws IOException {
        StringBuilder val = new StringBuilder();
        boolean isDouble = false;
        while (!finished && (Character.isDigit(lastRead) || ".eE-".contains("" + lastRead))) {
            isDouble = isDouble || ".eE".contains("" + lastRead);
            val.append(lastRead);
            readNext();
        }
        if (!finished && (!(Character.isSpaceChar(lastRead) || "}],".contains("" + lastRead))) && (!"\n\r\t".contains("" + lastRead))) {
            throw new IllegalStateException("Illegal value '" + val + lastRead + "'");
        }
        if (val.length() > 20) {
            return new BigDecimal(val.toString());
        }
        if (isDouble) {
            return Double.parseDouble(val.toString());
        }
        return Long.parseLong(val.toString());
    }

    private Object parseNullValue() throws IOException {
        expectValue("null");
        return null;
    }

    private boolean parseBooleanValue() throws IOException {
        boolean isTrue = (lastRead == 't');
        String expect = isTrue ? "true" : "false";
        expectValue(expect);
        return isTrue;
    }

    private void expectValue(String value) throws IOException {
        StringBuilder res = new StringBuilder();
        for (int i=0;i<value.length() && !finished;i++) {
            res.append(lastRead);
            readNext();
        }
        if (!res.toString().equals(value)) {
            throw new IllegalStateException(String.format("Unexpected value %s",res.toString()));
        }
    }

    private List<Object> parseArray() throws IOException {
        List<Object> jsonArray = new ArrayList<>();
        while (lastRead != ']') {
            readNext();
            if (lastRead == ']') {
                break;
            }
            jsonArray.add(parseValue());
            readSpaceUntil("Expected , or ] in array", ']', ',');
        }
        readNext();
        return jsonArray;
    }

    private String parseStringValue() throws IOException {
        readNext();
        return readText();
    }

    private HashMap<String,Object> parseObject() throws IOException {
        HashMap<String,Object> jsonObject = new HashMap<>();
        while (lastRead != '}') {
            readSpaceUntil("JSON object not closed. Expected }", '}', '"');
            if (lastRead == '}') {
                readNext();
                return jsonObject;
            }
            readNext();
            String key = readText();
            readSpaceUntil("Expected value for property " + key, ':');
            readNext();
            if (finished) {
                throw new IllegalStateException("Expected value for property " + key);
            }
            jsonObject.put(key, parseValue());
            readSpaceUntil("JSON object not closed. Expected }", ',', '}');
        }
        readNext();
        return jsonObject;
    }

    private String readText() throws IOException {
        StringBuilder res = new StringBuilder();
        while (!(finished || lastRead == '"')) {
            if (lastRead == '\\') {
                readNext();
                if (finished) {
                    throw new IllegalStateException("JSON string not closed. Ended in escape sequence");
                }
                switch (lastRead) {
                    case '"':
                        res.append("\"");
                        break;
                    case '\\':
                        res.append("\\");
                        break;
                    case '/':
                        res.append("/");
                        break;
                    case 'b':
                        res.append("\b");
                        break;
                    case 'f':
                        res.append("\f");
                        break;
                    case 'n':
                        res.append("\n");
                        break;
                    case 't':
                        res.append("\t");
                        break;
                    case 'u':
                        res.append(readUnicodeValue());
                        break;
                }
            } else {
                res.append(lastRead);
            }
            readNext();
        }
        if (finished) {
            throw new IllegalStateException("JSON string not closed. Expected \"");
        }
        return res.toString();
    }

    private String readUnicodeValue() throws IOException {
        StringBuilder code = new StringBuilder();
        for (int i=0;i<4;i++) {
            readNext();
            if (finished) {
                throw new IllegalStateException("JSON string not closed. Ended in escape sequence");
            }
            code.append(lastRead);
        }

        try {
            int unicode = Integer.parseInt(code.toString(), 16);
            return Character.toString((char)unicode);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Illegal unicode sequence " + code);
        }
    }

    private void readSpaceUntil(String errormessage, Character... readUntil) throws IOException {
        List<Character> until = Arrays.asList(readUntil);
        if (until.contains(lastRead)) {
            return;
        }
        readNext();
        while (!(finished || until.contains(lastRead))) {
            if (!Character.isWhitespace(lastRead)) {
                throw new IllegalStateException(errormessage);
            }
            readNext();
        }
        if (finished) {
            throw new IllegalStateException(errormessage);
        }
    }
}
