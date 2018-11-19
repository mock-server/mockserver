package org.mockserver.dashboard;

import com.google.common.collect.ImmutableList;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockserver.mock.HttpStateHandler.PATH_PREFIX;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class DashboardHandler {

    private static final Map<String, String> MIME_MAP = new HashMap<>();
    private static final List<String> IS_TEST = ImmutableList.of(
        "css",
        "js",
        "map",
        "json",
        "html"
    );

    public DashboardHandler() {
        MIME_MAP.put("css", "text/css; charset=utf-8");
        MIME_MAP.put("js", "application/javascript; charset=UTF-8");
        MIME_MAP.put("map", "application/json; charset=UTF-8");
        MIME_MAP.put("json", "application/json; charset=UTF-8");
        MIME_MAP.put("html", "text/html; charset=utf-8");
        MIME_MAP.put("ico", "image/x-icon");
        MIME_MAP.put("woff2", "application/font-woff2");
        MIME_MAP.put("ttf", "application/octet-stream");
        MIME_MAP.put("png", "image/png");
    }

    public void renderDashboard(final ChannelHandlerContext ctx, final HttpRequest request) throws Exception {
        HttpResponse response = notFoundResponse();
        if (request.getMethod().getValue().equals("GET")) {
            String path = StringUtils.substringAfter(request.getPath().getValue(), PATH_PREFIX + "/dashboard");
            if (path.isEmpty() || path.equals("/")) {
                path = "/index.html";
            }
            InputStream contentStream = DashboardHandler.class.getResourceAsStream("/org/mockserver/dashboard" + path);
            if (contentStream != null) {
                final String extension = StringUtils.substringAfterLast(path, ".");
                if (IS_TEST.contains(extension)) {
                    final String content = IOUtils.toString(contentStream, UTF_8.name());
                    response =
                        response()
                            .withHeader(HttpHeaderNames.CONTENT_TYPE.toString(), MIME_MAP.get(extension))
                            .withHeader(HttpHeaderNames.CONTENT_LENGTH.toString(), String.valueOf(content.length()))
                            .withBody(content);
                } else {
                    final byte[] bytes = IOUtils.toByteArray(contentStream);
                    response =
                        response()
                            .withHeader(HttpHeaderNames.CONTENT_TYPE.toString(), MIME_MAP.get(extension))
                            .withHeader(HttpHeaderNames.CONTENT_LENGTH.toString(), String.valueOf(bytes.length))
                            .withBody(bytes);
                }
                if (request.isKeepAlive()) {
                    response.withHeader(HttpHeaderNames.CONNECTION.toString(), HttpHeaderValues.KEEP_ALIVE.toString());
                }
            }
        }
        if (!request.isKeepAlive()) {
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        } else {
            ctx.writeAndFlush(response);
        }
    }
}
