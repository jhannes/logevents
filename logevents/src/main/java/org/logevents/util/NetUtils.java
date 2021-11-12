package org.logevents.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;
import java.util.stream.Collectors;

public class NetUtils {
    public static final String NO_AUTHORIZATION_HEADER = null;


    public static HttpURLConnection post(URL url, String contentBody, String contentType) throws IOException {
        return post(url, contentBody, contentType, Proxy.NO_PROXY, NO_AUTHORIZATION_HEADER);
    }

    public static HttpURLConnection post(URL url, String contentBody, String contentType, Proxy proxy, String authorizationHeaderValue) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection(proxy);
        connection.setRequestMethod("POST");
        connection.setDoInput(true);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", contentType);
        if (authorizationHeaderValue != null && !authorizationHeaderValue.trim().isEmpty()) {
            connection.setRequestProperty("Authorization", authorizationHeaderValue);
        }
        connection.getOutputStream().write(contentBody.getBytes());
        connection.getOutputStream().flush();
        return connection;
    }

    public static String postJson(URL url, String json, Proxy proxy) throws IOException {
        return postJson(url, json, proxy, NO_AUTHORIZATION_HEADER);
    }

    public static String postJson(URL url, String json, Proxy proxy, String authorizationHeaderValue) throws IOException {
        HttpURLConnection connection = post(url, json, "application/json", proxy, authorizationHeaderValue);

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
