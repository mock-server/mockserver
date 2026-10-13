package org.mockserver.mock.action.http;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.ClientConfigurationException;
import org.mockserver.httpclient.HeaderLimitExceededException;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.httpclient.UpstreamProxyUnreachableException;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.proxyconfiguration.ForwardTargetBlockedException;

import java.lang.reflect.Constructor;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * End-to-end wiring of the forward retry policy and per-upstream circuit breaker through
 * {@link HttpForwardActionHandler}. Uses a mocked {@link NettyHttpClient} so the (retry,
 * circuit-breaker) decisions are exercised without a live upstream.
 *
 * <p>Mutates the {@link ForwardCircuitBreaker} singleton via {@code getInstance().reset()} so it is
 * registered in the sequential Surefire phase of {@code mockserver-core/pom.xml}.
 */
public class HttpForwardActionResilienceTest {

    private NettyHttpClient mockHttpClient;

    private static CompletableFuture<HttpResponse> ok() {
        return CompletableFuture.completedFuture(response().withStatusCode(200).withBody("ok"));
    }

    private static CompletableFuture<HttpResponse> failed(int statusCode) {
        return CompletableFuture.completedFuture(response().withStatusCode(statusCode));
    }

    @Before
    public void setup() {
        mockHttpClient = mock(NettyHttpClient.class);
        ForwardCircuitBreaker.getInstance().reset();
    }

    @After
    public void teardown() {
        ForwardCircuitBreaker.getInstance().reset();
    }

    private HttpForwardActionHandler handlerWith(Configuration configuration) {
        return new HttpForwardActionHandler(mock(MockServerLogger.class), configuration, mockHttpClient);
    }

    private static HttpForward upstream() {
        return forward().withHost("upstream.example").withPort(8080).withScheme(HttpForward.Scheme.HTTP);
    }

