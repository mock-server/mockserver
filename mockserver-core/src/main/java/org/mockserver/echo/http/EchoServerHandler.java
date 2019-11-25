package org.mockserver.echo.http;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import org.mockserver.codec.MockServerResponseEncoder;
import org.mockserver.log.MockServerEventLog;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.BodyWithContentType;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.model.HttpResponse.response;
import static org.slf4j.event.Level.INFO;

/**
 * @author jamesdbloom
 */
@ChannelHandler.Sharable
public class EchoServerHandler extends SimpleChannelInboundHandler<HttpRequest> {

    private final EchoServer.Error error;
    private final MockServerLogger mockServerLogger;
    private final MockServerEventLog mockServerEventLog;
    private final EchoServer.NextResponse nextResponse;
    private final EchoServer.OnlyResponse onlyResponse;

    EchoServerHandler(EchoServer.Error error, MockServerLogger mockServerLogger, MockServerEventLog mockServerEventLog, EchoServer.NextResponse nextResponse, EchoServer.OnlyResponse onlyResponse) {
        this.error = error;
        this.mockServerLogger = mockServerLogger;
        this.mockServerEventLog = mockServerEventLog;
        this.nextResponse = nextResponse;
        this.onlyResponse = onlyResponse;
    }

    protected void channelRead0(ChannelHandlerContext ctx, HttpRequest request) {

        mockServerEventLog.add(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(RECEIVED_REQUEST)
                .setHttpRequest(request)
                .setMessageFormat("EchoServer received request {}")
                .setArguments(request)
        );

        if (onlyResponse.httpResponse != null) {
            // WARNING: this logic is only for unit tests that run in series and is NOT thread safe!!!
            DefaultFullHttpResponse httpResponse = new MockServerResponseEncoder(mockServerLogger).encode(onlyResponse.httpResponse);
            ctx.writeAndFlush(httpResponse);
        } else if (!nextResponse.httpResponse.isEmpty()) {
            // WARNING: this logic is only for unit tests that run in series and is NOT thread safe!!!
            DefaultFullHttpResponse httpResponse = new MockServerResponseEncoder(mockServerLogger).encode(nextResponse.httpResponse.remove());
            ctx.writeAndFlush(httpResponse);
        } else {
            HttpResponse httpResponse =
                response()
                    .withStatusCode(request.getPath().equalsIgnoreCase("/not_found") ? NOT_FOUND.code() : OK.code())
                    .withHeaders(request.getHeaderList());

            if (request.getBody() instanceof BodyWithContentType) {
                httpResponse.withBody((BodyWithContentType) request.getBody());
            } else {
                httpResponse.withBody(request.getBodyAsString());
            }

            // set hop-by-hop headers
            final int length = httpResponse.getBodyAsString() != null ? httpResponse.getBodyAsString().length() : 0;
            if (error == EchoServer.Error.LARGER_CONTENT_LENGTH) {
                httpResponse.replaceHeader(CONTENT_LENGTH.toString(), String.valueOf(length * 2));
            } else if (error == EchoServer.Error.SMALLER_CONTENT_LENGTH) {
                httpResponse.replaceHeader(CONTENT_LENGTH.toString(), String.valueOf(length / 2));
            } else {
                httpResponse.replaceHeader(CONTENT_LENGTH.toString(), String.valueOf(length));
            }

            mockServerEventLog.add(
                new LogEntry()
                    .setLogLevel(INFO)
                    .setType(LogEntry.LogMessageType.INFO)
                    .setHttpRequest(request)
                    .setHttpResponse(httpResponse)
                    .setMessageFormat("EchoServer returning response {} for request {}")
                    .setArguments(httpResponse, request)
            );

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
