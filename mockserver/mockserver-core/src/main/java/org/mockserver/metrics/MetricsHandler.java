package org.mockserver.metrics;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.prometheus.metrics.expositionformats.ExpositionFormatWriter;
import io.prometheus.metrics.expositionformats.ExpositionFormats;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import org.mockserver.configuration.Configuration;
import org.mockserver.cors.CORSHeaders;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.io.ByteArrayOutputStream;

import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;

public class MetricsHandler {

    private final Boolean metricsEnabled;
    private final ExpositionFormats expositionFormats;
    private final CORSHeaders corsHeaders;

    public MetricsHandler(Configuration configuration) {
        metricsEnabled = configuration.metricsEnabled();
        expositionFormats = ExpositionFormats.init();
        corsHeaders = new CORSHeaders(configuration);
    }

    public void renderMetrics(final ChannelHandlerContext ctx, final HttpRequest request) throws Exception {
        HttpResponse response = notFoundResponse();
        if (metricsEnabled) {
            ExpositionFormatWriter writer = expositionFormats.findWriter(request.getFirstHeader("Accept"));
            MetricSnapshots snapshots = PrometheusRegistry.defaultRegistry.scrape();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            writer.write(outputStream, snapshots);
            byte[] content = outputStream.toByteArray();
            response =
                response()
                    .withStatusCode(200)
                    .withHeader(HttpHeaderNames.CONTENT_TYPE.toString(), writer.getContentType())
                    .withHeader(HttpHeaderNames.CONTENT_LENGTH.toString(), String.valueOf(content.length))
                    .withBody(content);
        }
        // This endpoint writes directly to the channel (it does not go through
        // ResponseWriter), so add CORS headers here — otherwise the dashboard
        // served from another origin (e.g. a dev server) is blocked by CORS,
        // including the disabled-state 404 (which the UI needs to read to show
        // its "metrics disabled" guidance rather than a fetch error).
        corsHeaders.addControlPlaneCORSHeaders(request, response);
        // Because this endpoint bypasses ResponseWriter (see above) nothing has copied the request's
        // HTTP/2 stream id onto the response, and the response mapper reads that field rather than a
        // header - so on HTTP/2 the scrape would go out on a fresh server-initiated stream and
        // Prometheus would hang instead of receiving metrics. Null on HTTP/1.1, where it is a no-op.
        response.withStreamId(request.getStreamId());
        if (!Boolean.TRUE.equals(request.isKeepAlive())) {
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        } else {
            ctx.writeAndFlush(response);
        }
    }

}
