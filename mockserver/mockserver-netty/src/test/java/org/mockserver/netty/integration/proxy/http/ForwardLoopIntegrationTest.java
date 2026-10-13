package org.mockserver.netty.integration.proxy.http;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.Configuration;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.action.ExpectationForwardCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpTemplate;
import org.mockserver.model.SocketAddress;
import org.mockserver.netty.MockServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpTemplate.template;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * A forward whose target leads back to the same MockServer stops after {@code forwardLoopMaxHops} forwards with a
 * {@code 508}, while forwards to, and through, other MockServer instances still work. Every loop here is real: each
 * hop is a new connection back to a running server.
 */
public class ForwardLoopIntegrationTest {

    private static final String HEADER = "x-mockserver-forwarded-by";
    private static final String LOOP_DETECTED = "MockServer did not forward this request because it has already forwarded it 10 times, so the forward's target leads back to this MockServer, where the request matched a forward again: check the forward's host and port, or raise forwardLoopMaxHops if the repeated forwarding is intended";

    private static MockServer first;
    private static MockServer second;
    private static MockServer third;
    private static MockServer detectionOff;
    private static MockServerClient firstClient;
    private static MockServerClient secondClient;
    private static MockServerClient thirdClient;
    private static MockServerClient detectionOffClient;

    @BeforeClass
    public static void startServers() {
        // the default forwardLoopMaxHops, so these tests also pin it at 10; WARN so the refusal is logged
        // whatever mockserver.testLogLevel is
        first = new MockServer(atWarn(), 0);
        second = new MockServer(atWarn(), 0);
        third = new MockServer(atWarn(), 0);
        detectionOff = new MockServer(atWarn().forwardLoopMaxHops(0), 0);
        FreshRequestCallback.port = first.getLocalPort();
        firstClient = new MockServerClient("127.0.0.1", first.getLocalPort());
        secondClient = new MockServerClient("127.0.0.1", second.getLocalPort());
        thirdClient = new MockServerClient("127.0.0.1", third.getLocalPort());
        detectionOffClient = new MockServerClient("127.0.0.1", detectionOff.getLocalPort());
    }

    private static Configuration atWarn() {
        return configuration().logLevel("WARN");
    }

    @AfterClass
    public static void stopServers() {
        for (MockServerClient client : Arrays.asList(firstClient, secondClient, thirdClient, detectionOffClient)) {
            stopQuietly(client);
        }
        for (MockServer server : Arrays.asList(first, second, third, detectionOff)) {
            stopQuietly(server);
        }
    }

    @Before
    public void reset() {
        for (MockServerClient client : Arrays.asList(firstClient, secondClient, thirdClient, detectionOffClient)) {
            client.reset();
        }
    }

    @Test
    public void shouldAnswerAForwardBackToItselfWith508AfterTenForwards() throws Exception {
        firstClient.when(request().withPath("/loop")).forward(forward().withHost("127.0.0.1").withPort(first.getLocalPort()));

        Response response = send(first, "/loop");

        assertThat(response.toString(), response.status, is(508));
        assertThat(response.body, is(LOOP_DETECTED));
        HttpRequest[] arrivals = firstClient.retrieveRecordedRequests(request().withPath("/loop"));
        assertThat("the client's request and ten forwards", arrivals, arrayWithSize(11));
        assertThat(arrivals[10].getHeader(HEADER), hasSize(10));
        assertThat(firstClient.retrieveLogMessages(request().withPath("/loop")), containsString("returning 508 Loop Detected in place of forwarding request"));
    }

    @Test
    public void shouldLetARequestBeForwardedBackToItselfTenTimes() throws Exception {
        firstClient.when(request().withPath("/ten-hops"), Times.exactly(10)).forward(forward().withHost("127.0.0.1").withPort(first.getLocalPort()));
        firstClient.when(request().withPath("/ten-hops")).respond(response().withBody("answered after ten forwards"));

        Response response = send(first, "/ten-hops");

        assertThat(response.toString(), response.status, is(200));
        assertThat(response.body, is("answered after ten forwards"));
    }

