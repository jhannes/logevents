package org.logevents.util;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class NetUtils {

    public static void postJson(URL url, String json) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("POST");
        connection.setRequestProperty("content-type", "application/json");
        connection.setDoOutput(true);
        connection.getOutputStream().write(json.getBytes());

        connection.getResponseCode();
        // TODO Deal with errors in Observers
    }

}
