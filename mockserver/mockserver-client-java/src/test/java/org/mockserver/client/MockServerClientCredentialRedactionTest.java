package org.mockserver.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThrows;
import static org.mockserver.configuration.Configuration.configuration;

/**
 * A failed control-plane call's exception describes the request it sent, but never with the credentials it sent.
 */
public class MockServerClientCredentialRedactionTest {

    @Test
    public void shouldNotPutBasicCredentialsInTheExceptionOfAFailedRequest() throws Exception {
        HttpServer failing = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        failing.createContext("/", exchange -> {
            byte[] body = "failed".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        failing.start();
        MockServerClient client = new MockServerClient(configuration(), "127.0.0.1", failing.getAddress().getPort())
            .withControlPlaneBasicAuthentication("admin", "pa55-near-miss");
        try {
            ClientException failure = assertThrows(ClientException.class, client::reset);

            assertThat(failure.getMessage(), containsString("/mockserver/reset"));
            assertThat(failure.getMessage(), not(containsString("pa55-near-miss")));
            assertThat(failure.getMessage(), not(containsString(Base64.getEncoder().encodeToString("admin:pa55-near-miss".getBytes(StandardCharsets.UTF_8)))));
        } finally {
            client.releaseEventLoops();
            failing.stop(0);
        }
    }
}
