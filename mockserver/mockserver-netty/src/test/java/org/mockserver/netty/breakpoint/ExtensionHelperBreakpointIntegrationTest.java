package org.mockserver.netty.breakpoint;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.extension.TemplateHelperProvider;
import org.mockserver.mock.breakpoint.BreakpointCallbackDispatcher;
import org.mockserver.mock.breakpoint.BreakpointMatcherRegistry;
import org.mockserver.mock.breakpoint.BreakpointPhase;
import org.mockserver.netty.MockServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpTemplate.TemplateType.VELOCITY;
import static org.mockserver.model.HttpTemplate.template;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * A response template continued after a REQUEST-phase breakpoint renders on the template pool when extension
 * template helpers are loaded, so the render waiting on a helper never holds an event loop or a shared scheduler
 * thread, and the helper still works.
 */
public class ExtensionHelperBreakpointIntegrationTest {

    static volatile CountDownLatch helperEntered = new CountDownLatch(1);
    static volatile CountDownLatch helperRelease = new CountDownLatch(1);

    public interface Gate {
        String hold() throws InterruptedException;
    }

    public static class GateHelpers implements TemplateHelperProvider {
        @Override
        public String name() {
            return "gate";
        }

        @Override
        public Map<Class<?>, Object> helpers() {
            return Map.of(Gate.class, (Gate) () -> {
                helperEntered.countDown();
                helperRelease.await(30, SECONDS);
                return "resumed-and-rendered";
            });
        }
    }

    private MockServer mockServer;
    private MockServerClient mockServerClient;

    @Before
    public void setUp() {
        BreakpointMatcherRegistry.getInstance().clear();
        BreakpointCallbackDispatcher.getInstance().reset();
        helperEntered = new CountDownLatch(1);
        helperRelease = new CountDownLatch(1);
        mockServer = new MockServer(configuration()
            .extensions(GateHelpers.class)
            .breakpointTimeoutMillis(30_000L)
            .extensionHelperTimeoutMillis(30_000L));
        mockServerClient = new MockServerClient("localhost", mockServer.getLocalPort());
    }

    @After
    public void tearDown() {
        helperRelease.countDown();
        stopQuietly(mockServerClient);
        stopQuietly(mockServer);
        BreakpointMatcherRegistry.getInstance().clear();
        BreakpointCallbackDispatcher.getInstance().reset();
    }

    @Test(timeout = 60_000)
    public void shouldRenderAContinuedResponseTemplateOnTheTemplatePool() throws Exception {
        mockServerClient
            .when(request().withPath("/breakpoint-helper"))
            .respond(template(VELOCITY, "{ \"statusCode\": 200, \"body\": \"$ext.gate.Gate.hold()\" }"));
        CountDownLatch paused = new CountDownLatch(1);
        mockServerClient.addBreakpoint(request().withPath("/breakpoint-helper"), EnumSet.of(BreakpointPhase.REQUEST), pausedRequest -> {
            paused.countDown();
            return pausedRequest;
        }, null, null);

        CompletableFuture<HttpResponse<String>> response = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build().sendAsync(
            HttpRequest.newBuilder(URI.create("http://localhost:" + mockServer.getLocalPort() + "/breakpoint-helper")).timeout(Duration.ofSeconds(30)).build(),
            HttpResponse.BodyHandlers.ofString());

        assertThat("the request paused at the breakpoint", paused.await(30, SECONDS), is(true));
        assertThat("the continued render called the helper", helperEntered.await(30, SECONDS), is(true));
        assertThat(renderThreadWaitingOnAHelper(), startsWith("MockServer-TemplateAction"));
        helperRelease.countDown();
        assertThat(response.get(30, SECONDS).body(), is("resumed-and-rendered"));
    }

    private static String renderThreadWaitingOnAHelper() {
        for (Map.Entry<Thread, StackTraceElement[]> thread : Thread.getAllStackTraces().entrySet()) {
            for (StackTraceElement frame : thread.getValue()) {
                if (frame.getClassName().equals("org.mockserver.extension.TemplateHelpers") && frame.getMethodName().equals("call")) {
                    return thread.getKey().getName();
                }
            }
        }
        return "no thread is waiting on a helper";
    }
}
