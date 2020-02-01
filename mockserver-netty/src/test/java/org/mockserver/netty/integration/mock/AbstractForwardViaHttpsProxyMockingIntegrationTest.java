package org.mockserver.netty.integration.mock;

import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContextBuilder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.model.HttpTemplate;
import org.mockserver.socket.tls.jdk.X509Generator;
import org.mockserver.testing.integration.callback.PrecannedTestExpectationForwardCallbackRequest;
import org.mockserver.testing.integration.mock.AbstractMockingIntegrationTestBase;

import javax.net.ssl.SSLException;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertEquals;
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
import static org.mockserver.verify.Verification.verification;

/**
 * @author jamesdbloom
 */
public abstract class AbstractForwardViaHttpsProxyMockingIntegrationTest extends AbstractMockingIntegrationTestBase {

    protected static EchoServer trustNoneTLSEchoServer;

    @BeforeClass
    public static void startTrustNoneTLSEchoServer() throws SSLException {
        if (trustNoneTLSEchoServer == null) {
            trustNoneTLSEchoServer = new EchoServer(SslContextBuilder
                .forServer(
                    X509Generator.privateKeyFromPEMFile("org/mockserver/netty/integration/tls/trustnoneechoserver/leaf-key-pkcs8.pem"),
                    X509Generator.x509FromPEMFile("org/mockserver/netty/integration/tls/trustnoneechoserver/leaf-cert.pem"),
                    X509Generator.x509FromPEMFile("org/mockserver/netty/integration/tls/trustnoneechoserver/ca.pem")
                )
                .trustManager(
                    X509Generator.x509FromPEMFile("org/mockserver/netty/integration/tls/trustnoneechoserver/leaf-cert.pem"),
                    X509Generator.x509FromPEMFile("org/mockserver/netty/integration/tls/trustnoneechoserver/ca.pem")
                )
                .clientAuth(ClientAuth.REQUIRE)
                .build());
        }
    }

    @AfterClass
    public static void stopTrustNoneTLSEchoServer() {
        if (trustNoneTLSEchoServer != null) {
            trustNoneTLSEchoServer.stop();
        }
    }

    @Test
    public void shouldForwardRequestInHTTPS() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
            )
            .forward(
                forward()
                    .withHost("127.0.0.1")
                    .withPort(secureEchoServer.getPort())
                    .withScheme(HttpForward.Scheme.HTTPS)
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
                    .withSecure(true)
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("Host", "127.0.0.1:" + secureEchoServer.getPort()),
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_http"),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReceiveRequestInHTTPSForwardRequestInHTTP() {
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
                    .withScheme(HttpForward.Scheme.HTTP)
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
                    .withSecure(true)
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("Host", "127.0.0.1:" + insecureEchoServer.getPort()),
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_http"),
                headersToIgnore)
        );

        insecureEchoServer
            .mockServerEventLog()
            .verify(
                verification()
                    .withRequest(
                        request()
                            .withSecure(false)
                            .withPath(calculatePath("echo"))
                            .withMethod("POST")
                            .withHeaders(
                                header("Host", "127.0.0.1:" + insecureEchoServer.getPort()),
                                header("x-test", "test_headers_and_body")
                            )
                            .withBody("an_example_body_http")
                    )
            );
    }

    @Test
    public void shouldReceiveRequestInHTTPForwardRequestInHTTPS() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
            )
            .forward(
                forward()
                    .withHost("127.0.0.1")
                    .withPort(secureEchoServer.getPort())
                    .withScheme(HttpForward.Scheme.HTTPS)
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
                        header("Host", "127.0.0.1:" + secureEchoServer.getPort()),
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_http"),
                headersToIgnore)
        );


        insecureEchoServer
            .mockServerEventLog()
            .verify(
                verification()
                    .withRequest(
                        request()
                            .withSecure(false)
                            .withPath(calculatePath("echo"))
                            .withMethod("POST")
                            .withHeaders(
                                header("Host", "127.0.0.1:" + insecureEchoServer.getPort()),
                                header("x-test", "test_headers_and_body")
                            )
                            .withBody("an_example_body_http")
                    )
            );
    }

    @Test
    public void shouldFailToForwardUntrustedHTTPS() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("trustNone"))
            )
            .forward(
                forward()
                    .withHost("127.0.0.1")
                    .withPort(trustNoneTLSEchoServer.getPort())
                    .withScheme(HttpForward.Scheme.HTTPS)
            );

        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
            )
            .forward(
                forward()
                    .withHost("127.0.0.1")
                    .withPort(secureEchoServer.getPort())
                    .withScheme(HttpForward.Scheme.HTTPS)
            );

        // then - invalid certificate returns 404
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("trustNone"))
                    .withMethod("POST")
                    .withHeaders(
                        header("Host", "127.0.0.1:" + trustNoneTLSEchoServer.getPort()),
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_http"),
                headersToIgnore)
        );

        // then - valid certificate returns response
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
                    .withSecure(true)
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("Host", "127.0.0.1:" + secureEchoServer.getPort()),
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_http"),
                headersToIgnore)
        );
    }

    @Test
    public void shouldForwardOverriddenRequest() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
                    .withSecure(true)
            )
            .forward(
                forwardOverriddenRequest(
                    request()
                        .withHeader("Host", "localhost:" + secureEchoServer.getPort())
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
                    .withSecure(true)
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_http"),
                headersToIgnore

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
                    .withSecure(true)
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body"),
                        header("x-echo-server-port", secureEchoServer.getPort())
                    )
                    .withBody("an_example_body_http"),
                headersToIgnore
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
                        "    'secure' : true," + NEW_LINE +
                        "    'headers' : [ {" + NEW_LINE +
                        "        'name' : \"Host\"," + NEW_LINE +
                        "        'values' : [ \"127.0.0.1:" + secureEchoServer.getPort() + "\" ]" + NEW_LINE +
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
                    .withSecure(true)
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_http"),
                headersToIgnore
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
            )
            .forward(
                forwardOverriddenRequest(
                    request()
                        .withHeader("Host", "localhost:" + secureEchoServer.getPort())
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
                    .withSecure(true)
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body"),
                headersToIgnore)
        );
        // - respond
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("test_headers_and_body")),
                headersToIgnore)
        );
        // - no response or forward
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("test_headers_and_body")),
                headersToIgnore)
        );
    }
}
