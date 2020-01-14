package org.mockserver.netty.integration.mock;

import org.hamcrest.CoreMatchers;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.testing.integration.mock.AbstractMockingIntegrationTestBase;
import org.mockserver.metrics.Metrics;

import static org.junit.Assert.assertEquals;
import static org.mockserver.matchers.Times.once;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.OK_200;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
public class WebsocketCallbackRegistryIntegrationTest extends AbstractMockingIntegrationTestBase {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @BeforeClass
    public static void startServer() {
        mockServerClient = new ClientAndServer();
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServerClient);
    }

    @Override
    public int getServerPort() {
        return ((ClientAndServer) mockServerClient).getLocalPort();
    }


    @Test // same JVM due to dynamic calls to static Metrics class
    public void shouldRemoveWebsocketCallbackClientFromRegistryForClientReset() {
        // given
        Metrics.clear();
        final MockServerClient mockServerClient = new MockServerClient("localhost", getServerPort());
        mockServerClient
            .when(
                request()
            )
            .respond(
                httpRequest -> response()
            );

        // then
        Assert.assertThat(Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_CLIENT_COUNT), CoreMatchers.is(1));

        // when
        mockServerClient.reset();

        // then
        Assert.assertThat(Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_CLIENT_COUNT), CoreMatchers.is(0));
    }

    @Test // same JVM due to dynamic calls to static Metrics class
    public void shouldRemoveWebsocketCallbackClientFromRegistryForClientStop() {
        // given
        Metrics.clear();
        final MockServerClient mockServerClient = new ClientAndServer();
        mockServerClient
            .when(
                request()
            )
            .respond(
                httpRequest -> response()
            );

        try {
            // then
            Assert.assertThat(Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_CLIENT_COUNT), CoreMatchers.is(1));

            // when
            mockServerClient.stop();

            // then
            Assert.assertThat(Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_CLIENT_COUNT), CoreMatchers.is(0));
        } finally {
            mockServerClient.stop();
        }
    }

    @Test // same JVM due to dynamic calls to static Metrics class
    public void shouldRemoveWebsocketResponseHandlerFromRegistry() {
        // given
        Metrics.clear();
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("websocket_response_handler")),
                once()
            )
            .respond(
                httpRequest -> {
                    // then
                    return response()
                        .withBody("websocket_response_handler_count_" + Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_RESPONSE_HANDLER_COUNT) + "_" + Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_FORWARD_HANDLER_COUNT));
                }
            );

        // then
        Assert.assertThat(Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_CLIENT_COUNT), CoreMatchers.is(1));

        // when
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("websocket_response_handler_count_1_0"),
            makeRequest(
                request()
                    .withPath(calculatePath("websocket_response_handler")),
                headersToIgnore)
        );

        // then
        Assert.assertThat(Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_RESPONSE_HANDLER_COUNT), CoreMatchers.is(0));
    }

    @Test // same JVM due to dynamic calls to static Metrics class
    public void shouldRemoveWebsocketForwardHandlerFromRegistry() {
        // given
        Metrics.clear();
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("websocket_forward_handler"))
            )
            .forward(
                httpRequest -> request()
                    .withHeader("Host", "localhost:" + insecureEchoServer.getPort())
                    .withBody("websocket_forward_handler_count_" + Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_FORWARD_HANDLER_COUNT) + "_" + Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_RESPONSE_HANDLER_COUNT))
            );

        // then
        Assert.assertThat(Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_CLIENT_COUNT), CoreMatchers.is(1));

        // when
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("websocket_forward_handler_count_1_0"),
            makeRequest(
                request()
                    .withPath(calculatePath("websocket_forward_handler")),
                headersToIgnore)
        );

        // then
        Assert.assertThat(Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_FORWARD_HANDLER_COUNT), CoreMatchers.is(0));
    }

    @Test // same JVM due to dynamic calls to static Metrics class
    public void shouldRemoveWebsocketForwardAndResponseHandlerFromRegistry() {
        // given
        Metrics.clear();
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("websocket_forward_handler")),
                once()
            )
            .forward(
                httpRequest ->
                    request()
                        .withHeader("Host", "localhost:" + insecureEchoServer.getPort())
                        .withBody("websocket_forward_handler_count_" + Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_FORWARD_HANDLER_COUNT) + "_" + Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_RESPONSE_HANDLER_COUNT)),
                (httpRequest, httpResponse) ->
                    httpResponse
                        .withHeader("x-response-test", "websocket_forward_handler_count_" + Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_FORWARD_HANDLER_COUNT) + "_" + Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_RESPONSE_HANDLER_COUNT))
            );

        // then
        Assert.assertThat(Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_CLIENT_COUNT), CoreMatchers.is(1));

        // when
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader("x-response-test", "websocket_forward_handler_count_0_1")
                .withBody("websocket_forward_handler_count_1_0"),
            makeRequest(
                request()
                    .withPath(calculatePath("websocket_forward_handler")),
                headersToIgnore
            )
        );

        // then
        Assert.assertThat(Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_FORWARD_HANDLER_COUNT), CoreMatchers.is(0));
        Assert.assertThat(Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_RESPONSE_HANDLER_COUNT), CoreMatchers.is(0));
    }

    private int objectCallbackCounter = 0;

    @Test
    public void shouldAllowUseOfSameWebsocketClientInsideCallback() {
        // when
        int total = 5;
        for (int i = 0; i < total; i++) {
            mockServerClient
                .when(
                    request()
                        .withPath(calculatePath("outer_websocket_client_registration_" + objectCallbackCounter)),
                    once()
                )
                .respond(
                    httpRequest -> {
                        mockServerClient
                            .when(
                                request()
                                    .withPath(calculatePath("inner_websocket_client_registration_" + objectCallbackCounter)),
                                once()
                            )
                            .respond(innerRequest -> {
                                    mockServerClient
                                        .when(
                                            request()
                                                .withPath(calculatePath("inner_inner_websocket_client_registration_" + objectCallbackCounter)),
                                            once()
                                        )
                                        .respond(innerInnerRequest -> response()
                                            .withBody("inner_inner_websocket_client_registration_" + objectCallbackCounter)
                                        );
                                    return response()
                                        .withBody("inner_websocket_client_registration_" + objectCallbackCounter);
                                }
                            );
                        return response()
                            .withBody("outer_websocket_client_registration_" + objectCallbackCounter);
                    }
                );
            objectCallbackCounter++;
        }

        objectCallbackCounter = 0;

        // then
        for (int i = 0; i < total; i++) {
            assertEquals(
                response()
                    .withStatusCode(OK_200.code())
                    .withReasonPhrase(OK_200.reasonPhrase())
                    .withBody("outer_websocket_client_registration_" + objectCallbackCounter),
                makeRequest(
                    request()
                        .withPath(calculatePath("outer_websocket_client_registration_" + objectCallbackCounter)),
                    headersToIgnore)
            );
            assertEquals(
                response()
                    .withStatusCode(OK_200.code())
                    .withReasonPhrase(OK_200.reasonPhrase())
                    .withBody("inner_websocket_client_registration_" + objectCallbackCounter),
                makeRequest(
                    request()
                        .withPath(calculatePath("inner_websocket_client_registration_" + objectCallbackCounter)),
                    headersToIgnore)
            );
            objectCallbackCounter++;
        }
    }

    @Test
    public void shouldAllowUseOfSeparateWebsocketClientInsideCallback() {
        // when
        int total = 5;
        for (int i = 0; i < total; i++) {
            mockServerClient
                .when(
                    request()
                        .withPath(calculatePath("outer_websocket_client_registration_" + objectCallbackCounter)),
                    once()
                )
                .respond(
                    httpRequest -> {
                        new MockServerClient("localhost", getServerPort())
                            .when(
                                request()
                                    .withPath(calculatePath("inner_websocket_client_registration_" + objectCallbackCounter)),
                                once()
                            )
                            .respond(innerRequest ->
                                response()
                                    .withBody("inner_websocket_client_registration_" + objectCallbackCounter)
                            );
                        return response()
                            .withBody("outer_websocket_client_registration_" + objectCallbackCounter);
                    }
                );
            objectCallbackCounter++;
        }

        objectCallbackCounter = 0;

        // then
        for (int i = 0; i < total; i++) {
            assertEquals(
                response()
                    .withStatusCode(OK_200.code())
                    .withReasonPhrase(OK_200.reasonPhrase())
                    .withBody("outer_websocket_client_registration_" + objectCallbackCounter),
                makeRequest(
                    request()
                        .withPath(calculatePath("outer_websocket_client_registration_" + objectCallbackCounter)),
                    headersToIgnore)
            );
            assertEquals(
                response()
                    .withStatusCode(OK_200.code())
                    .withReasonPhrase(OK_200.reasonPhrase())
                    .withBody("inner_websocket_client_registration_" + objectCallbackCounter),
                makeRequest(
                    request()
                        .withPath(calculatePath("inner_websocket_client_registration_" + objectCallbackCounter)),
                    headersToIgnore)
            );
            objectCallbackCounter++;
        }
    }
}
