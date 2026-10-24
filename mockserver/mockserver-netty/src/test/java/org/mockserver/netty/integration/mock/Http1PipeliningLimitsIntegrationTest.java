package org.mockserver.netty.integration.mock;

import org.junit.After;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.Configuration;
import org.mockserver.netty.MockServer;
import org.mockserver.netty.integration.PipelinedHttp1Client;
import org.mockserver.netty.integration.PipelinedHttp1Client.Response;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpSseResponse.sseResponse;
import static org.mockserver.model.HttpTemplate.TemplateType.VELOCITY;
import static org.mockserver.model.HttpTemplate.template;
import static org.mockserver.model.SseEvent.sseEvent;
import static org.mockserver.netty.integration.PipelinedHttp1Client.get;
import static org.mockserver.netty.integration.PipelinedHttp1Client.rawRequest;
import static org.mockserver.netty.integration.PipelinedHttp1Client.rawRequestWithBody;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * The bounds on what an HTTP/1.1 connection holds for in-order pipelining, on a real socket: an oversized request
 * released while reads are paused is refused with a closing {@code 413}; a stream that keeps writing is never cut by the
 * no-progress backstop; and past 1,024 held requests the connection answers what it holds, in order, then closes.
 */
public class Http1PipeliningLimitsIntegrationTest {

    private MockServer mockServer;
    private MockServerClient mockServerClient;

    @After
    public void stopServer() {
        stopQuietly(mockServerClient);
        stopQuietly(mockServer);
    }

    private int start(Configuration configuration) {
        mockServer = new MockServer(configuration.logLevel("WARN"), 0);
        mockServerClient = new MockServerClient("127.0.0.1", mockServer.getLocalPort());
        mockServerClient.when(request().withPath("/fast")).respond(response("fast"));
        return mockServer.getLocalPort();
    }

    // test 19
    @Test
    public void shouldRefuseAnOversizedPipelinedRequestWithAClosing413() throws Exception {
        int port = start(configuration().maxRequestBodySize(1024));
        mockServerClient.when(request().withPath("/slow")).respond(response("slow").withDelay(TimeUnit.MILLISECONDS, 300));
        mockServerClient.when(request().withPath("/upload")).respond(response("uploaded"));

        try (PipelinedHttp1Client client = PipelinedHttp1Client.plain(port)) {
            client.pipeline(get("/slow"), rawRequestWithBody("POST", "/upload", "u".repeat(2_000)), get("/fast"));

            assertThat(client.readResponse().body(), is("slow"));
            Response tooLarge = client.readResponse();
            assertThat(tooLarge.status, is(413));
            assertThat(tooLarge.header("connection"), is("close"));
            assertThat("nothing follows the 413 but the close", client.readUntilClosedOrQuiet(5_000), is(""));
        }
        assertThat("the request behind the refused one is not answered", mockServerClient.retrieveRecordedRequests(request().withPath("/fast")).length, is(0));
    }

    // test 24
    @Test
    public void shouldNotCloseAConnectionWhoseStreamKeepsWritingForLongerThanTheIdleTimeout() throws Exception {
        int port = start(configuration().inboundConnectionIdleTimeoutMillis(1_000L));
        List<org.mockserver.model.SseEvent> events = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            events.add(sseEvent().withData("event " + i).withDelay(TimeUnit.MILLISECONDS, 300));
        }
        mockServerClient.when(request().withPath("/sse")).respondWithSse(sseResponse().withCloseConnection(false).withEvents(events));

        try (PipelinedHttp1Client client = PipelinedHttp1Client.plain(port)) {
            long start = System.currentTimeMillis();
            client.pipeline(rawRequest("GET", "/sse", "Accept: text/event-stream"), get("/fast"));

            Response stream = client.readResponse();
            assertThat(stream.body(), containsString("event 9"));
            assertThat("the stream outlasted the idle timeout", System.currentTimeMillis() - start, greaterThanOrEqualTo(2_500L));
            assertThat(client.readResponse().body(), is("fast"));
        }
    }

    // test 28
    @Test
    public void shouldAnswerTheHeldRequestsInOrderThenCloseWhenTooManyArePipelined() throws Exception {
        assertCappedConnectionDrainsThenCloses(configuration());
    }

    @Test
    public void shouldAnswerTheHeldRequestsInOrderThenCloseWhenTooManyArePipelinedWithNoIdleTimeout() throws Exception {
        assertCappedConnectionDrainsThenCloses(configuration().inboundConnectionIdleTimeoutMillis(0L));
    }

    private void assertCappedConnectionDrainsThenCloses(Configuration configuration) throws Exception {
        int port = start(configuration);
        // every exchange is answered asynchronously, so whatever is decoded behind it is held
        mockServerClient.when(request().withPath("/c/.*")).respond(template(VELOCITY, "{ \"statusCode\": 200, \"body\": \"$!request.path\" }").withDelay(TimeUnit.MILLISECONDS, 1));
        int sent = 4_000;
        StringBuilder wire = new StringBuilder();
        for (int i = 0; i < sent; i++) {
            wire.append("GET /c/").append(i).append(" HTTP/1.1\r\nHost: a\r\n\r\n");
        }
        byte[] requests = wire.toString().getBytes(StandardCharsets.US_ASCII);

        try (PipelinedHttp1Client client = PipelinedHttp1Client.plain(port)) {
            OutputStream out = client.socket().getOutputStream();
            CompletableFuture<Void> written = CompletableFuture.runAsync(() -> {
                try {
                    out.write(requests);
                    out.flush();
                } catch (IOException closedWhileWriting) {
                    // the server closes the connection once it has answered what it held
                }
            });

            List<String> received = new ArrayList<>();
            long lastResponseAt = System.currentTimeMillis();
            try {
                while (received.size() < sent) {
                    received.add(client.readResponse().body());
                    lastResponseAt = System.currentTimeMillis();
                }
            } catch (IOException closed) {
                // the connection closed after the last response it was going to send
            }
            long closedAfter = System.currentTimeMillis() - lastResponseAt;

            assertThat("the cap refused some requests", received.size(), lessThan(sent));
            assertThat("the held requests were answered", received.size(), greaterThanOrEqualTo(1_025));
            for (int i = 0; i < received.size(); i++) {
                assertThat("response " + i + " is in order", received.get(i), is("/c/" + i));
            }
            assertThat("closed " + closedAfter + "ms after the last response", closedAfter, lessThan(5_000L));
            written.get(10, TimeUnit.SECONDS);
        }
        long deadline = System.currentTimeMillis() + 10_000;
        while (mockServer.getRequestsInFlight() != 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertThat(mockServer.getRequestsInFlight(), is(0));
    }
}