    @Test
    public void shouldStopATemplateForwardThatBuildsANewRequestEachHop() throws Exception {
        String templatedRequest = "{ 'method': 'GET', 'path': '/template-loop', 'headers': { 'Host': [ '127.0.0.1:" + first.getLocalPort() + "' ] } }";
        firstClient.when(request().withPath("/template-loop")).forward(template(HttpTemplate.TemplateType.VELOCITY, templatedRequest));

        Response response = send(first, "/template-loop");

        assertThat(response.toString(), response.status, is(508));
        assertThat(response.body, is(LOOP_DETECTED));
    }

    @Test
    public void shouldStopAnOverriddenForwardThatLoops() throws Exception {
        firstClient.when(request().withPath("/override-loop")).forward(forwardOverriddenRequest(
            request().withPath("/override-loop").withSocketAddress("127.0.0.1", first.getLocalPort(), SocketAddress.Scheme.HTTP)
        ));

        Response response = send(first, "/override-loop");

        assertThat(response.toString(), response.status, is(508));
        assertThat(response.body, is(LOOP_DETECTED));
    }

    @Test
    public void shouldStopAClassCallbackForwardThatBuildsANewRequestEachHop() throws Exception {
        firstClient.when(request().withPath("/callback-loop")).forward(callback(FreshRequestCallback.class.getName()));

        Response response = send(first, "/callback-loop");

        assertThat(response.toString(), response.status, is(508));
        assertThat(response.body, is(LOOP_DETECTED));
    }

    @Test
    public void shouldNotSendTheLoopPreventionValueOfTheUnmatchedProxyPathToAForwardsUpstream() throws Exception {
        secondClient.when(request().withPath("/proxied")).respond(response().withBody("proxied"));
        secondClient.when(request().withPath("/forwarded")).respond(response().withBody("forwarded"));
        firstClient.when(request().withPath("/forwarded")).forward(forward().withHost("127.0.0.1").withPort(second.getLocalPort()));

        Response proxied = exchange(first, "GET http://127.0.0.1:" + second.getLocalPort() + "/proxied HTTP/1.1\r\nHost: 127.0.0.1:" + second.getLocalPort() + "\r\nConnection: close\r\n\r\n");
        Response forwarded = send(first, "/forwarded");

        assertThat(proxied.toString(), proxied.status, is(200));
        assertThat(forwarded.toString(), forwarded.status, is(200));
        List<String> loopPreventionValues = secondClient.retrieveRecordedRequests(request().withPath("/proxied"))[0].getHeader("x-forwarded-by");
        assertThat(loopPreventionValues, hasSize(1));
        HttpRequest atSecond = secondClient.retrieveRecordedRequests(request().withPath("/forwarded"))[0];
        assertThat(atSecond.getHeader(HEADER), hasSize(1));
        assertThat(atSecond.getHeader(HEADER).get(0), is(not(loopPreventionValues.get(0))));
        assertThat(atSecond.getHeader("x-forwarded-by"), is(empty()));
    }

    @Test
    public void shouldStopALoopBetweenTwoInstances() throws Exception {
        firstClient.when(request().withPath("/ping-pong")).forward(forward().withHost("127.0.0.1").withPort(second.getLocalPort()));
        secondClient.when(request().withPath("/ping-pong")).forward(forward().withHost("127.0.0.1").withPort(first.getLocalPort()));

        Response response = send(first, "/ping-pong");

        assertThat(response.toString(), response.status, is(508));
        assertThat(response.body, is(LOOP_DETECTED));
    }

