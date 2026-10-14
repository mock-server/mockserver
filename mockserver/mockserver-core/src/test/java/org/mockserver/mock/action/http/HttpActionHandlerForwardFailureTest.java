package org.mockserver.mock.action.http;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.ssl.NotSslRecordException;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.httpclient.SocketConnectionException;
import org.mockserver.httpclient.UndecodableResponseException;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpState;
import org.mockserver.mock.crud.CrudDispatcher;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpTemplate;
import org.mockserver.model.ProxyPassMapping;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.uuid.UUIDService;
import org.slf4j.event.Level;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;

/**
 * The {@code 502} a failed forward is answered with, and the entry it is logged with, for each reason the forward
 * client can name: a connection it could not set up from MockServer's configuration, TLS with the upstream, and an
 * HTTP/2 error from the upstream, and an HTTP/1.1 response that could not be decoded; and for a connection that could not
 * be made, its cause and what to change. The matched forward, unmatched proxy and proxy-pass routes answer each alike.
 */
public class HttpActionHandlerForwardFailureTest {

    private static final HttpForward ACTION = forward().withHost("upstream.example").withPort(8443).withScheme(HttpForward.Scheme.HTTPS);
    private static final InetSocketAddress UPSTREAM = InetSocketAddress.createUnresolved("upstream.example", 8443);

    private final List<LogEntry> logged = new CopyOnWriteArrayList<>();
    private final MockServerLogger mockServerLogger = new MockServerLogger(HttpActionHandlerForwardFailureTest.class) {
        @Override
        public boolean isEnabledForInstance(Level level) {
            return true;
        }

        @Override
        public void logEvent(LogEntry logEntry) {
            logged.add(logEntry);
        }
    };
    private HttpActionHandler actionHandler;
    private ResponseWriter responseWriter;
    private final HttpRequest request = request("/some_path").withHeader("Authorization", "Bearer secret-token");

    @Before
    public void createActionHandler() {
        actionHandler = new HttpActionHandler(configuration(), null, httpStateFor(mockServerLogger), null, null);
        responseWriter = mock(ResponseWriter.class);
    }

    private static HttpState httpStateFor(MockServerLogger mockServerLogger) {
        HttpState httpState = mock(HttpState.class);
        when(httpState.getMockServerLogger()).thenReturn(mockServerLogger);
        when(httpState.getUniqueLoopPreventionHeaderName()).thenReturn("x-forwarded-by");
        when(httpState.getUniqueLoopPreventionHeaderValue()).thenReturn("MockServer_" + UUIDService.getUUID());
        return httpState;
    }

    @Test
    public void shouldAnswerAConfigurationErrorWithItsReason() throws Exception {
        Throwable failure = configurationError("connection to upstream.example:8443 could not be set up: RuntimeException: Exception creating SSL context for client", new RuntimeException("Exception creating SSL context for client", new IllegalArgumentException("deep cause")));

        actionHandler.handleExceptionDuringForwardingRequest(ACTION, request, responseWriter, new CompletionException(failure));

        String reason = "connection to the upstream could not be set up: RuntimeException: Exception creating SSL context for client";
        assertThat(answer().getStatusCode(), is(502));
        assertThat(answer().getBodyAsString(), is(reason));
        assertThat("only the top-level reason: a deeper cause may quote the configured files", answer().getBodyAsString(), not(containsString("deep cause")));
        assertLoggedOnceAsAnError(reason);
    }

    @Test
    public void shouldAnswerAnUntrustedCertificateWithTheHandshakesReason() {
        Throwable failure = socketConnectionException("TLS handshake with upstream.example:8443 failed: SSLHandshakeException: PKIX path building failed", new SSLHandshakeException("PKIX path building failed"));

        actionHandler.handleExceptionDuringForwardingRequest(ACTION, request, responseWriter, failure);

        String reason = "TLS with the upstream failed: SSLHandshakeException: PKIX path building failed";
        assertThat(answer().getStatusCode(), is(502));
        assertThat(answer().getBodyAsString(), is(reason));
        assertLoggedOnceAsAnError(reason);
    }

