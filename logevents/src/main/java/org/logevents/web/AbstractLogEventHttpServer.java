package org.logevents.web;

import com.sun.net.httpserver.HttpExchange;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpCookie;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class AbstractLogEventHttpServer {
    public static Map<String, String[]> parseParameters(String query) throws UnsupportedEncodingException {
        if (query == null) {
            return new HashMap<>();
        }
        Map<String, String[]> result = new HashMap<>();
        for (String parameterString : query.split("&")) {
            int equalsPos = parameterString.indexOf('=');
            if (equalsPos > 0) {
                String paramName = parameterString.substring(0, equalsPos);
                String paramValue = URLDecoder.decode(parameterString.substring(equalsPos+1), "ISO-8859-1");
                String[] existingValue = result.get(paramName);
                if (existingValue != null) {
                    String[] newValue = new String[existingValue.length+1];
                    System.arraycopy(existingValue, 0, newValue, 0, existingValue.length);
                    newValue[newValue.length-1] = paramValue;
                    result.put(paramName, newValue);
                } else {
                    result.put(paramName, new String[] { paramValue });
                }
            }
        }
        return result;
    }

    protected Optional<String> getCookie(HttpExchange exchange, String cookieName) {
        return Optional.ofNullable(exchange.getRequestHeaders().getFirst("Cookie"))
                .map(HttpCookie::parse)
                .flatMap(cookies -> cookies.stream()
                        .filter(c -> c.getName().equals(cookieName))
                        .map(HttpCookie::getValue)
                        .findFirst());
    }

    protected void sendResponse(HttpExchange exchange, String text, int responseCode) throws IOException {
        exchange.sendResponseHeaders(responseCode, text.getBytes().length);//response code and length
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(text.getBytes());
        }
    }

    public String getResourceFileAsString(String fileName) {
        InputStream is = getClass().getResourceAsStream(fileName);
        if (is != null) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            return reader.lines().collect(Collectors.joining(System.lineSeparator()));
        }
        return null;
    }
}
