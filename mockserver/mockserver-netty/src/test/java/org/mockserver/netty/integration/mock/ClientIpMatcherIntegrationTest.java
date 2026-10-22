package org.mockserver.netty.integration.mock;

import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.netty.MockServer;
import org.mockserver.verify.VerificationTimes;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * A client IP matcher, registered through {@link MockServerClient} and as raw JSON, discriminates requests by the
 * TCP peer address of a real socket: the address bytes the Netty transport captures, not anything in the request.
 */
public class ClientIpMatcherIntegrationTest {

    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();

    private static MockServer mockServer;
    private static MockServerClient client;

    @BeforeClass
    public static void startServer() {
        mockServer = new MockServer();
        client = new MockServerClient("localhost", mockServer.getLocalPort());
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServer);
    }

    @Before
    public void reset() {
        client.reset();
    }

    @Test
    public void shouldMatchOnTheClientAddressRegisteredThroughTheClient() throws Exception {
        client.when(request().withPath("/ip").withClientIp("10.0.0.0/8")).respond(response().withBody("private"));
        client.when(request().withPath("/ip").withClientIp("127.0.0.0/8")).respond(response().withBody("loopback"));

        HttpResponse<String> response = get("127.0.0.1", "/ip", null);
        assertThat(response.body(), response.statusCode(), is(200));
        assertThat(response.body(), is("loopback"));
    }

    @Test
    public void shouldUseThePeerAddressNotXForwardedFor() throws Exception {
        client.when(request().withPath("/ip").withClientIp("203.0.113.0/24")).respond(response().withBody("forwarded"));

        assertThat(get("127.0.0.1", "/ip", "203.0.113.9").statusCode(), is(404));
    }

    @Test
    public void shouldMatchANegatedClientIpRegisteredAsRawJson() throws Exception {
        putExpectation("{\"httpRequest\":{\"path\":\"/ip\",\"clientIp\":\"!127.0.0.0/8\"},\"httpResponse\":{\"body\":\"remote\"}}");
        assertThat(get("127.0.0.1", "/ip", null).statusCode(), is(404));

        putExpectation("{\"httpRequest\":{\"path\":\"/ip\",\"clientIp\":{\"not\":true,\"value\":\"10.0.0.0/8\"}},\"httpResponse\":{\"body\":\"not private\"}}");
        HttpResponse<String> response = get("127.0.0.1", "/ip", null);
        assertThat(response.statusCode(), is(200));
        assertThat(response.body(), is("not private"));
    }

    @Test
    public void shouldRejectAnInvalidClientIp() throws Exception {
        HttpResponse<String> response = putExpectation("{\"httpRequest\":{\"path\":\"/ip\",\"clientIp\":\"localhost\"},\"httpResponse\":{\"body\":\"x\"}}");
        assertThat(response.body(), response.statusCode(), is(400));
        assertThat(response.body(), containsString("is not an IP address or CIDR range"));
    }

    @Test
    public void shouldVerifyAndRetrieveRecordedRequestsByClientIp() throws Exception {
        get("127.0.0.1", "/recorded", null);

        client.verify(request().withPath("/recorded").withClientIp("127.0.0.1"), VerificationTimes.exactly(1));
        client.verify(request().withPath("/recorded").withClientIp("10.0.0.0/8"), VerificationTimes.never());
        assertThat(client.retrieveRecordedRequests(request().withPath("/recorded").withClientIp("127.0.0.0/8")).length, is(1));
        assertThat(client.retrieveRecordedRequests(request().withPath("/recorded").withClientIp("::1")).length, is(0));
    }

    @Test
    public void shouldMatchAnIpv6Client() throws Exception {
        Assume.assumeTrue("IPv6 loopback is not available", reachable("::1"));
        client.when(request().withPath("/ip").withClientIp("127.0.0.0/8")).respond(response().withBody("ipv4"));
        client.when(request().withPath("/ip").withClientIp("::1/128")).respond(response().withBody("ipv6"));

        HttpResponse<String> response = get("[::1]", "/ip", null);
        assertThat(response.body(), response.statusCode(), is(200));
        assertThat(response.body(), is("ipv6"));
    }

    private static boolean reachable(String host) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, mockServer.getLocalPort()), 2000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static HttpResponse<String> get(String host, String path, String forwardedFor) throws Exception {
        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder(URI.create("http://" + host + ":" + mockServer.getLocalPort() + path))
            .timeout(Duration.ofSeconds(10))
            .GET();
        if (forwardedFor != null) {
            builder.header("X-Forwarded-For", forwardedFor);
        }
        return HTTP.send(builder.build(), BodyHandlers.ofString());
    }

    private static HttpResponse<String> putExpectation(String json) throws Exception {
        return HTTP.send(java.net.http.HttpRequest.newBuilder(URI.create("http://localhost:" + mockServer.getLocalPort() + "/mockserver/expectation"))
            .timeout(Duration.ofSeconds(10))
            .PUT(BodyPublishers.ofString(json))
            .build(), BodyHandlers.ofString());
    }
}
