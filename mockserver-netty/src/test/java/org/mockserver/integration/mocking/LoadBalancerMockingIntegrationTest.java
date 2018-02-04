package org.mockserver.integration.mocking;

import io.netty.handler.codec.http.HttpHeaderNames;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.integration.server.AbstractBasicMockingIntegrationTest;
import org.mockserver.mock.Expectation;
import org.mockserver.mockserver.MockServer;
import org.mockserver.model.HttpOverrideForwardedRequest;
import org.mockserver.model.HttpStatusCode;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.matchers.Times.exactly;
import static org.mockserver.matchers.Times.once;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.OK_200;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.StringBody.exact;

/**
 * @author jamesdbloom
 */
@Ignore
public class LoadBalancerMockingIntegrationTest extends AbstractBasicMockingIntegrationTest {

    private static int mockServerPort;
    private static EchoServer echoServer;
    private static ClientAndServer loadBalancerClient;

    @BeforeClass
    public static void startServer() {
        echoServer = new EchoServer(false);

        loadBalancerClient = startClientAndServer();
        MockServer mockServer = new MockServer("localhost", echoServer.getPort(), 0);
        loadBalancerClient
            .when(request())
            .forward(
                forwardOverriddenRequest(
                    request()
                        .withHeader(HOST.toString(), "127.0.0.1:" + mockServer.getLocalPort())
                )
            );

        mockServerPort = loadBalancerClient.getLocalPort();
        mockServerClient = new MockServerClient("localhost", mockServer.getLocalPort());
    }

    @AfterClass
    public static void stopServer() {
        if (mockServerClient != null) {
            mockServerClient.stop();
        }

        if (loadBalancerClient != null) {
            loadBalancerClient.stop();
        }

        if (echoServer != null) {
            echoServer.stop();
        }
    }

    @Override
    public int getMockServerPort() {
        return mockServerPort;
    }

    @Override
    public int getMockServerSecurePort() {
        return mockServerPort;
    }

    @Override
    public int getTestServerPort() {
        return echoServer.getPort();
    }

}