    @Test
    public void shouldAnswerAFailedOpenSslHandshakeWithTheReasonInItsCause() {
        SSLHandshakeException openSsl = new SSLHandshakeException("General OpenSslEngine problem");
        openSsl.initCause(new java.security.cert.CertificateException("No subject alternative names matching IP address 127.0.0.1 found"));
        Throwable failure = socketConnectionException("TLS handshake failed", openSsl);

        actionHandler.handleExceptionDuringForwardingRequest(ACTION, request, responseWriter, failure);

        String reason = "TLS with the upstream failed: SSLHandshakeException: General OpenSslEngine problem; caused by CertificateException: No subject alternative names matching IP address 127.0.0.1 found";
        assertThat(answer().getBodyAsString(), is(reason));
        assertLoggedOnceAsAnError(reason);
    }

    @Test
    public void shouldAnswerAnUpstreamThatIsNotTlsWithoutItsBytes() {
        NotSslRecordException notTls = new NotSslRecordException("not an SSL/TLS record: " + "485454502f".repeat(1000));
        Throwable failure = socketConnectionException("TLS handshake failed", notTls);

        actionHandler.handleExceptionDuringForwardingRequest(ACTION, request, responseWriter, failure);

        String reason = "TLS with the upstream failed: NotSslRecordException: not an SSL/TLS record: 5000 bytes";
        assertThat(answer().getBodyAsString(), is(reason));
        assertLoggedOnceAsAnError(reason);
        LogEntry error = errors().get(0);
        assertThat(String.valueOf(error.getThrowable().getCause()), not(containsString("485454502f")));
    }

    @Test
    public void shouldAnswerAnHttp2ErrorWithItsCodeAndMessage() {
        Http2Exception protocolError = Http2Exception.connectionError(Http2Error.PROTOCOL_ERROR, "Frame of type %d must be associated with a stream.", 0);
        Throwable failure = socketConnectionException("HTTP/2 connection to upstream.example:8443 failed", protocolError);

        actionHandler.handleExceptionDuringForwardingRequest(ACTION, request, responseWriter, failure);

        String reason = "HTTP/2 error from the upstream: PROTOCOL_ERROR: Frame of type 0 must be associated with a stream.";
        assertThat(answer().getBodyAsString(), is(reason));
        assertLoggedOnceAsAnError(reason);
    }

    @Test
    public void shouldAnswerAResponseThatCouldNotBeDecodedWithTheDecodersReason() {
        Throwable failure = new UndecodableResponseException("response from upstream.example:8080 could not be decoded: IllegalArgumentException: No colon found", new IllegalArgumentException("No colon found"));

        actionHandler.handleExceptionDuringForwardingRequest(ACTION, request, responseWriter, new CompletionException(failure));

        String reason = "response from the upstream could not be decoded: IllegalArgumentException: No colon found";
        assertThat(answer().getStatusCode(), is(502));
        assertThat(answer().getBodyAsString(), is(reason));
        assertLoggedOnceAsAnError(reason);
    }

    @Test
    public void shouldAnswerAStatusLineThatIsNotHttpWithAtMostTheBoundOfIt() {
        String notHttp = "invalid version format: " + "A".repeat(5000);
        Throwable failure = new UndecodableResponseException("response from upstream could not be decoded", new IllegalArgumentException(notHttp));

        actionHandler.handleUnmatchedForwardFailure(failure, request, responseWriter, UPSTREAM, true);

        String prefix = "response from the upstream could not be decoded: IllegalArgumentException: invalid version format: AAA";
        assertThat(answer().getStatusCode(), is(502));
        assertThat(answer().getBodyAsString(), startsWith(prefix));
        assertThat(answer().getBodyAsString().length(), is("response from the upstream could not be decoded: IllegalArgumentException: ".length() + 256));
    }

