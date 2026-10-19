package org.mockserver.metrics;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockserver.configuration.Configuration;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;

/**
 * The metrics endpoint writes its response directly to the Netty channel rather
 * than going through ResponseWriter, so CORS headers must be added by the
 * handler itself — otherwise the dashboard served from another origin (e.g. a
 * dev server on :3000) is blocked by the browser when it fetches metrics.
 */
public class MetricsHandlerTest {

    private HttpResponse renderAndCapture(Configuration configuration, HttpRequest request) throws Exception {
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        new MetricsHandler(configuration).renderMetrics(ctx, request);
        ArgumentCaptor<HttpResponse> responseCaptor = ArgumentCaptor.forClass(HttpResponse.class);
        verify(ctx).writeAndFlush(responseCaptor.capture());
        return responseCaptor.getValue();
    }

    @Test
    public void shouldReflectOriginWhenMetricsEnabled() throws Exception {
        // given - a cross-origin browser GET for metrics, keep-alive so the
        // handler takes the simple write path
        HttpRequest request = request()
            .withMethod("GET")
            .withHeader("origin", "http://localhost:3000")
            .withKeepAlive(true);

        // when
        HttpResponse response = renderAndCapture(configuration().metricsEnabled(true), request);

        // then - the metrics body is served (content-type header set by the
        // exposition writer, not the 404 path) and the requesting origin is reflected
        assertThat(response.getStatusCode(), is(200));
        assertThat(response.getFirstHeader("content-type"), notNullValue());
        assertThat(response.getFirstHeader("access-control-allow-origin"), is("http://localhost:3000"));
    }

    @Test
    public void shouldReflectOriginOnDisabledNotFound() throws Exception {
        // given - metrics disabled (the default); the UI still needs to read the
        // 404 cross-origin to show its "metrics disabled" guidance rather than a
        // CORS fetch error
        HttpRequest request = request()
            .withMethod("GET")
            .withHeader("origin", "http://localhost:3000")
            .withKeepAlive(true);

        // when
        HttpResponse response = renderAndCapture(configuration(), request);

        // then
        assertThat(response.getStatusCode(), is(404));
        assertThat(response.getFirstHeader("access-control-allow-origin"), is("http://localhost:3000"));
    }

    /**
     * The same ResponseWriter bypass that made the CORS headers above necessary also means nothing
     * copies the request's HTTP/2 stream id onto the response. The response mapper reads that field
     * (not a header), so without it the scrape goes out on a new server-initiated stream and a
     * Prometheus client over HTTP/2 receives nothing and hangs. Same defect class as GitHub #2419.
     */
    @Test
    public void shouldSendMetricsDownTheRequestHttp2Stream() throws Exception {
        // given - a scrape that arrived on HTTP/2 stream 9
        HttpRequest request = request()
            .withMethod("GET")
            .withStreamId(9)
            .withKeepAlive(true);

        // when
        HttpResponse response = renderAndCapture(configuration().metricsEnabled(true), request);

        // then
        assertThat(response.getStreamId(), is(9));
    }

    @Test
    public void shouldSendDisabledNotFoundDownTheRequestHttp2Stream() throws Exception {
        // given - the disabled-state 404 must reach the client too, or the dashboard hangs
        HttpRequest request = request()
            .withMethod("GET")
            .withStreamId(9)
            .withKeepAlive(true);

        // when
        HttpResponse response = renderAndCapture(configuration(), request);

        // then
        assertThat(response.getStatusCode(), is(404));
        assertThat(response.getStreamId(), is(9));
    }

    @Test
    public void shouldServeMetricsAndCloseWhenKeepAliveIsUnset() throws Exception {
        // given - a request built in code carries no keep-alive flag; it must be treated as close
        HttpRequest request = request().withMethod("GET");
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        ChannelFuture writeFuture = mock(ChannelFuture.class);
        when(ctx.writeAndFlush(any())).thenReturn(writeFuture);

        // when
        new MetricsHandler(configuration().metricsEnabled(true)).renderMetrics(ctx, request);

        // then
        ArgumentCaptor<HttpResponse> responseCaptor = ArgumentCaptor.forClass(HttpResponse.class);
        verify(ctx).writeAndFlush(responseCaptor.capture());
        assertThat(responseCaptor.getValue().getStatusCode(), is(200));
        verify(writeFuture).addListener(ChannelFutureListener.CLOSE);
    }

    @Test
    public void shouldNotSetStreamIdOnHttp1() throws Exception {
        // given - an HTTP/1.1 scrape has no stream id
        HttpRequest request = request().withMethod("GET").withKeepAlive(true);

        // when
        HttpResponse response = renderAndCapture(configuration().metricsEnabled(true), request);

        // then
        assertThat(response.getStreamId(), nullValue());
    }
}
