package org.mockserver.lifecycle;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.extension.Extension;
import org.mockserver.extension.ExtensionContext;
import org.mockserver.extension.ExtensionException;
import org.mockserver.extension.LoadedExtension;
import org.mockserver.mock.Expectation;
import org.mockserver.netty.MockServer;
import org.mockserver.server.initialize.ExpectationInitializer;
import org.mockserver.socket.PortFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThrows;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Extensions on a Netty server: started first, stopped in reverse with the server, refused start-up cleans up, and
 * readiness waits for {@code onStarted}.
 */
public class ExtensionLifecycleTest {

    static final List<String> EVENTS = new CopyOnWriteArrayList<>();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public abstract static class Recording implements Extension {
        @Override
        public void start(ExtensionContext context) throws Exception {
            EVENTS.add("start:" + name());
        }

        @Override
        public void onStarted(List<Integer> ports) throws Exception {
            EVENTS.add("onStarted:" + name() + ":" + ports);
        }

        @Override
        public void stop() {
            EVENTS.add("stop:" + name());
        }
    }

    public static class First extends Recording {
        @Override
        public String name() {
            return "lifecycle-first";
        }
    }

    public static class Second extends Recording implements ExpectationInitializer {
        @Override
        public String name() {
            return "lifecycle-second";
        }

        @Override
        public Expectation[] initializeExpectations() {
            return new Expectation[]{new Expectation(request("/seeded-by-extension")).thenRespond(response("seeded"))};
        }
    }

    public static class FailsToStart extends Recording {
        @Override
        public String name() {
            return "lifecycle-fails";
        }

        @Override
        public int priority() {
            return 1;
        }

        @Override
        public void start(ExtensionContext context) {
            EVENTS.add("start:" + name());
            throw new IllegalStateException("refusing to start");
        }
    }

    public static class NeverReachedAfterFailure extends Recording {
        @Override
        public String name() {
            return "lifecycle-after-failure";
        }

        @Override
        public int priority() {
            return 2;
        }
    }

    public static class BlocksOnStarted extends Recording {
        static volatile CountDownLatch entered = new CountDownLatch(1);
        static volatile CountDownLatch release = new CountDownLatch(1);
        static volatile List<Integer> ports;

        @Override
        public String name() {
            return "lifecycle-blocks-on-started";
        }

        @Override
        public void onStarted(List<Integer> ports) throws Exception {
            BlocksOnStarted.ports = ports;
            entered.countDown();
            release.await(30, SECONDS);
            EVENTS.add("onStarted:" + name());
        }
    }

    @Before
    @After
    public void reset() {
        EVENTS.clear();
        BlocksOnStarted.entered = new CountDownLatch(1);
        BlocksOnStarted.release.countDown();
        BlocksOnStarted.release = new CountDownLatch(1);
    }

    @Test
    public void shouldStartConfiguredExtensionsBeforeTheServerAndStopThemInReverseWithIt() throws Exception {
        Configuration configuration = configuration().extensions(First.class.getName() + "," + Second.class.getName());

        MockServer mockServer = new MockServer(configuration);
        int port = mockServer.getLocalPort();
        try {
            assertThat(EVENTS, contains("start:lifecycle-first", "start:lifecycle-second",
                "onStarted:lifecycle-first:[" + port + "]", "onStarted:lifecycle-second:[" + port + "]"));
            assertThat(get(port, "/mockserver/ready").statusCode(), is(200));
            HttpResponse<String> seeded = get(port, "/seeded-by-extension");
            assertThat(seeded.statusCode(), is(200));
            assertThat(seeded.body(), is("seeded"));
        } finally {
            mockServer.stop();
        }

        assertThat(EVENTS.subList(4, EVENTS.size()), contains("stop:lifecycle-second", "stop:lifecycle-first"));
    }

    @Test
    public void shouldRefuseStartupAndStopTheStartedExtensionsInReverseWhenOneFailsToStart() {
        Configuration configuration = configuration().extensions(NeverReachedAfterFailure.class, FailsToStart.class, Second.class, First.class);

        ExtensionException failure = assertThrows(ExtensionException.class, () -> new MockServer(configuration));

        assertThat(failure.getMessage(), containsString("lifecycle-fails failed to start"));
        assertThat(EVENTS, contains(
            "start:lifecycle-first", "start:lifecycle-second", "start:lifecycle-fails",
            "stop:lifecycle-second", "stop:lifecycle-first"
        ));
        assertNoExtensionThreadsRemain();
    }

    @Test
    public void shouldStayNotReadyUntilEveryOnStartedReturns() throws Exception {
        Configuration configuration = configuration().extensions(BlocksOnStarted.class);

        CompletableFuture<MockServer> starting = CompletableFuture.supplyAsync(() -> new MockServer(configuration));
        assertThat(BlocksOnStarted.entered.await(30, SECONDS), is(true));
        int port = BlocksOnStarted.ports.get(0);
        try {
            assertThat("ports are bound but onStarted has not returned", get(port, "/mockserver/ready").statusCode(), is(503));
        } finally {
            BlocksOnStarted.release.countDown();
        }
        MockServer mockServer = starting.get(30, SECONDS);
        try {
            assertThat(get(port, "/mockserver/ready").statusCode(), is(200));
        } finally {
            mockServer.stop();
        }
    }