    @Test
    public void shouldAnswerARefusedConnectionWithItsCauseAndWhatToChangeWithoutNamingTheUpstream() {
        actionHandler.handleExceptionDuringForwardingRequest(ACTION, request, responseWriter, new CompletionException(new ConnectException("Connection refused: upstream.example/127.0.0.1:8443")));

        String reason = "upstream refused the connection: check the upstream is running and listening on the port forwarded to";
        assertThat(answer().getStatusCode(), is(502));
        assertThat(answer().getBodyAsString(), is(reason));
        assertThat(errors(), empty());
        assertThat("the 502's entry names the cause", info().getMessage(configuration()), containsString(reason));
    }

    @Test
    public void shouldAnswerAProxiedRequestsConnectTimeoutWithoutNamingATimeoutThatDoesNotApply() {
        // a request proxied only because its Host is not MockServer's connects with a fixed one second timeout
        actionHandler.handleUnmatchedForwardFailure(new io.netty.channel.ConnectTimeoutException("connection timed out after 1000 ms: upstream.example/192.0.2.1:8443"), request, responseWriter, UPSTREAM, true);

        assertThat(answer().getStatusCode(), is(502));
        assertThat(answer().getBodyAsString(), is("connection to the upstream timed out: check the upstream is reachable from where MockServer runs (firewall, routing, VPN or an upstream proxy it must go through)"));
        assertThat(errors(), empty());
    }

    @Test
    public void shouldAnswerAProxiedRequestsHostThatDoesNotResolveWithItsCauseAndNotAsAnError() {
        actionHandler.handleUnmatchedForwardFailure(new java.util.concurrent.ExecutionException(new java.net.UnknownHostException("upstream.example")), request, responseWriter, UPSTREAM, false);

        assertThat(answer().getStatusCode(), is(502));
        assertThat(answer().getBodyAsString(), is("upstream host name could not be resolved: check it is spelled correctly and resolves where MockServer runs (its DNS, hosts file or container network)"));
        assertThat(errors(), empty());
    }

    private static final String LOOP_HINT = "the forward's target is the address MockServer received the request on, so the request may have been forwarded back to MockServer itself, where it can match the same expectation again and loop: check the forward's host and port";

    @Test
    public void shouldAddTheLoopHintToLocalPortsRunningOutOnAForwardBackToTheAddressTheRequestArrivedOn() {
        HttpRequest received = request("/some_path").withLocalAddress("127.0.0.1:1080");
        ResponseWriter writer = mock(ResponseWriter.class);

        actionHandler.handleExceptionDuringForwardingRequest(forward().withHost("localhost").withPort(1080), received, writer, new CompletionException(new java.net.SocketException("Can't assign requested address")));

        assertThat(written(writer, received).getStatusCode(), is(502));
        assertThat(written(writer, received).getBodyAsString(), is("MockServer ran out of local ports or open files to connect to the upstream: a forward whose target is MockServer itself repeats until this happens, so check the forward's host and port; otherwise lower the number of concurrent forwards or raise the open-file limit; " + LOOP_HINT));
        assertThat(errors(), empty());
    }

    @Test
    public void shouldKeepAnUnnamedFailuresOwnEntryAndAddTheLoopHintOnAForwardBackToTheAddressTheRequestArrivedOn() {
        HttpRequest received = request("/some_path").withLocalAddress("127.0.0.1:1080");
        ResponseWriter writer = mock(ResponseWriter.class);
        String timedOut = "Response was not received after 500 milliseconds, to make the proxy wait longer please use \"mockserver.maxSocketTimeout\" system property";

        actionHandler.handleExceptionDuringForwardingRequest(forward().withHost("127.0.0.1").withPort(1080), received, writer, new RuntimeException(timedOut));

        assertThat(written(writer, received).getStatusCode(), is(502));
        assertThat(written(writer, received).getBodyAsString(), is(LOOP_HINT));
        assertThat("the failure's own entry, as before", errors(), hasSize(1));
        assertThat(errors().get(0).getMessage(configuration()), containsString(timedOut));
        assertThat(info().getMessage(configuration()), containsString(timedOut + "; " + LOOP_HINT));
    }

