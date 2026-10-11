package org.mockserver.netty.proxy.connect;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.codec.HttpChunkLineLimiter;
import org.mockserver.codec.HttpLineEndSplitGuard;
import org.mockserver.netty.unification.HttpServerCodecResponsePairing;
import org.mockserver.configuration.Configuration;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpResponse;
import org.mockserver.netty.connection.HttpTransportTimer;
import org.mockserver.scheduler.Scheduler;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;

/**
 * EmbeddedChannel tests for {@link HttpConnectHandler} verifying:
 * - successResponse returns an empty HttpResponse (200 OK)
 * - failureResponse returns a 502 Bad Gateway HttpResponse
 * - removeCodecSupport correctly strips HTTP codec handlers from pipeline
 * - exceptionCaught writes failure response and closes channel
 */
public class HttpConnectHandlerTest {

    private Configuration configuration;
    private MockServerLogger mockServerLogger;
    private LifeCycle server;
    private HttpConnectHandler handler;

    @Before
    public void setUp() {
        configuration = configuration();
        mockServerLogger = new MockServerLogger();
        server = mock(LifeCycle.class);
        when(server.getScheduler()).thenReturn(mock(Scheduler.class));
        handler = new HttpConnectHandler(configuration, server, mockServerLogger, "example.com", 443);
    }

    @After
    public void tearDown() {
    }

    @Test
    public void shouldReturnEmptySuccessResponse() {
        // when
        Object response = handler.successResponse(null);

        // then
        assertThat("response should not be null", response, is(notNullValue()));
        assertThat(response, instanceOf(HttpResponse.class));
        HttpResponse httpResponse = (HttpResponse) response;
        // MockServer's response() factory returns a blank HttpResponse with null statusCode
        // (the HTTP serializer will default null to 200 when encoding)
        assertThat("status code should be null (defaults to 200 during serialization)",
            httpResponse.getStatusCode(), is(nullValue()));
    }

    @Test
    public void shouldReturnBadGatewayOnFailure() {
        // when
        Object response = handler.failureResponse(null);

        // then
        assertThat("response should not be null", response, is(notNullValue()));
        assertThat(response, instanceOf(HttpResponse.class));
        HttpResponse httpResponse = (HttpResponse) response;
        assertThat("status code should be 502 Bad Gateway",
            httpResponse.getStatusCode(), is(502));
    }

    @Test
    public void shouldRemoveHttpCodecHandlersFromPipeline() {
        // given - a pipeline with HTTP codec handlers and the HttpConnectHandler
        HttpChunkLineLimiter chunkLineLimiter = new HttpChunkLineLimiter(mockServerLogger);
        HttpServerCodecResponsePairing responsePairing = new HttpServerCodecResponsePairing();
        HttpServerCodec codec = new HttpServerCodec();
        HttpLineEndSplitGuard lineEndSplitGuard = new HttpLineEndSplitGuard(codec);
        HttpTransportTimer transportTimer = new HttpTransportTimer();
        EmbeddedChannel channel = new EmbeddedChannel(
            transportTimer.readMarker(),
            chunkLineLimiter.beforeCodec(),
            responsePairing.beforeCodec(),
            lineEndSplitGuard.beforeCodec(),
            codec,
            lineEndSplitGuard.afterCodec(),
            responsePairing.afterCodec(),
            chunkLineLimiter.afterCodec(),
            transportTimer,
            new HttpContentDecompressor(),
            new HttpObjectAggregator(1024),
            handler
        );
        try {
            // verify handlers are present before removal
            assertThat("HttpServerCodec should be present",
                channel.pipeline().get(HttpServerCodec.class), is(notNullValue()));
            assertThat("HttpContentDecompressor should be present",
                channel.pipeline().get(HttpContentDecompressor.class), is(notNullValue()));
            assertThat("HttpObjectAggregator should be present",
                channel.pipeline().get(HttpObjectAggregator.class), is(notNullValue()));
            assertThat(channel.pipeline().names(), hasItems("HttpChunkLineLimiter$BeforeCodec#0", "HttpChunkLineLimiter$AfterCodec#0", "HttpLineEndSplitGuard$BeforeCodec#0", "HttpLineEndSplitGuard$AfterCodec#0"));

            // when
            handler.removeCodecSupport(channel.pipeline().context(handler));

            // then - all HTTP handlers should be removed
            assertThat("HttpServerCodec should be removed",
                channel.pipeline().get(HttpServerCodec.class), is(nullValue()));
            assertThat("HttpContentDecompressor should be removed",
                channel.pipeline().get(HttpContentDecompressor.class), is(nullValue()));
            assertThat("HttpObjectAggregator should be removed",
                channel.pipeline().get(HttpObjectAggregator.class), is(nullValue()));
            assertThat("HttpTransportTimer should be removed, the tunnel carries no more HTTP exchanges",
                channel.pipeline().get(HttpTransportTimer.class), is(nullValue()));
            assertThat("its read marker should be removed with it",
                channel.pipeline().names(), everyItem(not(containsString("HttpTransportTimer"))));
            assertThat("the chunk-line limiter should be removed with the codec",
                channel.pipeline().names(), everyItem(not(containsString("HttpChunkLineLimiter"))));
            assertThat("the codec's response pairing should be removed with it",
                channel.pipeline().names(), everyItem(not(containsString("HttpServerCodecResponsePairing"))));
            assertThat("the codec's line-end guard should be removed with it",
                channel.pipeline().names(), everyItem(not(containsString("HttpLineEndSplitGuard"))));
            // HttpConnectHandler itself should also be removed
            assertThat("HttpConnectHandler should be removed",
                channel.pipeline().get(HttpConnectHandler.class), is(nullValue()));
        } finally {
            try {
                channel.finishAndReleaseAll();
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    public void shouldRemoveCodecSupportIdempotentlyWhenHandlersAbsent() {
        // given - a pipeline with NO HTTP codec handlers (only the connect handler)
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        try {
            // when - calling removeCodecSupport when handlers are not present should not throw
            handler.removeCodecSupport(channel.pipeline().context(handler));

            // then - no exception and handler is removed
            assertThat("HttpConnectHandler should be removed",
                channel.pipeline().get(HttpConnectHandler.class), is(nullValue()));
        } finally {
            try {
                channel.finishAndReleaseAll();
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    public void shouldCloseChannelOnException() {
        // given
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        try {
            assertTrue("channel should be active initially", channel.isActive());

            // when
            channel.pipeline().fireExceptionCaught(new RuntimeException("simulated connect failure"));
            channel.runPendingTasks();

            // then - exceptionCaught in RelayConnectHandler writes failure response and closes
            assertFalse("channel should be closed on exception", channel.isActive());
        } finally {
            try {
                channel.checkException();
            } catch (Exception ignored) {
            }
            try {
                channel.finishAndReleaseAll();
            } catch (Exception ignored) {
            }
        }
    }
}