    @Test
    public void shouldStopTheServerAndItsExtensionsWhenOnStartedTimesOut() throws Exception {
        int port = PortFactory.findFreePort();
        Configuration configuration = configuration()
            .extensions(First.class, BlocksOnStarted.class)
            .extensionStartTimeoutMillis(300L);

        ExtensionException failure = assertThrows(ExtensionException.class, () -> new MockServer(configuration, port));

        assertThat(failure.getMessage(), containsString("lifecycle-blocks-on-started did not return from onStarted within 300ms"));
        // the timed-out onStarted is interrupted, so it records nothing, and the later onStarted never runs
        assertThat(EVENTS, contains("start:lifecycle-blocks-on-started", "start:lifecycle-first",
            "stop:lifecycle-first", "stop:lifecycle-blocks-on-started"));
        // the port is free again: the refused server released it
        MockServer rebound = new MockServer(configuration(), port);
        rebound.stop();
    }

    @Test
    public void shouldGiveEachServerBuiltFromOneConfigurationItsOwnExtensionInstances() {
        Configuration configuration = configuration().extensions(First.class);

        MockServer one = new MockServer(configuration);
        MockServer two = new MockServer(configuration);
        try {
            List<LoadedExtension> first = one.getExtensionRegistry().extensions();
            List<LoadedExtension> second = two.getExtensionRegistry().extensions();
            assertThat(first, hasSize(1));
            assertThat(second, hasSize(1));
            assertThat(first.get(0).extension(), not(sameInstance(second.get(0).extension())));
        } finally {
            one.stop();
            two.stop();
        }
        assertNoExtensionThreadsRemain();
    }

    @Test
    public void shouldNotLetAConfigurationPutChangeTheExtensions() throws Exception {
        Configuration configuration = configuration().extensions(First.class.getName());

        MockServer mockServer = new MockServer(configuration);
        int port = mockServer.getLocalPort();
        try {
            HttpResponse<String> put = HTTP_CLIENT.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mockserver/configuration"))
                .PUT(HttpRequest.BodyPublishers.ofString("{\"extensions\":\"com.example.Injected\",\"extensionScanning\":true}"))
                .header("Content-Type", "application/json")
                .build(), HttpResponse.BodyHandlers.ofString());
            assertThat(put.statusCode(), is(200));

            HttpResponse<String> get = get(port, "/mockserver/configuration");
            assertThat(get.body(), containsString("\"extensions\" : \"" + First.class.getName() + "\""));
            assertThat(get.body(), not(containsString("com.example.Injected")));
            assertThat(configuration.extensions(), is(First.class.getName()));
            assertThat(configuration.extensionScanning(), is(false));
            assertThat(mockServer.getExtensionRegistry().extensions(), hasSize(1));
        } finally {
            mockServer.stop();
        }
    }

    @Test
    public void shouldStopStartedExtensionsWhenTheConstructorRefusesAfterLifeCycleIsBuilt() {
        Configuration configuration = configuration().extensions(First.class);

        assertThrows(IllegalArgumentException.class, () -> new MockServer(configuration, (Integer) null, "localhost"));

        assertThat(EVENTS, contains("start:lifecycle-first", "stop:lifecycle-first"));
        assertNoExtensionThreadsRemain();
    }

    @Test
    public void shouldWaitForTheExtensionsBoundedStopsWhenStopping() {
        MockServer withoutExtensions = new MockServer(configuration().stopDrainMillis(0L));
        MockServer withExtensions = new MockServer(configuration().stopDrainMillis(0L)
            .extensions(First.class, Second.class)
            .extensionStartTimeoutMillis(50_000L));
        try {
            assertThat(((LifeCycle) withExtensions).stopTimeoutMillis(), is(((LifeCycle) withoutExtensions).stopTimeoutMillis() + 2 * 50_000L));
        } finally {
            withoutExtensions.stop();
            withExtensions.stop();
        }
    }

    @Test
    public void shouldLoadNoExtensionsWhenNoneAreConfigured() {
        MockServer mockServer = new MockServer(configuration());
        try {
            assertThat(mockServer.getExtensionRegistry() == null, is(true));
        } finally {
            mockServer.stop();
        }
    }

    private static HttpResponse<String> get(int port, String path) throws Exception {
        return HTTP_CLIENT.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().timeout(Duration.ofSeconds(10)).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    private static void assertNoExtensionThreadsRemain() {
        long deadline = System.currentTimeMillis() + 5_000;
        List<String> remaining;
        do {
            remaining = Thread.getAllStackTraces().keySet().stream()
                .map(Thread::getName)
                .filter(name -> name.startsWith("MockServer-ext-") && name.contains("lifecycle-"))
                .collect(Collectors.toList());
            if (remaining.isEmpty()) {
                break;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        } while (System.currentTimeMillis() < deadline);
        assertThat(remaining, is(empty()));
    }
}