    @Test
    public void shouldNotAddTheLoopHintToANamedCauseOnAForwardToTheAddressTheRequestArrivedOn() {
        // a loopback target on the same port can be another process when MockServer listens on one address
        HttpRequest received = request("/some_path").withLocalAddress("10.0.0.5:1080");
        ResponseWriter writer = mock(ResponseWriter.class);

        actionHandler.handleExceptionDuringForwardingRequest(forward().withHost("127.0.0.1").withPort(1080), received, writer, new ConnectException("Connection refused"));

        assertThat(written(writer, received).getBodyAsString(), is("upstream refused the connection: check the upstream is running and listening on the port forwarded to"));
    }

    @Test
    public void shouldAnswerAnUnnamedFailureOfAForwardElsewhereAsBefore() {
        HttpRequest received = request("/some_path").withLocalAddress("127.0.0.1:1080");
        ResponseWriter writer = mock(ResponseWriter.class);

        actionHandler.handleExceptionDuringForwardingRequest(forward().withHost("127.0.0.1").withPort(1081), received, writer, new RuntimeException("something else"));

        assertThat(written(writer, received).getBodyAsString(), is(nullValue()));
        assertThat(errors(), hasSize(1));
    }

    private static HttpResponse written(ResponseWriter writer, HttpRequest request) {
        ArgumentCaptor<HttpResponse> response = ArgumentCaptor.forClass(HttpResponse.class);
        verify(writer).writeResponse(eq(request), response.capture(), eq(false));
        return response.getValue();
    }

    @Test
    public void shouldNotTakeAFailedForwardToAnotherPortForALoop() {
        HttpRequest received = request("/some_path").withLocalAddress("127.0.0.1:1080");
        ResponseWriter writer = mock(ResponseWriter.class);

        actionHandler.handleExceptionDuringForwardingRequest(forward().withHost("127.0.0.1").withPort(1081), received, writer, new java.net.SocketException("Can't assign requested address"));

        assertThat(written(writer, received).getBodyAsString(), is("MockServer ran out of local ports or open files to connect to the upstream: a forward whose target is MockServer itself repeats until this happens, so check the forward's host and port; otherwise lower the number of concurrent forwards or raise the open-file limit"));
    }

    @Test
    public void shouldAnswerAConnectionFailureItCannotNameAsBefore() {
        actionHandler.handleExceptionDuringForwardingRequest(ACTION, request, responseWriter, new ConnectException("something unexpected"));

        assertThat(answer().getStatusCode(), is(502));
        assertThat(answer().getBodyAsString(), is(nullValue()));
        assertThat(errors(), empty());
    }

    @Test
    public void shouldNotLogAgainAForwardFailedForAnUnexpectedExceptionOnItsHttp2Connection() {
        // the connection's handler has logged the exception at ERROR with its stack trace
        Throwable failure = unexpectedExceptionOnHttp2Connection();

        actionHandler.handleExceptionDuringForwardingRequest(ACTION, request, responseWriter, failure);

        assertThat(answer().getStatusCode(), is(502));
        assertThat(errors(), empty());
    }

    @Test
    public void shouldNotLogAgainAProxiedRequestFailedForAnUnexpectedExceptionOnItsHttp2Connection() {
        Throwable failure = unexpectedExceptionOnHttp2Connection();

        actionHandler.handleUnmatchedForwardFailure(failure, request, responseWriter, UPSTREAM, false);

        assertThat(answer().getStatusCode(), is(502));
        assertThat(errors(), empty());
    }

    @Test
    public void shouldAnswerAProxiedRequestsFailureWithItsReason() {
        Throwable failure = socketConnectionException("TLS handshake failed", new SSLHandshakeException("PKIX path building failed"));

        actionHandler.handleUnmatchedForwardFailure(failure, request, responseWriter, UPSTREAM, true);

        String reason = "TLS with the upstream failed: SSLHandshakeException: PKIX path building failed";
        assertThat("not taken for an exploratory proxy's failed connect", answer().getBodyAsString(), is(reason));
        assertLoggedOnceAsAnError(reason);
    }

