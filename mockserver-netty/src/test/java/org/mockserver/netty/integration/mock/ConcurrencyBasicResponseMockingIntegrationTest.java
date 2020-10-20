package org.mockserver.netty.integration.mock;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.*;
import org.mockserver.client.NettyHttpClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.scheduler.Scheduler;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.stop.Stop.stopQuietly;

public class ConcurrencyBasicResponseMockingIntegrationTest {

    private ClientAndServer clientAndServer;
    private NettyHttpClient httpClient;

    private static final EventLoopGroup clientEventLoopGroup = new NioEventLoopGroup(3, new Scheduler.SchedulerThreadFactory(ConcurrencyResponseWebSocketMockingIntegrationTest.class.getSimpleName() + "-eventLoop"));

    @Before
    public void setUp() {
        clientAndServer = ClientAndServer.startClientAndServer();
        clientAndServer
            .when(
                request()
                    .withPath("/my/echo")
            )
            .respond(callback().withCallbackClass(ConcurrencyBasicResponseMockingIntegrationTest.ClassCallback.class));
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
    public void sendMultipleRequestsSingleThreaded() throws ExecutionException, InterruptedException, TimeoutException {
        scheduleTasksAndWaitForResponses(1);
    }

    @Test
    public void sendMultipleRequestsMultiThreaded() throws ExecutionException, InterruptedException, TimeoutException {
        scheduleTasksAndWaitForResponses(100);
    }

    private void scheduleTasksAndWaitForResponses(int parallelThreads) throws InterruptedException, ExecutionException, TimeoutException {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(parallelThreads);

        List<ScheduledFuture<?>> scheduledFutures = new ArrayList<>();
        for (int i = 0; i < parallelThreads; i++) {
            scheduledFutures.add(executor.schedule(new Task(), 1L, TimeUnit.SECONDS));
        }

        for (int i = 0; i < parallelThreads; i++) {
            scheduledFutures.get(i).get(15L, TimeUnit.SECONDS);
        }
    }

    private void sendRequestAndVerifyResponse() {
        try {
            String requestBody = "thread: " + Thread.currentThread().getName() + ", random content: " + Math.random();
            HttpResponse httpResponse = httpClient.sendRequest(
                request()
                    .withMethod("POST")
                    .withPath("/my/echo")
                    .withBody(requestBody),
                new InetSocketAddress("localhost", clientAndServer.getPort())
            ).get(20, TimeUnit.MINUTES);
            Assert.assertEquals(requestBody, httpResponse.getBodyAsString());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public class Task implements Runnable {
        @Override
        public void run() {
            ConcurrencyBasicResponseMockingIntegrationTest.this.sendRequestAndVerifyResponse();
        }
    }

    @SuppressWarnings("unused")
    public static class ClassCallback implements ExpectationResponseCallback {
        @Override
        public HttpResponse handle(HttpRequest request) {
            return response()
                .withHeader(CONTENT_LENGTH.toString(), String.valueOf(request.getBodyAsString().length()))
                .withBody(request.getBodyAsString());
        }
    }

}
