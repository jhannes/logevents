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

        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("content-type", "application/json");
        connection.getOutputStream().write(json.getBytes());
        connection.getOutputStream().flush();

        int responseCode = connection.getResponseCode();
        // TODO Deal with errors in Observers
        System.out.println("Response code " + responseCode);
        System.out.println(readAsString(connection.getInputStream()));
    }

    private static String readAsString(InputStream inputStream) throws IOException {
        StringBuilder builder = new StringBuilder();

        Reader reader = new BufferedReader(new InputStreamReader(inputStream));
        int c;
        while ((c = reader.read()) != -1) {
            builder.append(c);
        }

        return builder.toString();
    }

}