    @Test
    public void shouldAnswerAProxyPassResponseThatCouldNotBeDecodedAsTheForwardRouteDoes() throws Exception {
        Throwable failure = new UndecodableResponseException("response from upstream.example:8080 could not be decoded: IllegalArgumentException: No colon found", new IllegalArgumentException("No colon found"));

        HttpResponse answer = proxyPass(CompletableFuture.failedFuture(failure));

        String reason = "response from the upstream could not be decoded: IllegalArgumentException: No colon found";
        assertThat(answer.getStatusCode(), is(502));
        assertThat(answer.getBodyAsString(), is(reason));
        assertLoggedOnceAsAnError(reason);
        assertThat(errors().get(0).getMessage(configuration()), containsString("http://upstream.example:8080/api"));
    }

    @Test
    public void shouldAnswerAProxyPassTlsFailureAsTheForwardRouteDoes() throws Exception {
        Throwable failure = socketConnectionException("TLS handshake failed", new SSLHandshakeException("PKIX path building failed"));

        HttpResponse answer = proxyPass(CompletableFuture.failedFuture(failure));

        String reason = "TLS with the upstream failed: SSLHandshakeException: PKIX path building failed";
        assertThat(answer.getBodyAsString(), is(reason));
        assertLoggedOnceAsAnError(reason);
    }

    @Test
    public void shouldAnswerAProxyPassRefusedConnectionWithItsCause() throws Exception {
        HttpResponse answer = proxyPass(CompletableFuture.failedFuture(new ConnectException("Connection refused: upstream.example/127.0.0.1:8080")));

        String reason = "upstream refused the connection: check the upstream is running and listening on the port forwarded to";
        assertThat(answer.getStatusCode(), is(502));
        assertThat(answer.getBodyAsString(), is(reason));
        assertThat(errors(), empty());
        assertThat(info().getMessage(configuration()), containsString("proxy pass forwarding failed for http://upstream.example:8080/api: " + reason));
    }

    @Test
    public void shouldAnswerAProxyPassFailureWithoutAReasonAsBefore() throws Exception {
        HttpResponse answer = proxyPass(CompletableFuture.failedFuture(new ConnectException("something unexpected")));

        assertThat(answer.getStatusCode(), is(502));
        assertThat(answer.getBodyAsString(), is(nullValue()));
        assertThat(errors(), empty());
    }

