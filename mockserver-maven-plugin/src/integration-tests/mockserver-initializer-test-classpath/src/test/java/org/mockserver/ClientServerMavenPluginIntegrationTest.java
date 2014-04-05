package org.mockserver;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.http.ApacheHttpClient;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockserver.configuration.SystemProperties.bufferSize;
import static org.mockserver.configuration.SystemProperties.maxTimeout;

/**
 * @author jamesdbloom
 */
public class ClientServerMavenPluginIntegrationTest {

    private final static int port = 8080;
    private final static int serverSecurePort = 8082;
    private final ApacheHttpClient apacheHttpClient;

    public ClientServerMavenPluginIntegrationTest() {
        bufferSize(1024);
        maxTimeout(TimeUnit.SECONDS.toMillis(10));
        apacheHttpClient = new ApacheHttpClient();
    }

    @Test
    public void clientCanCallServer() {
        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("test_initializer_response_body"),
                makeRequest(
                        new HttpRequest()
                                .withMethod("POST")
                                .withURL("http://localhost:" + port + "/test_initializer_path")
                                .withBody("test_initializer_request_body")
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("test_initializer_response_body"),
                makeRequest(
                        new HttpRequest()
                                .withMethod("POST")
                                .withURL("https://localhost:" + serverSecurePort + "/test_initializer_path")
                                .withBody("test_initializer_request_body")
                )
        );
    }

    protected HttpResponse makeRequest(HttpRequest httpRequest) {
        HttpResponse httpResponse = apacheHttpClient.sendRequest(httpRequest);
        List<Header> headers = new ArrayList<Header>();
        for (Header header : httpResponse.getHeaders()) {
            if (!(header.getName().equals("Server") || header.getName().equals("Expires") || header.getName().equals("Date") || header.getName().equals("Connection"))) {
                headers.add(header);
            }
        }
        httpResponse.withHeaders(headers);
        return httpResponse;
    }

}
