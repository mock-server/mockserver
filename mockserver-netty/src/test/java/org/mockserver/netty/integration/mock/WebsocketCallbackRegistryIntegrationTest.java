package org.mockserver.netty.integration.mock;

import org.hamcrest.CoreMatchers;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.metrics.Metrics;
import org.mockserver.mock.action.ExpectationForwardAndResponseCallback;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.testing.integration.mock.AbstractMockingIntegrationTestBase;
import org.mockserver.uuid.UUIDService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.configuration.ConfigurationProperties.controlPlaneJWTAuthenticationJWKSource;
import static org.mockserver.configuration.ConfigurationProperties.metricsEnabled;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.matchers.Times.once;
import static org.mockserver.model.ConnectionOptions.connectionOptions;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.OK_200;
import static org.mockserver.stop.Stop.stopQuietly;
import static org.mockserver.test.Retries.tryWaitForSuccess;
import static org.mockserver.testing.closurecallback.ViaWebSocket.viaWebSocket;

/**
 * @author jamesdbloom
 */
public class WebsocketCallbackRegistryIntegrationTest extends AbstractMockingIntegrationTestBase {

    private static boolean originalMetricsEnabled;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @BeforeClass
    public static void startServer() {
        // save original value
        originalMetricsEnabled = metricsEnabled();

        // enabled metrics
        metricsEnabled(true);

        mockServerClient = new ClientAndServer();
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServerClient);

