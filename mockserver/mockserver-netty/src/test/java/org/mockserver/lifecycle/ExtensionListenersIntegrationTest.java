package org.mockserver.lifecycle;

import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.extension.ChangeCause;
import org.mockserver.extension.ExpectationChange;
import org.mockserver.extension.ExpectationLifecycleListener;
import org.mockserver.extension.ServeEvent;
import org.mockserver.extension.ServeEventListener;
import org.mockserver.extension.SettingsListener;
import org.mockserver.netty.MockServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.mockserver.configuration.Configuration.configuration;

/**
 * Listener extensions on a Netty server: a listener that stops reading never slows or blocks the server, and once
 * it reads again it is told about served requests, exhausted expectations and configuration updates.
 */
public class ExtensionListenersIntegrationTest {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public static class Listener implements ServeEventListener, ExpectationLifecycleListener, SettingsListener {
        final BlockingQueue<Object> events = new LinkedBlockingQueue<>();
        final CountDownLatch gate = new CountDownLatch(1);

        @Override
        public String name() {
            return "listeners-integration";
        }

        @Override
        public void onServed(ServeEvent event) {
            receive(event);
        }

        @Override
        public void onChange(ExpectationChange change) {
            receive(change);
        }

        @Override
        public void onConfigurationUpdated(Configuration configuration) {
            receive(configuration);
        }

        private void receive(Object event) {
            try {
                gate.await(60, SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            events.add(event);
        }
    }

    @Test
    public void shouldNeverBlockTheServerOnAListenerThatStopsReading() throws Exception {
        Listener listener = new Listener();
        MockServer mockServer = new MockServer(configuration().extensionListenerQueueSize(5).stopDrainMillis(1_000L).extensions(() -> listener));
        int port = mockServer.getLocalPort();
        try {
            // the listener is stuck, so its queue fills at once and every later event is dropped, never waited on
            long started = System.nanoTime();
            for (int i = 0; i < 30; i++) {
                assertThat(send(port, "PUT", "/mockserver/expectation", onceExpectation("/stuck-" + i)).statusCode(), is(201));
                assertThat(send(port, "GET", "/stuck-" + i, null).statusCode(), is(200));
                assertThat(send(port, "GET", "/unmatched-" + i, null).statusCode(), is(404));
            }
            long elapsedSeconds = SECONDS.convert(System.nanoTime() - started, java.util.concurrent.TimeUnit.NANOSECONDS);
            assertThat("90 requests must not wait for the stuck listener", elapsedSeconds, lessThan(30L));

            listener.gate.countDown();
            awaitQuiet(listener);
            listener.events.clear();

            // reading again: a once() expectation served over HTTP is reported exhausted, with no second request
            assertThat(send(port, "PUT", "/mockserver/expectation", onceExpectation("/after")).statusCode(), is(201));
            assertThat(send(port, "GET", "/after", null).statusCode(), is(200));
            assertThat(send(port, "PUT", "/mockserver/configuration", "{\"maxExpectations\": 500}").statusCode(), is(200));

            boolean added = false;
            boolean exhausted = false;
            boolean served = false;
            boolean settings = false;
            long deadline = System.currentTimeMillis() + 20_000;
            while (!(added && exhausted && served && settings) && System.currentTimeMillis() < deadline) {
                Object event = listener.events.poll(1, SECONDS);
                if (event instanceof ExpectationChange) {
                    ExpectationChange change = (ExpectationChange) event;
                    added |= change.type() == ExpectationChange.Type.ADDED && change.cause() == ChangeCause.API;
                    exhausted |= change.type() == ExpectationChange.Type.REMOVED && change.cause() == ChangeCause.EXHAUSTED;
                } else if (event instanceof ServeEvent) {
                    ServeEvent serveEvent = (ServeEvent) event;
                    served |= serveEvent.disposition() == ServeEvent.Disposition.MOCKED && serveEvent.expectationId() != null;
                } else if (event instanceof Configuration) {
                    settings = true;
                }
            }
            assertThat("added", added, is(true));
            assertThat("exhausted", exhausted, is(true));
            assertThat("served", served, is(true));
            assertThat("settings", settings, is(true));
        } finally {
            listener.gate.countDown();
            mockServer.stop();
        }
        assertNoListenerThreadRemains();
    }

    private static String onceExpectation(String path) {
        return "{\"httpRequest\": {\"path\": \"" + path + "\"}, \"httpResponse\": {\"body\": \"ok\"}, \"times\": {\"remainingTimes\": 1, \"unlimited\": false}}";
    }

    private static HttpResponse<String> send(int port, String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(10));
        builder.method(method, body != null ? HttpRequest.BodyPublishers.ofString(body) : HttpRequest.BodyPublishers.noBody());
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        return HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void awaitQuiet(Listener listener) throws InterruptedException {
        int size;
        do {
            size = listener.events.size();
            Thread.sleep(300);
        } while (size != listener.events.size());
    }

    private static void assertNoListenerThreadRemains() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        List<String> remaining;
        do {
            remaining = Thread.getAllStackTraces().keySet().stream()
                .map(Thread::getName)
                .filter(name -> name.equals("MockServer-ext-listener-listeners-integration"))
                .collect(Collectors.toList());
            if (!remaining.isEmpty()) {
                Thread.sleep(20);
            }
        } while (!remaining.isEmpty() && System.currentTimeMillis() < deadline);
        assertThat(remaining, is(empty()));
    }
}
