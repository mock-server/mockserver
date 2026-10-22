package org.mockserver.inprocess;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpTemplate;
import org.mockserver.model.RequestDefinition;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThrows;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.Delay.milliseconds;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpTemplate.template;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.verify.VerificationTimes.exactly;
import static org.mockserver.verify.VerificationTimes.once;

public class InProcessMockServerTest {

    private InProcessMockServer server;

    @Before
    public void createServer() {
        server = new InProcessMockServer();
    }

    @After
    public void closeServer() {
        server.close();
    }

    @Test
    public void shouldReturnMockedResponse() {
        server.upsert(new Expectation(request().withMethod("GET").withPath("/orders/1"))
            .thenRespond(response().withStatusCode(201).withHeader("x-order", "one").withBody("{\"id\":1}")));

        HttpResponse response = server.handle(request().withMethod("GET").withPath("/orders/1"));

        assertThat(response.getStatusCode(), is(201));
        assertThat(response.getFirstHeader("x-order"), is("one"));
        assertThat(response.getBodyAsString(), is("{\"id\":1}"));
    }

    @Test
    public void shouldFillInStatusReasonAndContentTypeAsTheWireWould() {
        server.upsert(new Expectation(request().withPath("/defaults")).thenRespond(response().withBody(json("{\"ok\":true}"))));

        HttpResponse response = server.handle(request().withPath("/defaults"));

        assertThat(response.getStatusCode(), is(200));
        assertThat(response.getReasonPhrase(), is("OK"));
        assertThat(response.getFirstHeader("content-type"), startsWith("application/json"));
    }

    @Test
    public void shouldReturnNotFoundForUnmatchedRequestWithoutProxyingToItsHost() {
        EchoServer liveHost = new EchoServer(false);
        try {
            // proxying to this live host would answer 200 with the echoed request
            HttpResponse response = server.handle(request().withPath("/missing").withHeader("Host", "127.0.0.1:" + liveHost.getPort()));

            assertThat(response.getStatusCode(), is(404));
        } finally {
            liveHost.stop();
        }
    }

