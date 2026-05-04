package org.mockserver.metrics;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.prometheus.metrics.expositionformats.ExpositionFormatWriter;
import io.prometheus.metrics.expositionformats.ExpositionFormats;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import org.mockserver.configuration.Configuration;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.io.ByteArrayOutputStream;

import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;

public class MetricsHandler {

    private final Boolean metricsEnabled;
    private final ExpositionFormats expositionFormats;

    public MetricsHandler(Configuration configuration) {
        metricsEnabled = configuration.metricsEnabled();
        expositionFormats = ExpositionFormats.init();
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
                    .withHeader(HttpHeaderNames.CONTENT_TYPE.toString(), writer.getContentType())
                    .withHeader(HttpHeaderNames.CONTENT_LENGTH.toString(), String.valueOf(content.length))
                    .withBody(content);
        }
        if (!request.isKeepAlive()) {
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        } else {
            ctx.writeAndFlush(response);
        }
    }

}