        // set back to original value
        metricsEnabled(originalMetricsEnabled);
    }

    @Override
    public int getServerPort() {
        return mockServerClient.getPort();
    }

    @Test // same JVM due to dynamic calls to static Metrics class
    public void shouldRemoveWebsocketCallbackClientFromRegistryForClientResetViaWebSocket() throws Exception {
        viaWebSocket(() -> {
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
            tryWaitForSuccess(() -> assertThat(Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_CLIENTS_COUNT), CoreMatchers.is(1)));

            // when
            mockServerClient.reset();

            // then
            tryWaitForSuccess(() -> assertThat(Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_CLIENTS_COUNT), CoreMatchers.is(0)));
        });
    }

    @Test // same JVM due to dynamic calls to static Metrics class
    public void shouldRemoveWebsocketCallbackClientFromRegistryForClientResetViaLocalJVM() {
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
        assertThat(Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_CLIENTS_COUNT), CoreMatchers.is(0));

        // when
        mockServerClient.reset();

        // then
        assertThat(Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_CLIENTS_COUNT), CoreMatchers.is(0));
    }

    @Test // same JVM due to dynamic calls to static Metrics class
    public void shouldRemoveWebsocketCallbackClientFromRegistryForClientStopViaWebSocket() throws Exception {
        viaWebSocket(() -> {
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
                tryWaitForSuccess(() -> assertThat(Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_CLIENTS_COUNT), CoreMatchers.is(1)));

                // when
                mockServerClient.stop();

                // then
                tryWaitForSuccess(() -> assertThat(Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_CLIENTS_COUNT), CoreMatchers.is(0)));
            } finally {
                mockServerClient.stop();
            }
        });
    }

    @Test // same JVM due to dynamic calls to static Metrics class
    public void shouldRemoveWebsocketCallbackClientFromRegistryForClientStopViaLocalJVM() {
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
            assertThat(Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_CLIENTS_COUNT), CoreMatchers.is(0));

            // when
            mockServerClient.stop();

            // then
            assertThat(Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_CLIENTS_COUNT), CoreMatchers.is(0));
        } finally {
            mockServerClient.stop();
        }
    }

    @Test // same JVM due to dynamic calls to static Metrics class
    public void shouldRemoveWebsocketResponseHandlerFromRegistryViaWebSocket() throws Exception {
        viaWebSocket(() -> {
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
                            .withBody("websocket_response_handler_count_" + Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_RESPONSE_HANDLERS_COUNT) + "_" + Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_FORWARD_HANDLERS_COUNT));
                    }
                );

            // then
            assertThat(Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_CLIENTS_COUNT), CoreMatchers.is(1));

            // when
            assertEquals(
                response()
                    .withStatusCode(OK_200.code())
                    .withReasonPhrase(OK_200.reasonPhrase())
                    .withBody("websocket_response_handler_count_1_0"),
                makeRequest(
                    request()
                        .withPath(calculatePath("websocket_response_handler")),
                    getHeadersToRemove()
                )
            );

            // then
            assertThat(Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_RESPONSE_HANDLERS_COUNT), CoreMatchers.is(0));
        });
    }

    @Test // same JVM due to dynamic calls to static Metrics class
    public void shouldRemoveWebsocketResponseHandlerFromRegistryViaLocalJVM() {
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
                        .withBody("websocket_response_handler_count_" + Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_RESPONSE_HANDLERS_COUNT) + "_" + Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_FORWARD_HANDLERS_COUNT));
                }
            );

        // then
        assertThat(Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_CLIENTS_COUNT), CoreMatchers.is(0));

        // when
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("websocket_response_handler_count_0_0"),
            makeRequest(
                request()
                    .withPath(calculatePath("websocket_response_handler")),
                getHeadersToRemove()
            )
        );

        // then
        assertThat(Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_RESPONSE_HANDLERS_COUNT), CoreMatchers.is(0));
    }

    @Test // same JVM due to dynamic calls to static Metrics class
    public void shouldRemoveWebsocketForwardHandlerFromRegistryViaWebSocket() throws Exception {
        viaWebSocket(() -> {
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
                        .withBody("websocket_forward_handler_count_" + Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_FORWARD_HANDLERS_COUNT) + "_" + Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_RESPONSE_HANDLERS_COUNT))
                );

            // then
            tryWaitForSuccess(() -> assertThat(Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_CLIENTS_COUNT), CoreMatchers.is(1)));

            // when
            assertEquals(
                response()
                    .withStatusCode(OK_200.code())
                    .withReasonPhrase(OK_200.reasonPhrase())
                    .withBody("websocket_forward_handler_count_1_0"),
                makeRequest(
                    request()
                        .withPath(calculatePath("websocket_forward_handler")),
                    getHeadersToRemove()
                )
            );

            // then
            tryWaitForSuccess(() -> assertThat(Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_FORWARD_HANDLERS_COUNT), CoreMatchers.is(0)));
        });
    }

    @Test // same JVM due to dynamic calls to static Metrics class
    public void shouldRemoveWebsocketForwardHandlerFromRegistryViaLocalJVM() {
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
                    .withBody("websocket_forward_handler_count_" + Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_FORWARD_HANDLERS_COUNT) + "_" + Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_RESPONSE_HANDLERS_COUNT))
            );

        // then
        assertThat(Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_CLIENTS_COUNT), CoreMatchers.is(0));

        // when
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("websocket_forward_handler_count_0_0"),
            makeRequest(
                request()
                    .withPath(calculatePath("websocket_forward_handler")),
                getHeadersToRemove()
            )
        );

        // then
        assertThat(Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_FORWARD_HANDLERS_COUNT), CoreMatchers.is(0));
    }

    @Test // same JVM due to dynamic calls to static Metrics class
    public void shouldRemoveWebsocketForwardAndResponseHandlerFromRegistryViaWebSocket() throws Exception {
        viaWebSocket(() -> {
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
                            .withBody("websocket_forward_handler_count_" + Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_FORWARD_HANDLERS_COUNT) + "_" + Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_RESPONSE_HANDLERS_COUNT)),
                    (httpRequest, httpResponse) ->
                        httpResponse
                            .withHeader("x-response-test", "websocket_forward_handler_count_" + Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_FORWARD_HANDLERS_COUNT) + "_" + Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_RESPONSE_HANDLERS_COUNT))
                );

            // then
            tryWaitForSuccess(() -> assertThat(Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_CLIENTS_COUNT), CoreMatchers.is(1)));

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
                    getHeadersToRemove()
                )
            );

            // then
            tryWaitForSuccess(() -> {
                assertThat(Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_FORWARD_HANDLERS_COUNT), CoreMatchers.is(0));
                assertThat(Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_RESPONSE_HANDLERS_COUNT), CoreMatchers.is(0));
            });
        });
    }

    @Test // same JVM due to dynamic calls to static Metrics class
    public void shouldRemoveWebsocketForwardAndResponseHandlerFromRegistryViaLocalJVM() {
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
                        .withBody("websocket_forward_handler_count_" + Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_FORWARD_HANDLERS_COUNT) + "_" + Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_RESPONSE_HANDLERS_COUNT)),
                (httpRequest, httpResponse) ->
                    httpResponse
                        .withHeader("x-response-test", "websocket_forward_handler_count_" + Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_FORWARD_HANDLERS_COUNT) + "_" + Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_RESPONSE_HANDLERS_COUNT))
            );

        // then
        assertThat(Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_CLIENTS_COUNT), CoreMatchers.is(0));

        // when
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader("x-response-test", "websocket_forward_handler_count_0_0")
                .withBody("websocket_forward_handler_count_0_0"),
            makeRequest(
                request()
                    .withPath(calculatePath("websocket_forward_handler")),
                getHeadersToRemove()
            )
        );

        // then
        assertThat(Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_FORWARD_HANDLERS_COUNT), CoreMatchers.is(0));
        assertThat(Metrics.get(Metrics.Name.WEBSOCKET_CALLBACK_RESPONSE_HANDLERS_COUNT), CoreMatchers.is(0));
    }

    private int objectCallbackCounter = 0;

    @Test
    public void shouldAllowUseOfSameWebsocketClientInsideCallbackViaWebSocket() throws Exception {
        viaWebSocket(() -> {
            // when
            objectCallbackCounter = 0;
            int total = 50;
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
                        getHeadersToRemove()
                    )
                );
                assertEquals(
                    response()
                        .withStatusCode(OK_200.code())
                        .withReasonPhrase(OK_200.reasonPhrase())
                        .withBody("inner_websocket_client_registration_" + objectCallbackCounter),
                    makeRequest(
                        request()
                            .withPath(calculatePath("inner_websocket_client_registration_" + objectCallbackCounter)),
                        getHeadersToRemove()
                    )
                );
                objectCallbackCounter++;
            }
        });
    }

    @Test
    public void shouldAllowUseOfSameWebsocketClientInsideCallbackViaLocalJVM() {
        // when
        objectCallbackCounter = 0;
        int total = 50;
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
                    getHeadersToRemove()
                )
            );
            assertEquals(
                response()
                    .withStatusCode(OK_200.code())
                    .withReasonPhrase(OK_200.reasonPhrase())
                    .withBody("inner_websocket_client_registration_" + objectCallbackCounter),
                makeRequest(
                    request()
                        .withPath(calculatePath("inner_websocket_client_registration_" + objectCallbackCounter)),
                    getHeadersToRemove()
                )
            );
            objectCallbackCounter++;
        }
    }

    @Test
    public void shouldAllowUseOfSeparateWebsocketClientInsideCallback() throws Exception {
        viaWebSocket(() -> {
            // when
            int total = 50;
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
                        getHeadersToRemove()
                    )
                );
                assertEquals(
                    response()
                        .withStatusCode(OK_200.code())
                        .withReasonPhrase(OK_200.reasonPhrase())
                        .withBody("inner_websocket_client_registration_" + objectCallbackCounter),
                    makeRequest(
                        request()
                            .withPath(calculatePath("inner_websocket_client_registration_" + objectCallbackCounter)),
                        getHeadersToRemove()
                    )
                );
                objectCallbackCounter++;
            }
        });
    }

    @Test
    public void shouldForwardLargeNumberOfRequestsAndResponsesByObjectCallbackViaWebSocket() throws Exception {
        viaWebSocket(() -> {
            ClientAndServer proxy = null;
            try {
                mockServerClient
                    .when(
                        request()
                            .withMethod("GET")
                            .withPath("/api/v1/employees")
                    )
                    .respond(
                        response()
                            .withBody("original body response")
                            .withConnectionOptions(
                                connectionOptions()
                                    .withSuppressContentLengthHeader(true)
                                    .withCloseSocket(true)
                            )
                    );
                String addedHeader = UUIDService.getUUID();
                proxy = startClientAndServer();
                proxy
                    .when(
                        request()
                            .withPath("/api/v1/employees")
                    )
                    .forward(
                        httpRequest -> httpRequest
                            .clone()
                            .replaceHeader(new Header("host", "localhost:" + (Integer) getServerPort())),
                        (httpRequest, httpResponse) ->
                            httpResponse
                                .withReasonPhrase("OK " + httpRequest.getFirstHeader("Counter"))
                                .withBody("modified body response " + httpRequest.getFirstHeader("Counter"))
                                .withHeader("AddedHeader", addedHeader)
                                .removeHeader("Content-Length")
                    );

                for (int counter = 0; counter < 500; ++counter) {
                    try {
                        URL url = new URL("http://localhost:" + proxy.getPort() + "/api/v1/employees");
                        HttpURLConnection con = (HttpURLConnection) url.openConnection();
                        con.setRequestMethod("GET");
                        con.setRequestProperty("Counter", "" + counter);
                        int responseCode = con.getResponseCode();
                        StringBuilder textBuilder = new StringBuilder();
                        try (Reader reader = new BufferedReader(new InputStreamReader(con.getInputStream(), Charset.forName(StandardCharsets.UTF_8.name())))) {
                            int c;
                            while ((c = reader.read()) != -1) {
                                textBuilder.append((char) c);
                            }
                        }
                        String body = textBuilder.toString();
                        con.disconnect();

                        // then
                        assertThat(responseCode, is(200));
                        assertThat(con.getHeaderField("AddedHeader"), is(addedHeader));
                        assertThat(body, is("modified body response " + counter));
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw e;
                    }
                }
            } finally {
                if (proxy != null) {
                    proxy.close();
                }
            }
        });
    }

    @Test
    public void shouldForwardLargeNumberOfRequestsAndResponsesByObjectCallbackViaLocalJVM() throws Exception {
        ClientAndServer proxy = null;
        try {
            mockServerClient
                .when(
                    request()
                        .withMethod("GET")
                        .withPath("/api/v1/employees")
                )
                .respond(
                    response()
                        .withBody("original body response")
                        .withConnectionOptions(
                            connectionOptions()
                                .withSuppressContentLengthHeader(true)
                                .withCloseSocket(true)
                        )
                );
            String addedHeader = UUIDService.getUUID();
            proxy = startClientAndServer();
            proxy
                .when(
                    request()
                        .withPath("/api/v1/employees")
                )
                .forward(
                    httpRequest -> httpRequest
                        .clone()
                        .replaceHeader(new Header("host", "localhost:" + (Integer) getServerPort())),
                    (httpRequest, httpResponse) ->
                        httpResponse
                            .withReasonPhrase("OK " + httpRequest.getFirstHeader("Counter"))
                            .withBody("modified body response " + httpRequest.getFirstHeader("Counter"))
                            .withHeader("AddedHeader", addedHeader)
                            .removeHeader("Content-Length")
                );

            for (int counter = 0; counter < 500; ++counter) {
                try {
                    URL url = new URL("http://localhost:" + proxy.getPort() + "/api/v1/employees");
                    HttpURLConnection con = (HttpURLConnection) url.openConnection();
                    con.setRequestMethod("GET");
                    con.setRequestProperty("Counter", "" + counter);
                    int responseCode = con.getResponseCode();
                    StringBuilder textBuilder = new StringBuilder();
                    try (Reader reader = new BufferedReader(new InputStreamReader(con.getInputStream(), Charset.forName(StandardCharsets.UTF_8.name())))) {
                        int c;
                        while ((c = reader.read()) != -1) {
                            textBuilder.append((char) c);
                        }
                    }
                    String body = textBuilder.toString();
                    con.disconnect();

                    // then
                    assertThat(responseCode, is(200));
                    assertThat(con.getHeaderField("AddedHeader"), is(addedHeader));
                    assertThat(body, is("modified body response " + counter));
                } catch (Exception e) {
                    e.printStackTrace();
                    throw e;
                }
            }
        } finally {
            if (proxy != null) {
                proxy.close();
            }
        }
    }

    @Test
    public void shouldForwardLargeNumberOfModifiedRequestAndReturnModifiedResponseByClassCallback() throws Exception {
        ClientAndServer proxy = null;
        try {
            mockServerClient
                .when(
                    request()
                        .withMethod("GET")
                        .withPath("/api/v1/employees")
                )
                .respond(
                    response()
                        .withBody("original body response")
                        .withConnectionOptions(
                            connectionOptions()
                                .withSuppressContentLengthHeader(true)
                                .withCloseSocket(true)
                        )
                );
            TestExpectationForwardAndResponseCallback.addedHeader = UUIDService.getUUID();
            TestExpectationForwardAndResponseCallback.serverPort = getServerPort();
            proxy = startClientAndServer();
            proxy
                .when(
                    request()
                        .withPath("/api/v1/employees")
                )
                .forward(callback(TestExpectationForwardAndResponseCallback.class));

            for (int counter = 0; counter < 500; ++counter) {
                try {
                    URL url = new URL("http://localhost:" + proxy.getPort() + "/api/v1/employees");
                    HttpURLConnection con = (HttpURLConnection) url.openConnection();
                    con.setRequestMethod("GET");
                    con.setRequestProperty("Counter", "" + counter);
                    int responseCode = con.getResponseCode();
                    StringBuilder textBuilder = new StringBuilder();
                    try (Reader reader = new BufferedReader(new InputStreamReader(con.getInputStream(), Charset.forName(StandardCharsets.UTF_8.name())))) {
                        int c;
                        while ((c = reader.read()) != -1) {
                            textBuilder.append((char) c);
                        }
                    }
                    String body = textBuilder.toString();
                    con.disconnect();

                    // then
                    assertThat(responseCode, is(200));
                    assertThat(con.getHeaderField("AddedHeader"), is(TestExpectationForwardAndResponseCallback.addedHeader));
                    assertThat(body, is("modified body response " + counter));
                } catch (Exception e) {
                    e.printStackTrace();
                    throw e;
                }
            }
        } finally {
            if (proxy != null) {
                proxy.close();
            }
        }
    }

    public static class TestExpectationForwardAndResponseCallback implements ExpectationForwardAndResponseCallback {

        static String addedHeader;
        static Integer serverPort;

        @Override
        public HttpRequest handle(HttpRequest httpRequest) {
            return httpRequest
                .clone()
                .replaceHeader(new Header("host", "localhost:" + serverPort));
        }

        @Override
        public HttpResponse handle(HttpRequest httpRequest, HttpResponse httpResponse) {
            return httpResponse
                .withReasonPhrase("OK " + httpRequest.getFirstHeader("Counter"))
                .withBody("modified body response " + httpRequest.getFirstHeader("Counter"))
                .withHeader("AddedHeader", addedHeader)
                .removeHeader("Content-Length");
        }
    }
}
