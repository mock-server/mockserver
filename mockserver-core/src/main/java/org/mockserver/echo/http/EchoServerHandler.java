package org.mockserver.echo.http;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import org.mockserver.filters.RequestLogFilter;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.server.netty.codec.MockServerResponseEncoder;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
@ChannelHandler.Sharable
public class EchoServerHandler extends SimpleChannelInboundHandler<HttpRequest> {

    private final EchoServer.Error error;
    private final boolean isSecure;
    private final RequestLogFilter requestLogFilter;
    private final EchoServer.NextResponse nextResponse;

    public EchoServerHandler(EchoServer.Error error, boolean isSecure, RequestLogFilter requestLogFilter, EchoServer.NextResponse nextResponse) {
        this.error = error;
        this.isSecure = isSecure;
        this.requestLogFilter = requestLogFilter;
        this.nextResponse = nextResponse;
    }

    protected void channelRead0(ChannelHandlerContext ctx, HttpRequest request) {
        requestLogFilter.onRequest(request);

        if (!nextResponse.httpResponse.isEmpty()) {
            // WARNING: this logic is only for unit tests that run in series and is NOT thread safe!!!
            DefaultFullHttpResponse httpResponse = new MockServerResponseEncoder().encode(nextResponse.httpResponse.remove());
            ctx.writeAndFlush(httpResponse);
        } else {
            HttpResponse httpResponse =
                    response()
                            .withStatusCode(request.getPath().equalsIgnoreCase("/not_found") ? NOT_FOUND.code() : OK.code())
                            .withBody(request.getBody())
                            .withHeaders(request.getHeaders());

            // set hop-by-hop headers
            final int length = httpResponse.getBodyAsString() != null ? httpResponse.getBodyAsString().length() : 0;
            if (error == EchoServer.Error.LARGER_CONTENT_LENGTH) {
                httpResponse.updateHeader(CONTENT_LENGTH.toString(), String.valueOf(length * 2));
            } else if (error == EchoServer.Error.SMALLER_CONTENT_LENGTH) {
                httpResponse.updateHeader(CONTENT_LENGTH.toString(), String.valueOf(length / 2));
            } else {
                httpResponse.updateHeader(CONTENT_LENGTH.toString(), String.valueOf(length));
            }

            // write and flush
            ctx.writeAndFlush(httpResponse);

            if (error == EchoServer.Error.LARGER_CONTENT_LENGTH || error == EchoServer.Error.SMALLER_CONTENT_LENGTH) {
                ctx.close();
            }
        }
    }

    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}