    /**
     * Sends {@link #request} through a proxy-pass mapping whose upstream answers with {@code upstream}.
     */
    private HttpResponse proxyPass(CompletableFuture<HttpResponse> upstream) throws Exception {
        Configuration configuration = configuration().proxyPassMappings(List.of(ProxyPassMapping.proxyPass("/some_path", "http://upstream.example:8080/api")));
        Scheduler scheduler = new Scheduler(configuration, mockServerLogger);
        try {
            HttpState httpState = mock(HttpState.class);
            when(httpState.getMockServerLogger()).thenReturn(mockServerLogger);
            when(httpState.getScheduler()).thenReturn(scheduler);
            when(httpState.getUniqueLoopPreventionHeaderValue()).thenReturn("MockServer_" + UUIDService.getUUID());
            when(httpState.getCrudDispatcher()).thenReturn(new CrudDispatcher());
            HttpActionHandler proxyPassHandler = new HttpActionHandler(configuration, null, httpState, null, null);
            NettyHttpClient httpClient = mock(NettyHttpClient.class);
            when(httpClient.sendRequest(any(HttpRequest.class), any(InetSocketAddress.class))).thenReturn(upstream);
            Field httpClientField = HttpActionHandler.class.getDeclaredField("httpClient");
            httpClientField.setAccessible(true);
            httpClientField.set(proxyPassHandler, httpClient);

            proxyPassHandler.processAction(request, responseWriter, null, new HashSet<>(), false, true);

            verify(httpClient).sendRequest(any(HttpRequest.class), any(InetSocketAddress.class));
            return answer();
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    public void shouldLogAnUpstreamThatAnsweredJdkTlsInPlainTextWithoutItsBytes() throws Exception {
        String plainText = "HTTP/1.1 200 OK\r\ncontent-length: 4000\r\n\r\n" + "x".repeat(4000);
        Throwable notTls = jdkTlsClientFault(plainText);
        assertThat("Netty's JDK TLS handler dumps what it read", notTls.getCause(), instanceOf(NotSslRecordException.class));
        String reason = "TLS with the upstream failed: NotSslRecordException: not an SSL/TLS record: " + plainText.length() + " bytes";

        actionHandler.handleExceptionDuringForwardingRequest(ACTION, request, responseWriter, socketConnectionException("TLS handshake failed", notTls));
        actionHandler.handleUnmatchedForwardFailure(socketConnectionException("TLS handshake failed", notTls), request, mock(ResponseWriter.class), UPSTREAM, false);

        List<LogEntry> errors = errors();
        assertThat(errors, hasSize(2));
        for (LogEntry error : errors) {
            assertThat(error.getMessage(configuration()), containsString(reason));
            assertThat(error.getMessage(configuration()).length(), lessThan(plainText.length()));
            Throwable attached = error.getThrowable();
            assertThat(attached.getCause().getCause().toString(), is("io.netty.handler.ssl.NotSslRecordException: not an SSL/TLS record: " + plainText.length() + " bytes"));
            assertThat(attached.getCause().getCause().getStackTrace(), is(notTls.getCause().getStackTrace()));
        }
    }

    @Test
    public void shouldMaskACredentialBeforeAFaultsMessageIsCut() {
        HttpState httpState = mock(HttpState.class);
        when(httpState.getMockServerLogger()).thenReturn(mockServerLogger);
        HttpActionHandler redactingHandler = new HttpActionHandler(configuration().redactSecretsInLog(true), null, httpState, null, null);
        String token = "zq7XkLmNpR4sT8vW2yB6dF0hJ3";
        HttpRequest withToken = request("/some_path").withHeader("Authorization", "Bearer " + token);
        // the token straddles the cut at 256 characters
        SSLHandshakeException handshake = new SSLHandshakeException("p".repeat(230) + " " + token + " rejected");

        redactingHandler.handleExceptionDuringForwardingRequest(ACTION, withToken, mock(ResponseWriter.class), socketConnectionException("TLS handshake failed", handshake));
        redactingHandler.handleUnmatchedForwardFailure(socketConnectionException("TLS handshake failed", handshake), withToken, mock(ResponseWriter.class), UPSTREAM, false);

        List<LogEntry> errors = errors();
        assertThat(errors, hasSize(2));
        for (LogEntry error : errors) {
            org.mockserver.configuration.Configuration redacting = configuration().redactSecretsInLog(true);
            assertThat(error.getMessage(redacting), not(containsString(token.substring(0, 6))));
            Throwable attached = error.getThrowable(redacting);
            assertThat(attached.getCause().toString(), not(containsString(token.substring(0, 6))));
            assertThat(attached.getCause().toString(), containsString("***REDACTED***"));
        }
    }

    /**
     * What Netty's JDK TLS handler, as the forward client's, throws when the upstream answers with {@code plainText}.
     */
    private static Throwable jdkTlsClientFault(String plainText) throws SSLException {
        AtomicReference<Throwable> caught = new AtomicReference<>();
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(SslContextBuilder.forClient().sslProvider(SslProvider.JDK).trustManager(InsecureTrustManagerFactory.INSTANCE).build().newHandler(channel.alloc(), "upstream.example", 8443));
        channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                caught.compareAndSet(null, cause);
            }
        });
        channel.writeInbound(Unpooled.copiedBuffer(plainText, StandardCharsets.US_ASCII));
        channel.finishAndReleaseAll();
        return caught.get();
    }

    @Test
    public void shouldAnswerAFailedForwardResponseTemplateAsATemplateFailureWhenConfigured() {
        HttpActionHandler serverErrorHandler = new HttpActionHandler(configuration().templateFailureAsServerError(true), null, httpStateFor(mockServerLogger), null, null);
        RuntimeException engineFailure = new RuntimeException("Exception: TEST_EXCEPTION transforming template", new IllegalStateException("TEST_EXCEPTION"));
        TemplateFailureException templateFailure = new TemplateFailureException(HttpTemplate.TemplateType.VELOCITY, "forward response", engineFailure);

        serverErrorHandler.handleExceptionDuringForwardingRequest(ACTION, request, responseWriter, new ExecutionException(templateFailure));

        assertThat(answer().getStatusCode(), is(500));
        assertThat(answer().getFirstHeader("x-mockserver-template-error"), is("true"));
        assertThat(answer().getBodyAsString(), is("VELOCITY forward response template failed: TEST_EXCEPTION"));
        List<LogEntry> errors = errors();
        assertThat(errors, hasSize(1));
        assertThat("the engine's own failure is logged", errors.get(0).getThrowable(), is(engineFailure));
    }

    @Test
    public void shouldAnswerATemplateFailureAsAnyForwardFailureByDefault() {
        RuntimeException engineFailure = new RuntimeException("Exception: TEST_EXCEPTION transforming template", new IllegalStateException("TEST_EXCEPTION"));
        TemplateFailureException templateFailure = new TemplateFailureException(HttpTemplate.TemplateType.VELOCITY, "forward response", engineFailure);

        actionHandler.handleExceptionDuringForwardingRequest(ACTION, request, responseWriter, new ExecutionException(templateFailure));

        assertThat(answer().getStatusCode(), is(502));
        assertThat(answer().getFirstHeader("x-mockserver-template-error"), is(""));
    }

    private HttpResponse answer() {
        ArgumentCaptor<HttpResponse> response = ArgumentCaptor.forClass(HttpResponse.class);
        verify(responseWriter).writeResponse(eq(request), response.capture(), eq(false));
        return response.getValue();
    }

    private LogEntry info() {
        List<LogEntry> infos = logged.stream().filter(entry -> entry.getLogLevel() == Level.INFO && org.mockserver.log.model.LogEntryMessages.FORWARD_FAILURE_MESSAGE_FORMAT.equals(entry.getMessageFormat())).collect(Collectors.toList());
        assertThat(infos, hasSize(1));
        return infos.get(0);
    }

    private List<LogEntry> errors() {
        return logged.stream().filter(entry -> entry.getLogLevel() == Level.ERROR).collect(Collectors.toList());
    }

    private void assertLoggedOnceAsAnError(String reason) {
        List<LogEntry> errors = errors();
        assertThat(errors, hasSize(1));
        assertThat(errors.get(0).getHttpRequest(), is(request));
        assertThat(errors.get(0).getMessage(configuration()), containsString(reason));
        assertThat("the cause, for its stack trace", errors.get(0).getThrowable() != null, is(true));
    }

    /**
     * As {@code Http2ForwardConnectionExceptionHandler} fails a forward in flight on a connection it closes for an
     * exception it does not recognise.
     */
    private static Throwable unexpectedExceptionOnHttp2Connection() {
        return socketConnectionException("HTTP/2 connection to upstream.example:8443 failed: unexpected exception: IllegalStateException: unexpected", new IllegalStateException("unexpected"));
    }

    private static Throwable socketConnectionException(String message, Throwable cause) {
        return construct(SocketConnectionException.class, message, cause);
    }

    private static Throwable configurationError(String message, Throwable cause) {
        return construct(org.mockserver.httpclient.ClientConfigurationException.class, message, cause);
    }

    private static Throwable construct(Class<? extends Throwable> type, String message, Throwable cause) {
        try {
            Constructor<? extends Throwable> constructor = type.getDeclaredConstructor(String.class, Throwable.class);
            constructor.setAccessible(true);
            return constructor.newInstance(message, cause);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
