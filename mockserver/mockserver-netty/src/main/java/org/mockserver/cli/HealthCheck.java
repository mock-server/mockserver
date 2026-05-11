package org.mockserver.cli;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class HealthCheck {

    private static final int TIMEOUT_MILLIS = 3000;
    private static final int DEFAULT_PORT = 1080;

    public static void main(String... args) {
        System.exit(check(resolvePort()) ? 0 : 1);
    }

    static boolean check(int port) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL("http://localhost:" + port + "/mockserver/status").openConnection();
            connection.setRequestMethod("PUT");
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            return connection.getResponseCode() == 200;
        } catch (IOException e) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    static int resolvePort() {
        String port = System.getenv("MOCKSERVER_SERVER_PORT");
        if (port == null || port.isEmpty()) {
            port = System.getenv("SERVER_PORT");
        }
        if (port != null && !port.isEmpty()) {
            try {
                String firstPort = port.contains(",") ? port.substring(0, port.indexOf(',')) : port;
                return Integer.parseInt(firstPort.trim());
            } catch (NumberFormatException e) {
                return DEFAULT_PORT;
            }
        }
        return DEFAULT_PORT;
    }

}
