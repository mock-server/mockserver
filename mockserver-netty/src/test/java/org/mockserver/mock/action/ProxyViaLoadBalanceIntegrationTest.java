package org.mockserver.mock.action;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpResponse;
import org.mockserver.verify.VerificationTimes;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class ProxyViaLoadBalanceIntegrationTest {

    private static ClientAndServer clientAndServer;
    private static ClientAndServer loadBalancerClient;

    private static NettyHttpClient httpClient = new NettyHttpClient();

    @BeforeClass
    public static void startServer() {
        clientAndServer = startClientAndServer();
        loadBalancerClient = startClientAndServer("127.0.0.1", clientAndServer.getLocalPort());
    }

    @AfterClass
    public static void stopServer() {
        if (clientAndServer != null) {
            clientAndServer.stop();
        }
        if (loadBalancerClient != null) {
            loadBalancerClient.stop();
        }
    }

    @Before
    public void reset() {
        clientAndServer.reset();
        loadBalancerClient.reset();
    }

    @Test
    public void shouldNotForwardRequestWithInvalidHostHead() throws Exception {
        // when
        Future<HttpResponse> responseSettableFuture =
            httpClient.sendRequest(
                request()
                    .withPath("/some_path")
                    .withHeader(HOST.toString(), "localhost:" + loadBalancerClient.getLocalPort()),
                new InetSocketAddress(loadBalancerClient.getLocalPort())
            );

        // then - returns 404
        assertThat(responseSettableFuture.get(10, TimeUnit.SECONDS).getStatusCode(), is(404));

        // and - logs hide proxied request
        assertThat(clientAndServer.retrieveLogMessagesArray(null)[2], containsString("no matching expectation - returning:" + NEW_LINE +
            NEW_LINE +
            "\t{" + NEW_LINE +
            "\t  \"statusCode\" : 404," + NEW_LINE +
            "\t  \"reasonPhrase\" : \"Not Found\"" + NEW_LINE +
            "\t}" + NEW_LINE +
            NEW_LINE +
            " for request:" + NEW_LINE +
            NEW_LINE +
            "\t{" + NEW_LINE +
            "\t  \"method\" : \"GET\"," + NEW_LINE +
            "\t  \"path\" : \"/some_path\""));
        assertThat(loadBalancerClient.retrieveLogMessagesArray(null).length, is(5));
    }

    @Test
    public void shouldVerifyReceivedRequests() throws Exception {
        // given
        Future<HttpResponse> responseSettableFuture =
            httpClient.sendRequest(
                request()
                    .withPath("/some_path")
                    .withHeader(HOST.toString(), "localhost:" + loadBalancerClient.getLocalPort()),
                new InetSocketAddress(loadBalancerClient.getLocalPort())
            );

        // then
        assertThat(responseSettableFuture.get(10, TimeUnit.SECONDS).getStatusCode(), is(404));

        // then
        clientAndServer.verify(request()
            .withPath("/some_path"));
        clientAndServer.verify(request()
            .withPath("/some_path"), VerificationTimes.exactly(1));
    }

}
