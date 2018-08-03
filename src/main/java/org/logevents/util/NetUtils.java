package org.logevents.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;

public class NetUtils {

    public static void postJson(URL url, String json) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("POST");
        connection.setDoInput(true);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.getOutputStream().write(json.getBytes());
        connection.getOutputStream().flush();

        int statusCode = connection.getResponseCode();
        if (statusCode >= 400) {
            throw new IOException("Failed to POST to " + url + ", status code: " + statusCode
                    + ": " + readAsString(connection.getErrorStream()));
        }
    }

    private static String readAsString(InputStream inputStream) throws IOException {
        StringBuilder builder = new StringBuilder();

        Reader reader = new BufferedReader(new InputStreamReader(inputStream));
        int c;
        while ((c = reader.read()) != -1) {
            builder.append((char)c);
        }

        return builder.toString();
    }

}
