package org.mockserver.integration.mocking;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.*;
import org.mockserver.client.NettyHttpClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpResponse;
import org.mockserver.scheduler.Scheduler;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockserver.matchers.Times.once;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.stop.Stop.stopQuietly;

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
                once()
            )
            .respond(request ->
                response()
                    .withHeader(CONTENT_LENGTH.toString(), String.valueOf(request.getBodyAsString().length()))
                    .withBody(request.getBodyAsString())
            );
        httpClient = new NettyHttpClient(new MockServerLogger(), clientEventLoopGroup, null);
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
    public void sendMultipleRequestsSingleThreaded() throws ExecutionException, InterruptedException, TimeoutException {
        scheduleTasksAndWaitForResponses(1);
    }

    @Test
    public void sendMultipleRequestsMultiThreaded() throws ExecutionException, InterruptedException, TimeoutException {
        scheduleTasksAndWaitForResponses(25);
    }

    @SuppressWarnings("rawtypes")
    private void scheduleTasksAndWaitForResponses(int parallelThreads) throws InterruptedException, ExecutionException, TimeoutException {
        ExecutorService executor = Executors.newFixedThreadPool(parallelThreads * 2, new Scheduler.SchedulerThreadFactory(this.getClass().getSimpleName()));

        List<CompletableFuture> completableFutures = new ArrayList<>();
        for (int i = 0; i < parallelThreads; i++) {
            int counter = i;
            final CompletableFuture<String> completableFuture = new CompletableFuture<>();
            executor.execute(() -> ConcurrencyResponseWebSocketMockingIntegrationTest.this.sendRequestAndVerifyResponse(counter, completableFuture));
            completableFutures.add(completableFuture);
        }

        for (int i = 0; i < parallelThreads; i++) {
            System.out.println("counter waiting = " + i);
            completableFutures.get(i).get(120L, SECONDS);
            System.out.println("counter finished = " + i);
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
                new InetSocketAddress("localhost", clientAndServer.getLocalPort())
            ).get(20, TimeUnit.MINUTES);
            Assert.assertEquals(requestBody, httpResponse.getBodyAsString());
            completableFuture.complete(httpResponse.getBodyAsString());
        } catch (Exception ex) {
            completableFuture.completeExceptionally(ex);
        }
    }

}