    @Test
    public void shouldNotRetryByDefault() throws Exception {
        // given - default configuration (retry disabled)
        Configuration configuration = Configuration.configuration();
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any(InetSocketAddress.class))).thenReturn(failed(503));

        // when
        HttpResponse result = handlerWith(configuration)
            .handle(upstream(), request().withMethod("GET").withPath("/x"))
            .getHttpResponse()
            .get(5, TimeUnit.SECONDS);

        // then - single attempt, 503 surfaced unchanged
        assertThat(result.getStatusCode(), is(503));
        verify(mockHttpClient, times(1)).sendRequest(any(HttpRequest.class), any(InetSocketAddress.class));
    }

    @Test
    public void shouldRetryIdempotentRequestAndSucceedAfterFailures() throws Exception {
        // given - 2 retries; first two attempts 503, third 200
        Configuration configuration = Configuration.configuration()
            .forwardProxyRetryCount(2)
            .forwardProxyRetryBackoffMillis(0L);
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any(InetSocketAddress.class)))
            .thenReturn(failed(503), failed(503), ok());

        // when
        HttpResponse result = handlerWith(configuration)
            .handle(upstream(), request().withMethod("GET").withPath("/x"))
            .getHttpResponse()
            .get(5, TimeUnit.SECONDS);

        // then
        assertThat(result.getStatusCode(), is(200));
        verify(mockHttpClient, times(3)).sendRequest(any(HttpRequest.class), any(InetSocketAddress.class));
    }

    @Test
    public void shouldNotRetryNonIdempotentRequest() throws Exception {
        Configuration configuration = Configuration.configuration()
            .forwardProxyRetryCount(3)
            .forwardProxyRetryBackoffMillis(0L);
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any(InetSocketAddress.class))).thenReturn(failed(503));

        HttpResponse result = handlerWith(configuration)
            .handle(upstream(), request().withMethod("POST").withPath("/x"))
            .getHttpResponse()
            .get(5, TimeUnit.SECONDS);

        assertThat(result.getStatusCode(), is(503));
        verify(mockHttpClient, times(1)).sendRequest(any(HttpRequest.class), any(InetSocketAddress.class));
    }

    @Test
    public void shouldOpenCircuitAfterConsecutiveFailuresAndFailFast() throws Exception {
        // given - circuit breaker enabled, threshold 3, retry disabled
        Configuration configuration = Configuration.configuration()
            .forwardProxyCircuitBreakerEnabled(true)
            .forwardProxyCircuitBreakerFailureThreshold(3)
            .forwardProxyCircuitBreakerWindowMillis(60_000L);
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any(InetSocketAddress.class))).thenReturn(failed(503));

        HttpForwardActionHandler handler = handlerWith(configuration);

        // when - 3 consecutive failures open the breaker
        for (int i = 0; i < 3; i++) {
            handler.handle(upstream(), request().withMethod("GET").withPath("/x"))
                .getHttpResponse().get(5, TimeUnit.SECONDS);
        }

        // then - the breaker is now open for this upstream
        assertThat(ForwardCircuitBreaker.getInstance().isOpen("upstream.example:8080"), is(true));
        assertThat(MetricsHelper.openCount(), is(1));

        // and - the next request fails fast with a 503 WITHOUT reaching the client
        reset(mockHttpClient);
        HttpResponse fastFail = handler.handle(upstream(), request().withMethod("GET").withPath("/x"))
            .getHttpResponse().get(5, TimeUnit.SECONDS);
        assertThat(fastFail.getStatusCode(), is(503));
        assertThat(fastFail.getBodyAsString(), containsString("circuit breaker open"));
        verify(mockHttpClient, never()).sendRequest(any(HttpRequest.class), any(InetSocketAddress.class));
    }

    @Test
    public void shouldNotCountAHeaderLimitRefusalAgainstTheCircuitBreakerNorRetryIt() throws Exception {
        // given - breaker enabled with threshold 3 and two retries; the upstream answers, with headers over the limit
        Configuration configuration = Configuration.configuration()
            .forwardProxyCircuitBreakerEnabled(true)
            .forwardProxyCircuitBreakerFailureThreshold(3)
            .forwardProxyCircuitBreakerWindowMillis(60_000L)
            .forwardProxyRetryCount(2)
            .forwardProxyRetryBackoffMillis(0L);
        CompletableFuture<HttpResponse> refused = new CompletableFuture<>();
        refused.completeExceptionally(new HeaderLimitExceededException("upstream response headers are larger than maxHeaderSize (262144 bytes)"));
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any(InetSocketAddress.class))).thenReturn(refused);
        HttpForwardActionHandler handler = handlerWith(configuration);

        // when - more refusals than the threshold
        for (int i = 0; i < 5; i++) {
            try {
                handler.handle(upstream(), request().withMethod("GET").withPath("/x")).getHttpResponse().get(5, TimeUnit.SECONDS);
                throw new AssertionError("expected the refusal");
            } catch (ExecutionException failed) {
                assertThat(HeaderLimitExceededException.in(failed), is(notNullValue()));
            }
        }

        // then - the upstream is reachable, so the breaker stays closed, and each request was sent once
        assertThat(ForwardCircuitBreaker.getInstance().isOpen("upstream.example:8080"), is(false));
        assertThat(MetricsHelper.openCount(), is(0));
        verify(mockHttpClient, times(5)).sendRequest(any(HttpRequest.class), any(InetSocketAddress.class));

        // and - any other failure still counts: three of them open the breaker
        reset(mockHttpClient);
        CompletableFuture<HttpResponse> connectionReset = new CompletableFuture<>();
        connectionReset.completeExceptionally(new java.io.IOException("Connection reset"));
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any(InetSocketAddress.class))).thenReturn(connectionReset);
        for (int i = 0; i < 3; i++) {
            try {
                handler.handle(upstream(), request().withMethod("POST").withPath("/x")).getHttpResponse().get(5, TimeUnit.SECONDS);
            } catch (ExecutionException failed) {
                assertThat(HeaderLimitExceededException.in(failed), is(nullValue()));
            }
        }
        assertThat(ForwardCircuitBreaker.getInstance().isOpen("upstream.example:8080"), is(true));
    }

    @Test
    public void shouldNotCountAConfigurationErrorAgainstTheCircuitBreakerNorRetryIt() throws Exception {
        // given - breaker enabled with threshold 3 and two retries; the connection cannot be set up from the configuration
        Configuration configuration = Configuration.configuration()
            .forwardProxyCircuitBreakerEnabled(true)
            .forwardProxyCircuitBreakerFailureThreshold(3)
            .forwardProxyCircuitBreakerWindowMillis(60_000L)
            .forwardProxyRetryCount(2)
            .forwardProxyRetryBackoffMillis(0L);
        Constructor<ClientConfigurationException> constructor = ClientConfigurationException.class.getDeclaredConstructor(String.class, Throwable.class);
        constructor.setAccessible(true);
        CompletableFuture<HttpResponse> notSetUp = new CompletableFuture<>();
        notSetUp.completeExceptionally(constructor.newInstance("connection to upstream.example:8080 could not be set up: RuntimeException: Exception creating SSL context for client", new RuntimeException("Exception creating SSL context for client")));
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any(InetSocketAddress.class))).thenReturn(notSetUp);
        HttpForwardActionHandler handler = handlerWith(configuration);

        // when - more of them than the threshold
        for (int i = 0; i < 5; i++) {
            ExecutionException failed = org.junit.Assert.assertThrows(ExecutionException.class, () -> handler.handle(upstream(), request().withMethod("GET").withPath("/x")).getHttpResponse().get(5, TimeUnit.SECONDS));
            assertThat(ClientConfigurationException.in(failed), is(notNullValue()));
        }

        // then - the upstream was never reached, so the breaker stays closed, and each request was sent once
        assertThat(ForwardCircuitBreaker.getInstance().isOpen("upstream.example:8080"), is(false));
        verify(mockHttpClient, times(5)).sendRequest(any(HttpRequest.class), any(InetSocketAddress.class));
    }

    @Test
    public void shouldNotCountATargetTheForwardClientRefusedAgainstTheCircuitBreakerNorRetryIt() throws Exception {
        // given - breaker enabled with threshold 3 and two retries; the forward client refuses the address it looked up
        Configuration configuration = Configuration.configuration()
            .forwardProxyCircuitBreakerEnabled(true)
            .forwardProxyCircuitBreakerFailureThreshold(3)
            .forwardProxyCircuitBreakerWindowMillis(60_000L)
            .forwardProxyRetryCount(2)
            .forwardProxyRetryBackoffMillis(0L);
        CompletableFuture<HttpResponse> refused = new CompletableFuture<>();
        refused.completeExceptionally(new ForwardTargetBlockedException("Forward to loopback address blocked: upstream.example"));
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any(InetSocketAddress.class))).thenReturn(refused);
        HttpForwardActionHandler handler = handlerWith(configuration);

        // when - more refusals than the threshold
        for (int i = 0; i < 5; i++) {
            ExecutionException failed = org.junit.Assert.assertThrows(ExecutionException.class, () -> handler.handle(upstream(), request().withMethod("GET").withPath("/x")).getHttpResponse().get(5, TimeUnit.SECONDS));
            assertThat(ForwardTargetBlockedException.in(failed), is(notNullValue()));
        }

        // then - nothing was sent to the upstream, so the breaker stays closed, and no request was retried
        assertThat(ForwardCircuitBreaker.getInstance().isOpen("upstream.example:8080"), is(false));
        verify(mockHttpClient, times(5)).sendRequest(any(HttpRequest.class), any(InetSocketAddress.class));
    }

    @Test
    public void shouldNotCountAnUnreachableUpstreamProxyAgainstTheCircuitBreakerNorRetryIt() throws Exception {
        // given - breaker enabled with threshold 3 and two retries; the configured upstream proxy cannot be reached
        Configuration configuration = Configuration.configuration()
            .forwardProxyCircuitBreakerEnabled(true)
            .forwardProxyCircuitBreakerFailureThreshold(3)
            .forwardProxyCircuitBreakerWindowMillis(60_000L)
            .forwardProxyRetryCount(2)
            .forwardProxyRetryBackoffMillis(0L);
        Throwable unreachable = upstreamProxyUnreachable();
        CompletableFuture<HttpResponse> failed = new CompletableFuture<>();
        failed.completeExceptionally(unreachable);
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any(InetSocketAddress.class))).thenReturn(failed);
        HttpForwardActionHandler handler = handlerWith(configuration);

        // when - more of them than the threshold
        for (int i = 0; i < 5; i++) {
            ExecutionException thrown = org.junit.Assert.assertThrows(ExecutionException.class, () -> handler.handle(upstream(), request().withMethod("GET").withPath("/x")).getHttpResponse().get(5, TimeUnit.SECONDS));
            assertThat(thrown.getCause(), is(sameInstance(unreachable)));
        }

        // then - the target behind the proxy was never reached, so its breaker stays closed, and no request was retried
        assertThat(ForwardCircuitBreaker.getInstance().isOpen("upstream.example:8080"), is(false));
        verify(mockHttpClient, times(5)).sendRequest(any(HttpRequest.class), any(InetSocketAddress.class));
    }

    @Test
    public void shouldNotCountAForwardHttpProxyTheForwardClientCannotReachAgainstTheTargetNorRetryIt() throws Exception {
        // given - breaker enabled with threshold 1 and two retries; forwardHttpProxy names a port nothing listens on
        Configuration configuration = Configuration.configuration()
            .forwardProxyCircuitBreakerEnabled(true)
            .forwardProxyCircuitBreakerFailureThreshold(1)
            .forwardProxyCircuitBreakerWindowMillis(60_000L)
            .forwardProxyRetryCount(2)
            .forwardProxyRetryBackoffMillis(0L)
            .socketConnectionTimeoutInMillis(5_000L)
            .forwardConnectionPoolEnabled(false);
        InetSocketAddress refusingProxy;
        try (java.net.ServerSocket closed = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            refusingProxy = new InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), closed.getLocalPort());
        }
        io.netty.channel.EventLoopGroup group = new io.netty.channel.nio.NioEventLoopGroup(1);
        try {
            java.util.concurrent.atomic.AtomicInteger sends = new java.util.concurrent.atomic.AtomicInteger();
            NettyHttpClient throughProxy = new NettyHttpClient(configuration, mock(MockServerLogger.class), group, java.util.Collections.singletonList(org.mockserver.proxyconfiguration.ProxyConfiguration.proxyConfiguration(org.mockserver.proxyconfiguration.ProxyConfiguration.Type.HTTP, refusingProxy)), true) {
                @Override
                public CompletableFuture<HttpResponse> sendRequest(HttpRequest httpRequest, InetSocketAddress remoteAddress) {
                    sends.incrementAndGet();
                    return super.sendRequest(httpRequest, remoteAddress);
                }
            };
            HttpForwardActionHandler handler = new HttpForwardActionHandler(mock(MockServerLogger.class), configuration, throughProxy);

            // when - three forwards, each failing to reach the proxy
            for (int i = 0; i < 3; i++) {
                ExecutionException thrown = org.junit.Assert.assertThrows(ExecutionException.class, () -> handler.handle(upstream(), request().withMethod("GET").withPath("/x")).getHttpResponse().get(30, TimeUnit.SECONDS));
                assertThat(UpstreamProxyUnreachableException.in(thrown), is(notNullValue()));
            }

            // then - the target's breaker never opened, and each forward was sent once
            assertThat(ForwardCircuitBreaker.getInstance().isOpen("upstream.example:8080"), is(false));
            assertThat(sends.get(), is(3));
        } finally {
            group.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).syncUninterruptibly();
        }
    }

    @Test
    public void shouldNotResetTheFailureCountForAnUnreachableUpstreamProxy() throws Exception {
        assertARunOfNeutralOutcomesDoesNotResetTheFailureCount(upstreamProxyUnreachable());
    }

    @Test
    public void shouldNeitherCloseNorStrandAHalfOpenBreakerForAnUnreachableUpstreamProxy() throws Exception {
        assertANeutralHalfOpenTrialLeavesTheBreakerOpenAndReleasesTheTrial(upstreamProxyUnreachable());
    }

    @Test
    public void shouldNotResetTheFailureCountForATargetTheForwardClientRefused() throws Exception {
        assertARunOfNeutralOutcomesDoesNotResetTheFailureCount(new ForwardTargetBlockedException("Forward to loopback address blocked: upstream.example"));
    }

    @Test
    public void shouldNotResetTheFailureCountForAConfigurationError() throws Exception {
        assertARunOfNeutralOutcomesDoesNotResetTheFailureCount(configurationError());
    }

    @Test
    public void shouldNotResetTheFailureCountForAHeaderLimitRefusal() throws Exception {
        assertARunOfNeutralOutcomesDoesNotResetTheFailureCount(headerLimitRefusal());
    }

    private void assertARunOfNeutralOutcomesDoesNotResetTheFailureCount(Throwable neutral) throws Exception {
        // given - breaker enabled, threshold 3, retry disabled
        Configuration configuration = Configuration.configuration()
            .forwardProxyCircuitBreakerEnabled(true)
            .forwardProxyCircuitBreakerFailureThreshold(3)
            .forwardProxyCircuitBreakerWindowMillis(60_000L);
        HttpForwardActionHandler handler = handlerWith(configuration);

        // when - two real failures, then a run of outcomes that say nothing about the upstream, then a third failure
        forwardFailingWith(handler, new java.io.IOException("Connection reset"));
        forwardFailingWith(handler, new java.io.IOException("Connection reset"));
        assertThat(ForwardCircuitBreaker.getInstance().isOpen("upstream.example:8080"), is(false));
        for (int i = 0; i < 5; i++) {
            forwardFailingWith(handler, neutral);
        }
        assertThat(ForwardCircuitBreaker.getInstance().isOpen("upstream.example:8080"), is(false));
        forwardFailingWith(handler, new java.io.IOException("Connection reset"));

        // then - the three real failures are consecutive, so the breaker opens
        assertThat(ForwardCircuitBreaker.getInstance().isOpen("upstream.example:8080"), is(true));
    }

    @Test
    public void shouldNeitherCloseNorStrandAHalfOpenBreakerForAConfigurationError() throws Exception {
        assertANeutralHalfOpenTrialLeavesTheBreakerOpenAndReleasesTheTrial(configurationError());
    }

    @Test
    public void shouldNeitherCloseNorStrandAHalfOpenBreakerForAHeaderLimitRefusal() throws Exception {
        assertANeutralHalfOpenTrialLeavesTheBreakerOpenAndReleasesTheTrial(headerLimitRefusal());
    }

    private void assertANeutralHalfOpenTrialLeavesTheBreakerOpenAndReleasesTheTrial(Throwable neutral) throws Exception {
        // given - breaker enabled, threshold 1, a 1ms window so the breaker reaches half-open
        Configuration configuration = Configuration.configuration()
            .forwardProxyCircuitBreakerEnabled(true)
            .forwardProxyCircuitBreakerFailureThreshold(1)
            .forwardProxyCircuitBreakerWindowMillis(1L);
        HttpForwardActionHandler handler = handlerWith(configuration);
        forwardFailingWith(handler, new java.io.IOException("Connection refused"));
        assertThat(ForwardCircuitBreaker.getInstance().isOpen("upstream.example:8080"), is(true));
        Thread.sleep(5);

        // when - the half-open trial ends in an outcome that says nothing about the upstream
        forwardFailingWith(handler, neutral);

        // then - the breaker is still open: the upstream has not been shown to have recovered
        assertThat(ForwardCircuitBreaker.getInstance().isOpen("upstream.example:8080"), is(true));
        assertThat(MetricsHelper.openCount(), is(1));

        // and - the trial was released, so the next request probes the upstream, and its success closes the breaker
        reset(mockHttpClient);
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any(InetSocketAddress.class))).thenReturn(ok());
        HttpResponse probe = handler.handle(upstream(), request().withMethod("GET").withPath("/x")).getHttpResponse().get(5, TimeUnit.SECONDS);
        assertThat(probe.getStatusCode(), is(200));
        verify(mockHttpClient, times(1)).sendRequest(any(HttpRequest.class), any(InetSocketAddress.class));
        assertThat(ForwardCircuitBreaker.getInstance().isOpen("upstream.example:8080"), is(false));
    }

    @Test
    public void shouldNeitherCountNorTakeTheHalfOpenTrialForATargetRefusedByForwardProxyBlockPrivateNetworks() throws Exception {
        // given - the block on, and a half-open breaker for a loopback upstream
        Configuration configuration = Configuration.configuration()
            .forwardProxyBlockPrivateNetworks(true)
            .forwardProxyCircuitBreakerEnabled(true)
            .forwardProxyCircuitBreakerFailureThreshold(1)
            .forwardProxyCircuitBreakerWindowMillis(1L);
        String loopback = "127.0.0.1:8080";
        ForwardCircuitBreaker.getInstance().recordFailure(configuration, loopback);
        Thread.sleep(5);
        HttpForwardActionHandler handler = handlerWith(configuration);

        // when - forwards to the loopback upstream are refused
        for (int i = 0; i < 3; i++) {
            HttpResponse refused = handler.handle(forward().withHost("127.0.0.1").withPort(8080).withScheme(HttpForward.Scheme.HTTP), request().withMethod("GET").withPath("/x"))
                .getHttpResponse().get(5, TimeUnit.SECONDS);
            assertThat(refused.getStatusCode(), is(502));
        }

        // then - nothing was sent, the breaker is still open, and the half-open trial is still free
        verify(mockHttpClient, never()).sendRequest(any(HttpRequest.class), any(InetSocketAddress.class));
        assertThat(ForwardCircuitBreaker.getInstance().isOpen(loopback), is(true));
        assertThat(ForwardCircuitBreaker.getInstance().allowRequest(configuration, loopback), is(true));
    }

    private void forwardFailingWith(HttpForwardActionHandler handler, Throwable failure) throws Exception {
        reset(mockHttpClient);
        CompletableFuture<HttpResponse> failed = new CompletableFuture<>();
        failed.completeExceptionally(failure);
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any(InetSocketAddress.class))).thenReturn(failed);
        // POST is never retried, so each forward is one outcome
        ExecutionException thrown = org.junit.Assert.assertThrows(ExecutionException.class, () -> handler.handle(upstream(), request().withMethod("POST").withPath("/x")).getHttpResponse().get(5, TimeUnit.SECONDS));
        assertThat(thrown.getCause(), is(sameInstance(failure)));
        verify(mockHttpClient, times(1)).sendRequest(any(HttpRequest.class), any(InetSocketAddress.class));
    }

    private static Throwable headerLimitRefusal() {
        return new HeaderLimitExceededException("upstream response headers are larger than maxHeaderSize (262144 bytes)");
    }

    private static Throwable upstreamProxyUnreachable() throws Exception {
        Constructor<UpstreamProxyUnreachableException> constructor = UpstreamProxyUnreachableException.class.getDeclaredConstructor(InetSocketAddress.class, String.class, Throwable.class);
        constructor.setAccessible(true);
        return constructor.newInstance(InetSocketAddress.createUnresolved("proxy.example", 3128), "forwardHttpProxy", new java.net.ConnectException("Connection refused"));
    }

    private static Throwable configurationError() throws Exception {
        Constructor<ClientConfigurationException> constructor = ClientConfigurationException.class.getDeclaredConstructor(String.class, Throwable.class);
        constructor.setAccessible(true);
        return constructor.newInstance("connection to upstream.example:8080 could not be set up: RuntimeException: Exception creating SSL context for client", new RuntimeException("Exception creating SSL context for client"));
    }

    @Test
    public void shouldNotStrandOpenWhenHalfOpenTrialThrowsSynchronously() throws Exception {
        // given - breaker enabled, threshold 3, short window so we can reach half-open
        Configuration configuration = Configuration.configuration()
            .forwardProxyCircuitBreakerEnabled(true)
            .forwardProxyCircuitBreakerFailureThreshold(3)
            .forwardProxyCircuitBreakerWindowMillis(1L);
        // every forward attempt throws synchronously (e.g. connection refused before any future)
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any(InetSocketAddress.class)))
            .thenThrow(new RuntimeException("connection refused"));

        HttpForwardActionHandler handler = handlerWith(configuration);

        // when - 3 synchronous failures open the breaker (proving sync failures are counted)
        for (int i = 0; i < 3; i++) {
            handler.handle(upstream(), request().withMethod("GET").withPath("/x"))
                .getHttpResponse().get(5, TimeUnit.SECONDS);
        }
        assertThat(ForwardCircuitBreaker.getInstance().isOpen("upstream.example:8080"), is(true));

        // and - after the 1ms window a half-open trial is admitted but also throws synchronously
        Thread.sleep(5);
        handler.handle(upstream(), request().withMethod("GET").withPath("/x"))
            .getHttpResponse().get(5, TimeUnit.SECONDS);

        // then - the breaker is NOT stranded: the failed sync trial released the slot and re-opened,
        // so after another window a fresh trial is admitted again (recovery can still be probed)
        assertThat(ForwardCircuitBreaker.getInstance().isOpen("upstream.example:8080"), is(true));
        Thread.sleep(5);
        // a now-recovering upstream returns 200 on the next admitted trial and the breaker closes
        reset(mockHttpClient);
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any(InetSocketAddress.class))).thenReturn(ok());
        HttpResponse recovered = handler.handle(upstream(), request().withMethod("GET").withPath("/x"))
            .getHttpResponse().get(5, TimeUnit.SECONDS);
        assertThat(recovered.getStatusCode(), is(200));
        assertThat(ForwardCircuitBreaker.getInstance().isOpen("upstream.example:8080"), is(false));
        assertThat(MetricsHelper.openCount(), is(0));
    }

    @Test
    public void shouldNotInterfereWhenCircuitBreakerDisabled() throws Exception {
        // given - breaker disabled (default), every request reaches the upstream regardless of failures
        Configuration configuration = Configuration.configuration();
        when(mockHttpClient.sendRequest(any(HttpRequest.class), any(InetSocketAddress.class))).thenReturn(failed(503));

        HttpForwardActionHandler handler = handlerWith(configuration);

        for (int i = 0; i < 10; i++) {
            HttpResponse result = handler.handle(upstream(), request().withMethod("GET").withPath("/x"))
                .getHttpResponse().get(5, TimeUnit.SECONDS);
            assertThat(result.getStatusCode(), is(503));
        }
        verify(mockHttpClient, times(10)).sendRequest(any(HttpRequest.class), any(InetSocketAddress.class));
        assertThat(MetricsHelper.openCount(), is(0));
    }

    /** Reads the live open-circuit count exposed by Metrics (the gauge source). */
    private static final class MetricsHelper {
        static int openCount() {
            return org.mockserver.metrics.Metrics.getOpenUpstreamCircuitCount();
        }
    }
}
