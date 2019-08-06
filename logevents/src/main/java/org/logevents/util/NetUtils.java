package org.logevents.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;
import java.util.stream.Collectors;

public class NetUtils {

    public static HttpURLConnection post(URL url, String contentBody, String contentType) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoInput(true);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", contentType);
        connection.getOutputStream().write(contentBody.getBytes());
        connection.getOutputStream().flush();
        return connection;
    }

    public static String postJson(URL url, String json) throws IOException {
        HttpURLConnection connection = post(url, json, "application/json");

        int statusCode = connection.getResponseCode();
        if (statusCode >= 400) {
            throw new IOException("Failed to POST to " + url + ", status code: " + statusCode
                    + ": " + readAsString(connection.getErrorStream()));
        }
        return readAsString(connection.getInputStream());
    }

    public static Map<String, Object> postFormForJson(URL url, Map<String, String> formPayload) throws IOException {
        String payload = formPayload.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue()))
                .collect(Collectors.joining("&"));

        HttpURLConnection connection = post(url, payload, "application/x-www-form-urlencoded");
        return JsonParser.parseObject(connection);
    }

    public static String readAsString(InputStream inputStream) throws IOException {
        StringBuilder builder = new StringBuilder();

        Reader reader = new BufferedReader(new InputStreamReader(inputStream));
        int c;
        while ((c = reader.read()) != -1) {
            builder.append((char)c);
        }

        return builder.toString();
    }

}
