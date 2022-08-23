package org.mockserver.metrics;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import org.mockserver.configuration.Configuration;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.io.StringWriter;

import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;

public class MetricsHandler {

    private final Boolean metricsEnabled;

    public MetricsHandler(Configuration configuration) {
        metricsEnabled = configuration.metricsEnabled();
    }

    public void renderMetrics(final ChannelHandlerContext ctx, final HttpRequest request) throws Exception {
        HttpResponse response = notFoundResponse();
        if (metricsEnabled) {
            StringWriter stringWriter = new StringWriter();
            String contentType = TextFormat.chooseContentType(request.getFirstHeader("Accept"));
            TextFormat.writeFormat(contentType, stringWriter, CollectorRegistry.defaultRegistry.metricFamilySamples());
            String content = stringWriter.toString();
            response =
                response()
                    .withHeader(HttpHeaderNames.CONTENT_TYPE.toString(), contentType)
                    .withHeader(HttpHeaderNames.CONTENT_LENGTH.toString(), String.valueOf(content.getBytes().length))
                    .withBody(content);
        }
        if (!request.isKeepAlive()) {
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        } else {
            ctx.writeAndFlush(response);
        }
    }

}