    @Test
    public void shouldAnswerConcurrentRequestsIndependently() throws Exception {
        server.upsert(new Expectation(request().withPath("/concurrent"))
            .thenRespond(template(HttpTemplate.TemplateType.VELOCITY, "{ \"statusCode\": 200, \"body\": \"$!request.headers['x-id'][0]\" }")));
        int threads = 8;
        int requestsPerThread = 25;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<List<String>>> mismatches = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                final int thread = t;
                mismatches.add(executor.submit(() -> {
                    List<String> wrong = new ArrayList<>();
                    for (int i = 0; i < requestsPerThread; i++) {
                        String id = thread + "-" + i;
                        HttpResponse response = i % 2 == 0
                            ? server.handle(request().withPath("/concurrent").withHeader("x-id", id))
                            : server.handleAsync(request().withPath("/concurrent").withHeader("x-id", id)).get(10, TimeUnit.SECONDS);
                        if (!id.equals(response.getBodyAsString())) {
                            wrong.add(id + " -> " + response.getStatusCode() + " " + response.getBodyAsString());
                        }
                    }
                    return wrong;
                }));
            }
            for (Future<List<String>> mismatch : mismatches) {
                assertThat(mismatch.get(60, TimeUnit.SECONDS), is(empty()));
            }
        } finally {
            executor.shutdownNow();
        }
        server.verify(request().withPath("/concurrent"), exactly(threads * requestsPerThread));
    }

    @Test
    public void shouldForwardThroughConfiguredHttpProxy() {
        EchoServer proxy = new EchoServer(false);
        try (InProcessMockServer proxied = new InProcessMockServer(configuration().forwardHttpProxy(new InetSocketAddress("127.0.0.1", proxy.getPort())))) {
            // .invalid never resolves, so only the proxy can answer
            proxied.upsert(new Expectation(request().withPath("/via-proxy")).thenForward(forward().withHost("upstream.invalid").withPort(80)));

            HttpResponse response = proxied.handle(request().withMethod("POST").withPath("/via-proxy").withBody("through the proxy"));

            assertThat(response.getStatusCode(), is(200));
            assertThat(response.getBodyAsString(), is("through the proxy"));
            assertThat(proxy.getLastRequest().getFirstHeader("Host"), is("upstream.invalid"));
        } finally {
            proxy.stop();
        }
    }

    @Test
    public void shouldLetVerificationSeeHandledRequests() {
        server.upsert(new Expectation(request().withPath("/ping")).thenRespond(response().withBody("pong")));

        server.handle(request().withPath("/ping"));

        server.verify(request().withPath("/ping"), once());
        AssertionError failure = assertThrows(AssertionError.class, () -> server.verify(request().withPath("/ping"), exactly(2)));
        assertThat(failure.getMessage(), containsString("not found exactly 2 times"));
        RequestDefinition[] recorded = server.retrieveRecordedRequests(request().withPath("/ping"));
        assertThat(recorded.length, is(1));
    }

    @Test
    public void shouldNotLeakCorrelationIdIntoCallersRequest() {
        HttpRequest request = request().withPath("/reused");

        server.handle(request);

        assertThat(request.getLogCorrelationId(), is(nullValue()));
    }

    @Test
    public void shouldRenderTemplate() {
        server.upsert(new Expectation(request().withPath("/hello/.*"))
            .thenRespond(template(HttpTemplate.TemplateType.VELOCITY, "{ \"statusCode\": 200, \"body\": \"path=$!request.path\" }")));

        HttpResponse response = server.handle(request().withPath("/hello/world"));

        assertThat(response.getStatusCode(), is(200));
        assertThat(response.getBodyAsString(), is("path=/hello/world"));
    }

    @Test
    public void shouldServeControlPlaneThroughHandle() {
        HttpResponse created = server.handle(request()
            .withMethod("PUT")
            .withPath("/mockserver/expectation")
            .withBody("{ \"httpRequest\": { \"path\": \"/rest\" }, \"httpResponse\": { \"statusCode\": 202, \"body\": \"via rest\" } }"));
        assertThat(created.getStatusCode(), is(201));

        HttpResponse response = server.handle(request().withPath("/rest"));

        assertThat(response.getStatusCode(), is(202));
        assertThat(response.getBodyAsString(), is("via rest"));
        HttpResponse verified = server.handle(request()
            .withMethod("PUT")
            .withPath("/mockserver/verify")
            .withBody("{ \"httpRequest\": { \"path\": \"/rest\" }, \"times\": { \"atLeast\": 1, \"atMost\": 1 } }"));
        assertThat(verified.getStatusCode(), is(202));
    }

    @Test
    public void shouldBlockCallerForDelayInHandleButNotInHandleAsync() throws Exception {
        server.upsert(new Expectation(request().withPath("/slow")).thenRespond(response().withBody("late").withDelay(milliseconds(300))));

        long syncStart = System.nanoTime();
        HttpResponse syncResponse = server.handle(request().withPath("/slow"));
        long syncMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - syncStart);
        assertThat(syncResponse.getBodyAsString(), is("late"));
        assertThat(syncMillis, greaterThanOrEqualTo(300L));

        CompletableFuture<HttpResponse> asyncResponse = server.handleAsync(request().withPath("/slow"));
        assertThat("handleAsync completed before the delay elapsed", asyncResponse.isDone(), is(false));
        assertThat(asyncResponse.get(10, TimeUnit.SECONDS).getBodyAsString(), is("late"));
    }

    @Test
    public void shouldRunClassCallback() {
        server.upsert(new Expectation(request().withPath("/callback")).thenRespond(callback(EchoPathCallback.class)));

        HttpResponse response = server.handle(request().withPath("/callback"));

        assertThat(response.getStatusCode(), is(200));
        assertThat(response.getBodyAsString(), is("callback for /callback"));
    }

    @Test
    public void shouldForwardThroughMockServersClient() {
        EchoServer echoServer = new EchoServer(false);
        try {
            server.upsert(new Expectation(request().withPath("/forwarded")).thenForward(forward().withHost("127.0.0.1").withPort(echoServer.getPort())));

            HttpResponse response = server.handle(request().withMethod("POST").withPath("/forwarded").withBody("echo me"));

            assertThat(response.getStatusCode(), is(200));
            assertThat(response.getBodyAsString(), is("echo me"));
        } finally {
            echoServer.stop();
        }
    }

    @Test
    public void shouldAnswerConnectionFaultWithoutHanging() {
        server.upsert(new Expectation(request().withPath("/drop")).thenError(error().withDropConnection(true)));

        HttpResponse response = server.handle(request().withPath("/drop"));

        assertThat(response.getStatusCode(), is(404));
    }

    @Test
    public void shouldRefuseEveryOperationOnceClosed() {
        server.close();

        assertThat(server.isClosed(), is(true));
        assertThrows(IllegalStateException.class, () -> server.handle(request().withPath("/closed")));
        assertThrows(IllegalStateException.class, () -> server.handleAsync(request().withPath("/closed")));
        assertThrows(IllegalStateException.class, () -> server.upsert(new Expectation(request()).thenRespond(response())));
        assertThrows(IllegalStateException.class, () -> server.verify(request().withPath("/closed"), once()));
        assertThrows(IllegalStateException.class, () -> server.retrieveRecordedRequests(null));
        assertThrows(IllegalStateException.class, () -> server.reset());
    }

    @Test
    public void shouldLeaveNoThreadsBehindWhenClosed() throws InterruptedException {
        EchoServer echoServer = new EchoServer(false);
        try {
            // every thread the in-process server starts descends from the driver, so it lands in this group
            ThreadGroup group = new ThreadGroup("in-process-mockserver-thread-check");
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread driver = new Thread(group, () -> {
                try (InProcessMockServer inProcess = new InProcessMockServer()) {
                    inProcess.upsert(
                        new Expectation(request().withPath("/delayed")).thenRespond(response().withBody("delayed").withDelay(milliseconds(50))),
                        new Expectation(request().withPath("/template")).thenRespond(template(HttpTemplate.TemplateType.VELOCITY, "{ \"statusCode\": 200 }")),
                        new Expectation(request().withPath("/callback")).thenRespond(callback(EchoPathCallback.class)),
                        new Expectation(request().withPath("/forwarded")).thenForward(forward().withHost("127.0.0.1").withPort(echoServer.getPort()))
                    );
                    assertThat(inProcess.handle(request().withPath("/delayed")).getStatusCode(), is(200));
                    assertThat(inProcess.handleAsync(request().withPath("/delayed")).get(10, TimeUnit.SECONDS).getStatusCode(), is(200));
                    assertThat(inProcess.handleAsync(request().withPath("/template")).get(10, TimeUnit.SECONDS).getStatusCode(), is(200));
                    assertThat(inProcess.handleAsync(request().withPath("/callback")).get(10, TimeUnit.SECONDS).getStatusCode(), is(200));
                    assertThat(inProcess.handleAsync(request().withPath("/forwarded")).get(10, TimeUnit.SECONDS).getStatusCode(), is(200));
                    inProcess.verify(request().withPath("/delayed"), exactly(2));
                } catch (Throwable throwable) {
                    failure.set(throwable);
                }
            }, "in-process-mockserver-driver");
            driver.start();
            driver.join(TimeUnit.SECONDS.toMillis(60));
            assertThat("driver did not finish", driver.isAlive(), is(false));
            assertThat(failure.get(), is(nullValue()));

            List<String> remaining = liveThreadNames(group);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!remaining.isEmpty() && System.nanoTime() < deadline) {
                Thread.sleep(50);
                remaining = liveThreadNames(group);
            }
            assertThat("threads still running after close()", remaining, is(empty()));
        } finally {
            echoServer.stop();
        }
    }

    private static List<String> liveThreadNames(ThreadGroup group) {
        Thread[] threads = new Thread[group.activeCount() + 16];
        int count = group.enumerate(threads, true);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            // the JDK's one shared timer behind CompletableFuture.orTimeout, started by the first caller, never ends
            if (threads[i].isAlive() && !threads[i].getName().equals("CompletableFutureDelayScheduler")) {
                names.add(threads[i].getName());
            }
        }
        return names;
    }

    public static class EchoPathCallback implements ExpectationResponseCallback {
        @Override
        public HttpResponse handle(HttpRequest httpRequest) {
            return response().withStatusCode(200).withBody("callback for " + httpRequest.getPath().getValue());
        }
    }
}
