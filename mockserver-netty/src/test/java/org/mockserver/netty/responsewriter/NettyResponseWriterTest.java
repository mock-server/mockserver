package org.mockserver.netty.responsewriter;

import io.netty.channel.*;
import io.netty.util.concurrent.GenericFutureListener;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.scheduler.Scheduler;

import java.net.ServerSocket;
import java.net.Socket;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItemInArray;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.configuration.ConfigurationProperties.enableCORSForAllResponses;
import static org.mockserver.model.ConnectionOptions.connectionOptions;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;

@SuppressWarnings("unchecked")
public class NettyResponseWriterTest {

    @Mock
    private ChannelHandlerContext mockChannelHandlerContext;
    @Mock
    private ChannelFuture mockChannelFuture;
    @Mock
    private Channel mockChannel;
    @Mock
    private Scheduler scheduler;

    private ArgumentCaptor<GenericFutureListener<ChannelFuture>> genericFutureListenerArgumentCaptor;

    @Before
    public void setupTestFixture() {
        openMocks(this);

        genericFutureListenerArgumentCaptor = ArgumentCaptor.forClass(GenericFutureListener.class);
        when(mockChannelFuture.addListener(genericFutureListenerArgumentCaptor.capture())).thenReturn(null);
        when(mockChannelFuture.channel()).thenReturn(mockChannel);
        when(mockChannelHandlerContext.writeAndFlush(any())).thenReturn(mockChannelFuture);
        when(mockChannel.close()).thenReturn(mockChannelFuture);
        when(mockChannel.disconnect()).thenReturn(mockChannelFuture);
        when(mockChannelFuture.isSuccess()).thenReturn(true);
    }

    @Test
    public void shouldWriteBasicResponse() {
        // given
        HttpRequest request = request("some_request");
        HttpResponse response = response("some_response");

        // when
        new NettyResponseWriter(configuration(), new MockServerLogger(), mockChannelHandlerContext, scheduler).writeResponse(request.clone(), response.clone(), false);

        // then
        verify(mockChannelHandlerContext).writeAndFlush(
            response("some_response")
                .withHeader("connection", "close")
        );
        verify(mockChannelFuture).addListener(any(GenericFutureListener.class));
    }

    @Test
    public void shouldWriteNullResponse() {
        // given
        HttpRequest request = request("some_request");

        // when
        new NettyResponseWriter(configuration(), new MockServerLogger(), mockChannelHandlerContext, scheduler).writeResponse(request.clone(), null, false);

        // then
        verify(mockChannelHandlerContext).writeAndFlush(
            notFoundResponse()
                .withHeader("connection", "close")
        );
        verify(mockChannelFuture).addListener(any(GenericFutureListener.class));
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
            new NettyResponseWriter(configuration(), new MockServerLogger(), mockChannelHandlerContext, scheduler).writeResponse(request.clone(), response.clone(), false);

            // then
            verify(mockChannelHandlerContext).writeAndFlush(
                response
                    .withHeader("connection", "close")
                    .withHeader("access-control-allow-origin", "*")
                    .withHeader("access-control-allow-methods", "CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, PATCH, TRACE")
                    .withHeader("access-control-allow-headers", "Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization")
                    .withHeader("access-control-expose-headers", "Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization")
                    .withHeader("access-control-max-age", "300")
            );
            verify(mockChannelFuture).addListener(any(GenericFutureListener.class));
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
        new NettyResponseWriter(configuration(), new MockServerLogger(), mockChannelHandlerContext, scheduler).writeResponse(request.clone(), response.clone(), false);

        // then
        verify(mockChannelHandlerContext).writeAndFlush(
            response("some_response")
                .withHeader("connection", "keep-alive")
        );
        verify(mockChannelFuture, times(0)).addListener(any(GenericFutureListener.class));
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
        new NettyResponseWriter(configuration(), new MockServerLogger(), mockChannelHandlerContext, scheduler).writeResponse(request.clone(), response.clone(), false);

        // then
        verify(mockChannelHandlerContext).writeAndFlush(
            response("some_response")
                .withHeader("connection", "keep-alive")
                .withConnectionOptions(
                    connectionOptions()
                        .withKeepAliveOverride(true)
                )
        );
        verify(mockChannelFuture).addListener(any(GenericFutureListener.class));
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
        new NettyResponseWriter(configuration(), new MockServerLogger(), mockChannelHandlerContext, scheduler).writeResponse(request.clone(), response.clone(), false);

        // then
        verify(mockChannelHandlerContext).writeAndFlush(
            response("some_response")
                .withConnectionOptions(
                    connectionOptions()
                        .withSuppressConnectionHeader(true)
                )
        );
        verify(mockChannelFuture).addListener(any(GenericFutureListener.class));
    }

    @Test
    public void shouldCloseSocket() throws Exception {
        // given
        HttpRequest request = request("some_request");
        HttpResponse response = response("some_response")
            .withConnectionOptions(
                connectionOptions()
                    .withCloseSocket(true)
            );

        // when
        new NettyResponseWriter(configuration(), new MockServerLogger(), mockChannelHandlerContext, scheduler).writeResponse(request.clone(), response.clone(), false);
        genericFutureListenerArgumentCaptor.getValue().operationComplete(mockChannelFuture);
        genericFutureListenerArgumentCaptor.getValue().operationComplete(mockChannelFuture);

        // then
        verify(mockChannel).disconnect();
        verify(mockChannel).close();
        verify(mockChannelHandlerContext).writeAndFlush(
            response("some_response")
                .withHeader("connection", "close")
                .withConnectionOptions(
                    connectionOptions()
                        .withCloseSocket(true)
                )
        );
    }

    @Test
    public void shouldDelaySocketClose() throws Exception {
        // given
        HttpRequest request = request("some_request");
        HttpResponse response = response("some_response")
            .withConnectionOptions(
                connectionOptions()
                    .withCloseSocket(true)
                    .withCloseSocketDelay(new Delay(SECONDS, 3))
            );

        // when
        new NettyResponseWriter(configuration(), new MockServerLogger(), mockChannelHandlerContext, scheduler).writeResponse(request.clone(), response.clone(), false);
        genericFutureListenerArgumentCaptor.getValue().operationComplete(mockChannelFuture);

        // then
        verify(scheduler).schedule(isA(Runnable.class), eq(false), eq(new Delay(SECONDS, 3)));
        verify(mockChannelHandlerContext).writeAndFlush(
            response("some_response")
                .withHeader("connection", "close")
                .withConnectionOptions(
                    connectionOptions()
                        .withCloseSocket(true)
                        .withCloseSocketDelay(new Delay(SECONDS, 3))
                )
        );
    }

}
