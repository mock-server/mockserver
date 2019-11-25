package org.mockserver.integration.mocking;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.*;
import org.mockserver.client.NettyHttpClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.stop.Stop.stopQuietly;

public class ConcurrencyResponseWebSocketMockingIntegrationTest {

    private ClientAndServer clientAndServer;
    private NettyHttpClient httpClient;

    private static EventLoopGroup clientEventLoopGroup = new NioEventLoopGroup();

    @Before
    public void setUp() {
        clientAndServer = ClientAndServer.startClientAndServer();
        clientAndServer
            .when(
                request()
                    .withPath("/my/echo")
            )
            .respond(new ExpectationResponseCallback() {
                @Override
                public HttpResponse handle(HttpRequest request) {
                    return response()
                        .withHeader(CONTENT_LENGTH.toString(), String.valueOf(request.getBodyAsString().length()))
                        .withBody(request.getBodyAsString());
                }
            });
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
        scheduleTasksAndWaitForResponses(100);
    }

    private void scheduleTasksAndWaitForResponses(int parallelThreads) throws InterruptedException, ExecutionException, TimeoutException {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(parallelThreads);

        List<ScheduledFuture> scheduledFutures = new ArrayList<>();
        for (int i = 0; i < parallelThreads; i++) {
            scheduledFutures.add(executor.schedule(new Task(), 500L, MILLISECONDS));
        }

        for (int i = 0; i < parallelThreads; i++) {
            scheduledFutures.get(i).get(25L, SECONDS);
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
                new InetSocketAddress("localhost", clientAndServer.getLocalPort())
            ).get(20, TimeUnit.MINUTES);
            Assert.assertEquals(requestBody, httpResponse.getBodyAsString());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public class Task implements Runnable {
        @Override
        public void run() {
            ConcurrencyResponseWebSocketMockingIntegrationTest.this.sendRequestAndVerifyResponse();
        }
    }

}
