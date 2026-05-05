package org.mockserver.examples.mockserver;

import org.apache.commons.io.IOUtils;
import org.mockserver.integration.ClientAndServer;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class RedirectBetweenMultiMockServerInstances {
    public static void main(String[] args) throws IOException {

        // 1070: forwards -> 1080
        // 1080: redirects -> 1090
        // 1090: returns: 200: [redirected_body]

        new ClientAndServer(1070)
            .when(
                request()
            )
            .forward(
                forward()
                    .withHost("localhost")
                    .withPort(1080)
            );

        new ClientAndServer(1080)
            .when(request())
            .respond(
                response()
                    .withStatusCode(302)
                    .withHeader("Location", "http://localhost:1090/redirected")
            );

        new ClientAndServer(1090)
            .when(
                request()
                    .withPath("/redirected")
            )
            .respond(
                response()
                    .withBody("redirected_body", StandardCharsets.UTF_8)
            );

        HttpURLConnection con = (HttpURLConnection) new URL("http://localhost:1070/redirected").openConnection();
        con.setRequestMethod("GET");
        if (con.getErrorStream() != null) {
            System.out.println("error response " + con.getResponseCode() + ": " + IOUtils.readLines(con.getErrorStream(), StandardCharsets.UTF_8));
        } else if (con.getResponseCode() != 404) {
            System.out.println("success response " + con.getResponseCode() + ": " + IOUtils.readLines(con.getInputStream(), StandardCharsets.UTF_8));
        } else {
            System.out.println("not found response");
        }
        con.disconnect();
    }
}
