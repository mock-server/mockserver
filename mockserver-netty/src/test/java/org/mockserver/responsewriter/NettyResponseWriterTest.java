package org.mockserver.responsewriter;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.configuration.ConfigurationProperties.enableCORSForAllResponses;
import static org.mockserver.model.ConnectionOptions.connectionOptions;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;

public class NettyResponseWriterTest {

    @Mock
    private ChannelHandlerContext mockChannelHandlerContext;
    @Mock
    private ChannelFuture mockChannelFuture;
    @Mock
    private MockServerLogger mockServerLogger;

    @Before
    public void setupTestFixture() {
        initMocks(this);

        when(mockChannelHandlerContext.writeAndFlush(any())).thenReturn(mockChannelFuture);
    }

    @Test
    public void shouldWriteBasicResponse() {
        // given
        HttpRequest request = request("some_request");
        HttpResponse response = response("some_response");

        // when
        new NettyResponseWriter(mockChannelHandlerContext, mockServerLogger).writeResponse(request.clone(), response.clone(), false);

        // then
        verify(mockChannelHandlerContext).writeAndFlush(
                response("some_response")
                        .withHeader("connection", "close")
        );
        verify(mockChannelFuture).addListener(ChannelFutureListener.CLOSE);
    }

    @Test
    public void shouldWriteNullResponse() {
        // given
        HttpRequest request = request("some_request");

        // when
        new NettyResponseWriter(mockChannelHandlerContext, mockServerLogger).writeResponse(request.clone(), (HttpResponse) null, false);

        // then
        verify(mockChannelHandlerContext).writeAndFlush(
                notFoundResponse()
                        .withHeader("connection", "close")
        );
        verify(mockChannelFuture).addListener(ChannelFutureListener.CLOSE);
    }

    @Test
    public void shouldWriteAddCORSHeaders() {
        boolean enableCORSForAllResponses = enableCORSForAllResponses();
        try {
            // given
            enableCORSForAllResponses(true);
            HttpRequest request = request("some_request");
            HttpResponse response = response("some_response");

            // when
            new NettyResponseWriter(mockChannelHandlerContext, mockServerLogger).writeResponse(request.clone(), response.clone(), false);

            // then
            verify(mockChannelHandlerContext).writeAndFlush(
                    response
                            .withHeader("connection", "close")
                            .withHeader("access-control-allow-origin", "*")
                            .withHeader("access-control-allow-methods", "CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, PATCH, TRACE")
                            .withHeader("access-control-allow-headers", "Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization")
                            .withHeader("access-control-expose-headers", "Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization")
                            .withHeader("access-control-max-age", "300")
                            .withHeader("x-cors", "MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORSForAPI(false) or -Dmockserver.enableCORSForAPI=false")
            );
            verify(mockChannelFuture).addListener(ChannelFutureListener.CLOSE);
        } finally {
            enableCORSForAllResponses(enableCORSForAllResponses);
        }
    }

    @Test
    public void shouldKeepAlive() {
        // given
        HttpRequest request = request("some_request")
                .withKeepAlive(true);
        HttpResponse response = response("some_response");

        // when
        new NettyResponseWriter(mockChannelHandlerContext, mockServerLogger).writeResponse(request.clone(), response.clone(), false);

        // then
        verify(mockChannelHandlerContext).writeAndFlush(
                response("some_response")
                        .withHeader("connection", "keep-alive")
        );
        verify(mockChannelFuture, times(0)).addListener(ChannelFutureListener.CLOSE);
    }

    @Test
    public void shouldOverrideKeepAlive() {
        // given
        HttpRequest request = request("some_request");
        HttpResponse response = response("some_response")
                .withConnectionOptions(
                        connectionOptions()
                                .withKeepAliveOverride(true)
                );

        // when
        new NettyResponseWriter(mockChannelHandlerContext, mockServerLogger).writeResponse(request.clone(), response.clone(), false);

        // then
        verify(mockChannelHandlerContext).writeAndFlush(
                response("some_response")
                        .withHeader("connection", "keep-alive")
                        .withConnectionOptions(
                                connectionOptions()
                                        .withKeepAliveOverride(true)
                        )
        );
        verify(mockChannelFuture).addListener(ChannelFutureListener.CLOSE);
    }

    @Test
    public void shouldSuppressConnectionHeader() {
        // given
        HttpRequest request = request("some_request");
        HttpResponse response = response("some_response")
                .withConnectionOptions(
                        connectionOptions()
                                .withSuppressConnectionHeader(true)
                );

        // when
        new NettyResponseWriter(mockChannelHandlerContext, mockServerLogger).writeResponse(request.clone(), response.clone(), false);

        // then
        verify(mockChannelHandlerContext).writeAndFlush(
                response("some_response")
                        .withConnectionOptions(
                                connectionOptions()
                                        .withSuppressConnectionHeader(true)
                        )
        );
        verify(mockChannelFuture).addListener(ChannelFutureListener.CLOSE);
    }

    @Test
    public void shouldCloseSocket() {
        // given
        HttpRequest request = request("some_request");
        HttpResponse response = response("some_response")
                .withConnectionOptions(
                        connectionOptions()
                                .withCloseSocket(false)
                );

        // when
        new NettyResponseWriter(mockChannelHandlerContext, mockServerLogger).writeResponse(request.clone(), response.clone(), false);

        // then
        verify(mockChannelHandlerContext).writeAndFlush(
                response("some_response")
                        .withHeader("connection", "close")
                        .withConnectionOptions(
                                connectionOptions()
                                        .withCloseSocket(false)
                        )
        );
        verify(mockChannelFuture, times(0)).addListener(ChannelFutureListener.CLOSE);
    }

}
