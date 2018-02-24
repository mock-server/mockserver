package org.mockserver.dashboard;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.io.File;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockserver.mock.HttpStateHandler.PATH_PREFIX;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class DashboardHandler {

    private static final Map<String, String> MIME_MAP = new HashMap<>();

    public DashboardHandler() {
        MIME_MAP.put("css", "text/css; charset=utf-8");
        MIME_MAP.put("js", "application/javascript; charset=UTF-8");
        MIME_MAP.put("map", "application/json; charset=UTF-8");
        MIME_MAP.put("json", "application/json; charset=UTF-8");
        MIME_MAP.put("html", "text/html; charset=utf-8");
        MIME_MAP.put("ico", "image/x-icon");
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
                String content = IOUtils.toString(contentStream, UTF_8.name());
                response =
                    response()
                        .withHeader(HttpHeaderNames.CONTENT_TYPE.toString(), MIME_MAP.get(StringUtils.substringAfterLast(path, ".")))
                        .withHeader(HttpHeaderNames.CONTENT_LENGTH.toString(), String.valueOf(content.length()))
                        .withBody(content);

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