    @Test
    public void shouldForwardThroughAChainOfInstancesAndKeepTheHeaderOutOfRecordedExpectations() throws Exception {
        firstClient.when(request().withPath("/chain")).forward(forward().withHost("127.0.0.1").withPort(second.getLocalPort()));
        secondClient.when(request().withPath("/chain")).forward(forward().withHost("127.0.0.1").withPort(third.getLocalPort()));
        thirdClient.when(request().withPath("/chain")).respond(response().withBody("answered by the third instance"));

        Response response = send(first, "/chain");

        assertThat(response.toString(), response.status, is(200));
        assertThat(response.body, is("answered by the third instance"));
        List<String> atSecond = secondClient.retrieveRecordedRequests(request().withPath("/chain"))[0].getHeader(HEADER);
        List<String> atThird = thirdClient.retrieveRecordedRequests(request().withPath("/chain"))[0].getHeader(HEADER);
        assertThat(atSecond, hasSize(1));
        assertThat(atThird, hasSize(2));
        assertThat(atThird.get(0), is(atSecond.get(0)));
        assertThat(atThird.get(1), is(not(atSecond.get(0))));
        Expectation[] recordedAtSecond = secondClient.retrieveRecordedExpectations(request().withPath("/chain"));
        assertThat(recordedAtSecond, arrayWithSize(1));
        assertThat(((HttpRequest) recordedAtSecond[0].getHttpRequest()).getHeader(HEADER), is(empty()));
    }

    @Test
    public void shouldNotSendTheHeaderWhenLoopDetectionIsOff() throws Exception {
        detectionOffClient.when(request().withPath("/off")).forward(forward().withHost("127.0.0.1").withPort(second.getLocalPort()));
        secondClient.when(request().withPath("/off")).respond(response().withBody("answered"));

        Response response = send(detectionOff, "/off");

        assertThat(response.toString(), response.status, is(200));
        assertThat(secondClient.retrieveRecordedRequests(request().withPath("/off"))[0].getHeader(HEADER), is(empty()));
    }

    @Test
    public void shouldForwardOnceToAnotherInstanceWithOneEntry() throws Exception {
        firstClient.when(request().withPath("/other")).forward(forward().withHost("127.0.0.1").withPort(second.getLocalPort()));
        secondClient.when(request().withPath("/other")).respond(response().withBody("answered by the second instance"));

        Response response = send(first, "/other");

        assertThat(response.toString(), response.status, is(200));
        assertThat(response.body, is("answered by the second instance"));
        assertThat(secondClient.retrieveRecordedRequests(request().withPath("/other"))[0].getHeader(HEADER), contains(containsString("MockServer_")));
    }

    private static Response send(MockServer mockServer, String path) throws IOException {
        return exchange(mockServer, "GET " + path + " HTTP/1.1\r\nHost: 127.0.0.1:" + mockServer.getLocalPort() + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
    }

    private static Response exchange(MockServer mockServer, String head) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", mockServer.getLocalPort())) {
            socket.setSoTimeout(30_000);
            OutputStream output = socket.getOutputStream();
            output.write(head.getBytes(StandardCharsets.ISO_8859_1));
            output.flush();
            ByteArrayOutputStream received = new ByteArrayOutputStream();
            InputStream input = socket.getInputStream();
            byte[] buffer = new byte[16 * 1024];
            for (int read = input.read(buffer); read != -1; read = input.read(buffer)) {
                received.write(buffer, 0, read);
            }
            return new Response(received.toString(StandardCharsets.ISO_8859_1.name()));
        }
    }

    /**
     * Forwards a request built afresh on each hop, without the received request's headers, back to the first server.
     */
    public static class FreshRequestCallback implements ExpectationForwardCallback {
        static volatile int port;

        @Override
        public HttpRequest handle(HttpRequest httpRequest) {
            return request().withPath("/callback-loop").withHeader("Host", "127.0.0.1:" + port);
        }
    }

    private static final class Response {
        private final int status;
        private final String body;
        private final String raw;

        private Response(String raw) {
            this.raw = raw;
            this.status = raw.length() > 12 ? Integer.parseInt(raw.substring(9, 12)) : -1;
            int bodyStart = raw.indexOf("\r\n\r\n");
            this.body = bodyStart < 0 || bodyStart + 4 == raw.length() ? null : raw.substring(bodyStart + 4);
        }

        @Override
        public String toString() {
            return raw;
        }
    }
}
