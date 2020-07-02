package org.mockserver.dashboard;

import io.netty.channel.ChannelFuture;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Ignore;
import org.junit.Test;
import org.mockserver.log.MockServerEventLog;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpState;
import org.mockserver.model.HttpRequest;
import org.mockserver.scheduler.Scheduler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class DashboardWebSocketServerHandlerTest {

    @Test
    @Ignore
    public void shouldSerialiseEventWithSingleLineStringArgument() throws ExecutionException, InterruptedException {
        // given
        MockServerLogger mockServerLogger = new MockServerLogger(DashboardWebSocketServerHandlerTest.class);
        Scheduler scheduler = new Scheduler(mockServerLogger);
        HttpState httpState = new HttpState(mockServerLogger, scheduler);
        DashboardWebSocketServerHandler handler = new DashboardWebSocketServerHandler(httpState, false);
        MockChannelHandlerContext mockChannelHandlerContext = new MockChannelHandlerContext();
        handler.getClientRegistry().put(mockChannelHandlerContext, HttpRequest.request());

        // TODO add events

        // when
        handler.updated(new MockServerEventLog(mockServerLogger, scheduler, true));

        // then
        mockChannelHandlerContext.objectCompletableFuture.get();
        // TODO add assertions
    }

    public static class MockChannelHandlerContext extends EmbeddedChannel {

        public CompletableFuture<Object> objectCompletableFuture = new CompletableFuture<>();

        @Override
        public ChannelFuture writeAndFlush(Object msg) {
            objectCompletableFuture.complete(msg);
            return null;
        }
    }

}