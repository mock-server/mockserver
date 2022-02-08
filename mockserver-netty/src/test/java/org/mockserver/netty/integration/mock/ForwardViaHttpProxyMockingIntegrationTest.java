package org.mockserver.netty.integration.mock;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.model.HttpTemplate;
import org.mockserver.netty.MockServer;
import org.mockserver.proxyconfiguration.ProxyConfiguration;
import org.mockserver.testing.integration.callback.PrecannedTestExpectationForwardCallbackRequest;
import org.mockserver.testing.integration.mock.AbstractMockingIntegrationTestBase;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static junit.framework.TestCase.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.matchers.Times.once;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.OK_200;
import static org.mockserver.model.HttpTemplate.template;
import static org.mockserver.proxyconfiguration.ProxyConfiguration.proxyConfiguration;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
public class ForwardViaHttpProxyMockingIntegrationTest extends AbstractMockingIntegrationTestBase {

    private static MockServer mockServer;
    private static MockServer proxy;

    @BeforeClass
    public static void startServer() {
        proxy = new MockServer();
        mockServer = new MockServer(proxyConfiguration(ProxyConfiguration.Type.HTTP, "127.0.0.1:" + proxy.getLocalPort()));

        mockServerClient = new MockServerClient("localhost", mockServer.getLocalPort());
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(proxy);
        stopQuietly(mockServer);
        stopQuietly(mockServerClient);
    }

    public int getServerPort() {
        return mockServer.getLocalPort();
    }

    @Test
    public void shouldForwardRequestInHTTP() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
            )
            .forward(
                forward()
                    .withHost("127.0.0.1")
                    .withPort(insecureEchoServer.getPort())
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("an_example_body_http"),
            makeRequest(
                request()
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("Host", "127.0.0.1:" + insecureEchoServer.getPort()),
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_http"),
                    HEADERS_TO_IGNORE)
        );
    }

    @Test
    public void shouldForwardOverriddenRequest() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
                    .withSecure(false)
            )
            .forward(
                forwardOverriddenRequest(
                    request()
                        .withHeader("Host", "localhost:" + insecureEchoServer.getPort())
                        .withBody("some_overridden_body")
                ).withDelay(MILLISECONDS, 10)
            );
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
                    .withSecure(true)
            )
            .forward(
                forwardOverriddenRequest(
                    request()
                        .withHeader("Host", "localhost:" + insecureEchoServer.getPort())
                        .withBody("some_overridden_body")
                ).withDelay(MILLISECONDS, 10)
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("some_overridden_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_http"),
                    HEADERS_TO_IGNORE

            )
        );
    }

    @Test
    public void shouldCallbackForForwardToSpecifiedClassWithPrecannedResponse() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
            )
            .forward(
                callback()
                    .withCallbackClass(PrecannedTestExpectationForwardCallbackRequest.class)
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("some_overridden_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body"),
                        header("x-echo-server-port", insecureEchoServer.getPort())
                    )
                    .withBody("an_example_body_http"),
                    HEADERS_TO_IGNORE
            )
        );
    }

    @Test
    public void shouldForwardTemplateInVelocity() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
            )
            .forward(
                template(HttpTemplate.TemplateType.VELOCITY,
                    "{" + NEW_LINE +
                        "    'path' : \"/somePath\"," + NEW_LINE +
                        "    'headers' : [ {" + NEW_LINE +
                        "        'name' : \"Host\"," + NEW_LINE +
                        "        'values' : [ \"127.0.0.1:" + insecureEchoServer.getPort() + "\" ]" + NEW_LINE +
                        "    }, {" + NEW_LINE +
                        "        'name' : \"x-test\"," + NEW_LINE +
                        "        'values' : [ \"$!request.headers['x-test'][0]\" ]" + NEW_LINE +
                        "    } ]," + NEW_LINE +
                        "    'body': \"{'name': 'value'}\"" + NEW_LINE +
                        "}")
                    .withDelay(MILLISECONDS, 10)
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("{'name': 'value'}"),
            makeRequest(
                request()
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_http"),
                    HEADERS_TO_IGNORE
            )
        );
    }

    @Test
    public void shouldAllowSimultaneousForwardAndResponseExpectations() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
                    .withSecure(false)
            )
            .forward(
                forwardOverriddenRequest(
                    request()
                        .withHeader("Host", "localhost:" + insecureEchoServer.getPort())
                        .withBody("some_overridden_body")
                ).withDelay(MILLISECONDS, 10)
            );
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("test_headers_and_body")),
                once()
            )
            .respond(
                response()
                    .withBody("some_body")
            );

        // then
        // - forward
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("some_overridden_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body"),
                    HEADERS_TO_IGNORE)
        );
        // - respond
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("test_headers_and_body")),
                    HEADERS_TO_IGNORE)
        );
        // - no response or forward
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("test_headers_and_body")),
                    HEADERS_TO_IGNORE)
        );
    }
}
