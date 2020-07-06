package org.mockserver.netty.integration.mock;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.client.NettyHttpClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpResponse;
import org.mockserver.scheduler.Scheduler;
import org.slf4j.event.Level;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.matchers.Times.unlimited;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.stop.Stop.stopQuietly;
import static org.mockserver.testing.closurecallback.ViaWebSocket.viaWebSocket;

public class ConcurrencyResponseWebSocketMockingIntegrationTest {

    private ClientAndServer clientAndServer;
    private NettyHttpClient httpClient;

    private static final EventLoopGroup clientEventLoopGroup = new NioEventLoopGroup(3, new Scheduler.SchedulerThreadFactory(ConcurrencyResponseWebSocketMockingIntegrationTest.class.getSimpleName() + "-eventLoop"));

    @Before
    public void setUp() {
        clientAndServer = ClientAndServer.startClientAndServer();
        clientAndServer
            .when(
                request()
                    .withPath("/my/echo.*"),
                unlimited()
            )
            .respond(request ->
                response()
                    .withHeader(CONTENT_LENGTH.toString(), String.valueOf(request.getBodyAsString().length()))
                    .withBody(request.getBodyAsString())
            );
        httpClient = new NettyHttpClient(new MockServerLogger(), clientEventLoopGroup, null, false);
    }

    @AfterClass
    public static void stopEventLoopGroup() {
        clientEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
    }

    @After
    public void tearDown() {
        stopQuietly(clientAndServer);
    }

    @Test
    public void sendMultipleRequestsSingleThreadedViaWebSocket() throws Exception {
        viaWebSocket(() -> scheduleTasksAndWaitForResponses(1));
    }

    @Test
    public void sendMultipleRequestsMultiThreadedViaWebSocket() throws Exception {
        viaWebSocket(() -> scheduleTasksAndWaitForResponses(25));
    }

    @Test
    public void sendMultipleRequestsSingleThreadedViaLocalJVM() {
        scheduleTasksAndWaitForResponses(1);
    }

    @Test
    public void sendMultipleRequestsMultiThreadedViaLocalJVM() {
        scheduleTasksAndWaitForResponses(25);
    }

    @SuppressWarnings("rawtypes")
    private void scheduleTasksAndWaitForResponses(int parallelThreads) {
        ExecutorService executor = Executors.newFixedThreadPool(parallelThreads * 3, new Scheduler.SchedulerThreadFactory(this.getClass().getSimpleName()));

        List<CompletableFuture> completableFutures = new ArrayList<>();
        for (int i = 0; i < parallelThreads; i++) {
            int counter = i;
            final CompletableFuture<String> completableFuture = new CompletableFuture<>();
            executor.execute(() -> ConcurrencyResponseWebSocketMockingIntegrationTest.this.sendRequestAndVerifyResponse(counter, completableFuture));
            completableFutures.add(completableFuture);
        }

        for (int i = 0; i < parallelThreads; i++) {
            try {
                completableFutures.get(i).get(120L, SECONDS);
            } catch (Throwable throwable) {
                new MockServerLogger().logEvent(
                    new LogEntry()
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("exception waiting for counter " + i)
                        .setArguments(throwable)
                );
            }
        }
    }

    private void sendRequestAndVerifyResponse(int counter, CompletableFuture<String> completableFuture) {
        try {
            String requestBody = "thread: " + Thread.currentThread().getName() + ", counter: " + counter;
            HttpResponse httpResponse = httpClient.sendRequest(
                request()
                    .withMethod("POST")
                    .withPath("/my/echo" + counter)
                    .withBody(requestBody),
                new InetSocketAddress("localhost", clientAndServer.getPort())
            ).get(20, TimeUnit.MINUTES);
            assertThat(httpResponse.getBodyAsString(), is(requestBody));
            completableFuture.complete(httpResponse.getBodyAsString());
        } catch (Exception ex) {
            completableFuture.completeExceptionally(ex);
        }
    }

}
