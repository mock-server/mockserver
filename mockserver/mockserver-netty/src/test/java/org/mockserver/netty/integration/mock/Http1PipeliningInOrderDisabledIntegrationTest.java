package org.mockserver.netty.integration.mock;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.netty.MockServer;
import org.mockserver.netty.integration.PipelinedHttp1Client;
import org.mockserver.netty.integration.PipelinedHttp1Client.Response;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.netty.integration.PipelinedHttp1Client.get;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * With {@code http1PipeliningInOrder} off, pipelined HTTP/1.1 requests are handled as they arrive, as before the
 * in-order gate: a fast response is written ahead of a slower one sent before it.
 */
public class Http1PipeliningInOrderDisabledIntegrationTest {

    private static MockServer mockServer;
    private static MockServerClient mockServerClient;

    @BeforeClass
    public static void startServer() {
        mockServer = new MockServer(configuration().logLevel("WARN").http1PipeliningInOrder(false), 0);
        mockServerClient = new MockServerClient("127.0.0.1", mockServer.getLocalPort());
        mockServerClient.when(request().withPath("/slow")).respond(response("slow").withDelay(TimeUnit.MILLISECONDS, 1_000));
        mockServerClient.when(request().withPath("/fast")).respond(response("fast"));
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServerClient);
        stopQuietly(mockServer);
    }

    @Test
    public void shouldAnswerPipelinedRequestsAsTheyCompleteWhenInOrderPipeliningIsOff() throws Exception {
        try (PipelinedHttp1Client client = PipelinedHttp1Client.plain(mockServer.getLocalPort())) {
            client.pipeline(get("/slow"), get("/fast"));

            List<Response> responses = client.readResponses(false, false);
            assertThat("the fast response is written first", responses.get(0).body(), is("fast"));
            assertThat(responses.get(1).body(), is("slow"));
        }
    }
}
