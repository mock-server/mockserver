package org.mockserver.inprocess;

import org.junit.Test;
import org.mockserver.authentication.basic.PasswordHash;
import org.mockserver.configuration.Configuration;
import org.mockserver.extension.AdminApiExtension;
import org.mockserver.extension.Extension;
import org.mockserver.extension.ExtensionContext;
import org.mockserver.extension.ExtensionException;
import org.mockserver.extension.MustacheHelper;
import org.mockserver.extension.ServeEvent;
import org.mockserver.extension.ServeEventListener;
import org.mockserver.extension.TemplateHelperProvider;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThrows;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpTemplate.TemplateType.MUSTACHE;
import static org.mockserver.model.HttpTemplate.TemplateType.VELOCITY;
import static org.mockserver.model.HttpTemplate.template;

/**
 * Extensions in an in-process MockServer: started before its state is built, told it has started (with no ports),
 * stopped in reverse on {@code close()} or a refused start, and each hook reachable without a socket: admin routes
 * through {@code handle()}, serve events, and {@code $ext} template helpers.
 */
public class InProcessMockServerExtensionTest {

    private final List<String> events = new CopyOnWriteArrayList<>();
    private final BlockingQueue<ServeEvent> served = new LinkedBlockingQueue<>();

    public abstract static class Recording implements Extension {
        private final List<String> events;

        Recording(List<String> events) {
            this.events = events;
        }

        @Override
        public void start(ExtensionContext context) {
            events.add("start:" + name());
        }

        @Override
        public void onStarted(List<Integer> ports) {
            events.add("onStarted:" + name() + ":" + ports);
        }

        @Override
        public void stop() {
            events.add("stop:" + name());
        }
    }

    public static class Admin extends Recording implements AdminApiExtension {
        Admin(List<String> events) {
            super(events);
        }

        @Override
        public String name() {
            return "inproc-admin";
        }

        @Override
        public HttpResponse handle(HttpRequest request, String subPath) {
            return response().withStatusCode(200).withBody("admin " + request.getMethod().getValue() + " " + subPath);
        }
    }

    public static class Listener extends Recording implements ServeEventListener {
        private final BlockingQueue<ServeEvent> served;

        Listener(List<String> events, BlockingQueue<ServeEvent> served) {
            super(events);
            this.served = served;
        }

        @Override
        public String name() {
            return "inproc-listener";
        }

        @Override
        public void onServed(ServeEvent event) {
            served.add(event);
        }
    }

    public interface Greetings {
        String greet(String name);
    }

    public static class Helpers extends Recording implements TemplateHelperProvider {
        Helpers(List<String> events) {
            super(events);
        }

        @Override
        public String name() {
            return "greeter";
        }

        @Override
        public Map<Class<?>, Object> helpers() {
            return Map.of(Greetings.class, (Greetings) name -> "hello " + name);
        }

        @Override
        public Map<String, MustacheHelper> mustacheHelpers() {
            return Map.of("greet", section -> "hello " + section);
        }
    }

    public static class FailsOnStarted extends Recording {
        FailsOnStarted(List<String> events) {
            super(events);
        }

        @Override
        public String name() {
            return "inproc-fails-on-started";
        }

        @Override
        public void onStarted(List<Integer> ports) {
            throw new IllegalStateException("refusing to finish starting");
        }
    }

    private Configuration withAllHooks() {
        return configuration().extensions(() -> new Admin(events), () -> new Listener(events, served), () -> new Helpers(events));
    }

    @Test
    public void shouldStartBeforeStateIsBuiltNotifyWithNoPortsAndStopInReverseOnClose() {
        InProcessMockServer server = new InProcessMockServer(withAllHooks());
        try {
            assertThat(events, contains(
                "start:greeter", "start:inproc-admin", "start:inproc-listener",
                "onStarted:greeter:[]", "onStarted:inproc-admin:[]", "onStarted:inproc-listener:[]"
            ));
            assertThat(server.getHttpState().isInitializationComplete(), is(true));
        } finally {
            server.close();
        }

        assertThat(events.subList(6, events.size()), contains("stop:inproc-listener", "stop:inproc-admin", "stop:greeter"));
        server.close();
        assertThat(events.size(), is(9));
    }

    @Test
    public void shouldRefuseToConstructAndStopTheStartedExtensionsInReverseWhenOnStartedFails() throws InterruptedException {
        List<String> remaining = runInOwnThreadGroup(failure -> {
            ExtensionException refused = assertThrows(ExtensionException.class, () -> new InProcessMockServer(configuration().extensions(
                () -> new Admin(events), () -> new FailsOnStarted(events), () -> new Helpers(events)
            )));
            assertThat(refused.getMessage(), containsString("inproc-fails-on-started failed in onStarted"));
        });

        assertThat(events, contains(
            "start:greeter", "start:inproc-admin", "start:inproc-fails-on-started",
            "onStarted:greeter:[]", "onStarted:inproc-admin:[]",
            "stop:inproc-fails-on-started", "stop:inproc-admin", "stop:greeter"
        ));
        assertThat("threads still running after a refused start", remaining, is(empty()));
    }

    @Test
    public void shouldServeAnExtensionAdminRouteThroughHandle() {
        try (InProcessMockServer server = new InProcessMockServer(withAllHooks())) {
            HttpResponse routed = server.handle(request().withMethod("POST").withPath("/mockserver/extensions/inproc-admin/things/1"));
            assertThat(routed.getStatusCode(), is(200));
            assertThat(routed.getBodyAsString(), is("admin POST /things/1"));

            assertThat(server.handle(request().withMethod("GET").withPath("/mockserver/extensions/inproc-admin")).getBodyAsString(), is("admin GET /"));
            // a name no extension has falls through, as it does with no extension loaded
            assertThat(server.handle(request().withMethod("GET").withPath("/mockserver/extensions/unknown/x")).getStatusCode(), is(404));
        }
    }

    @Test
    public void shouldApplyControlPlaneAuthenticationToAdminRoutesThroughHandle() {
        Configuration configuration = withAllHooks()
            .controlPlaneBasicAuthenticationRequired(true)
            .controlPlaneBasicAuthenticationAllowInsecure(true)
            .controlPlaneBasicAuthenticationCredentials("admin:" + PasswordHash.pbkdf2Sha256("admin-pass", 10_000));
        try (InProcessMockServer server = new InProcessMockServer(configuration)) {
            HttpRequest route = request().withMethod("GET").withPath("/mockserver/extensions/inproc-admin/secret");

            assertThat(server.handle(route).getStatusCode(), is(401));

            String credentials = Base64.getEncoder().encodeToString("admin:admin-pass".getBytes(StandardCharsets.UTF_8));
            HttpResponse authenticated = server.handle(route.clone().withHeader("Authorization", "Basic " + credentials));
            assertThat(authenticated.getStatusCode(), is(200));
            assertThat(authenticated.getBodyAsString(), is("admin GET /secret"));
        }
    }

    @Test
    public void shouldDeliverServeEventsForRequestsHandledInProcess() throws InterruptedException {
        // a not-matched response is logged, and so served, only at INFO or finer
        try (InProcessMockServer server = new InProcessMockServer(withAllHooks().logLevel("INFO"))) {
            server.upsert(new Expectation(request().withPath("/served")).thenRespond(response().withBody("served")));

            assertThat(server.handle(request().withPath("/served")).getStatusCode(), is(200));
            ServeEvent mocked = served.poll(10, TimeUnit.SECONDS);
            assertThat(mocked, notNullValue());
            assertThat(mocked.disposition(), is(ServeEvent.Disposition.MOCKED));
            assertThat(((HttpRequest) mocked.request()).getPath().getValue(), is("/served"));
            assertThat(mocked.response().getBodyAsString(), is("served"));

            assertThat(server.handle(request().withPath("/not-matched")).getStatusCode(), is(404));
            ServeEvent notMatched = served.poll(10, TimeUnit.SECONDS);
            assertThat(notMatched, notNullValue());
            assertThat(notMatched.disposition(), is(ServeEvent.Disposition.NOT_MATCHED));
        }
    }

    @Test
    public void shouldCallExtensionTemplateHelpersFromResponseTemplates() throws Exception {
        try (InProcessMockServer server = new InProcessMockServer(withAllHooks())) {
            server.upsert(
                new Expectation(request().withPath("/velocity")).thenRespond(template(VELOCITY, "{ \"statusCode\": 200, \"body\": \"$ext.greeter.Greetings.greet('velocity')\" }")),
                new Expectation(request().withPath("/mustache")).thenRespond(template(MUSTACHE, "{ \"statusCode\": 200, \"body\": \"{{#ext.greeter.greet}}mustache{{/ext.greeter.greet}}\" }"))
            );

            assertThat(server.handle(request().withPath("/velocity")).getBodyAsString(), is("hello velocity"));
            assertThat(server.handle(request().withPath("/mustache")).getBodyAsString(), is("hello mustache"));
            // handleAsync renders on the template pool rather than the caller
            assertThat(server.handleAsync(request().withPath("/velocity")).get(10, TimeUnit.SECONDS).getBodyAsString(), is("hello velocity"));
        }
    }

    @Test
    public void shouldLeaveNoThreadsBehindWhenClosedWithExtensionsLoaded() throws InterruptedException {
        List<String> remaining = runInOwnThreadGroup(failure -> {
            try (InProcessMockServer server = new InProcessMockServer(withAllHooks())) {
                server.upsert(new Expectation(request().withPath("/template")).thenRespond(template(VELOCITY, "{ \"statusCode\": 200, \"body\": \"$ext.greeter.Greetings.greet('x')\" }")));
                assertThat(server.handle(request().withPath("/template")).getBodyAsString(), is("hello x"));
                assertThat(server.handleAsync(request().withPath("/template")).get(10, TimeUnit.SECONDS).getBodyAsString(), is("hello x"));
                assertThat(server.handle(request().withPath("/mockserver/extensions/inproc-admin/x")).getStatusCode(), is(200));
                assertThat(served.poll(10, TimeUnit.SECONDS), notNullValue());
            } catch (Exception e) {
                failure.set(e);
            }
        });

        assertThat(events.subList(events.size() - 3, events.size()), contains("stop:inproc-listener", "stop:inproc-admin", "stop:greeter"));
        assertThat("threads still running after close()", remaining, is(empty()));
    }

    /**
     * Runs {@code body} on a thread in a new group, so every thread the server starts lands in that group, and
     * returns the names of the group's threads still alive (after up to 10 seconds) once {@code body} has finished.
     */
    private static List<String> runInOwnThreadGroup(Consumer<AtomicReference<Throwable>> body) throws InterruptedException {
        ThreadGroup group = new ThreadGroup("in-process-extension-thread-check");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread driver = new Thread(group, () -> {
            try {
                body.accept(failure);
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        }, "in-process-extension-driver");
        driver.start();
        driver.join(TimeUnit.SECONDS.toMillis(60));
        assertThat("driver did not finish", driver.isAlive(), is(false));
        if (failure.get() != null) {
            throw new AssertionError("driver failed", failure.get());
        }
        List<String> remaining = liveThreadNames(group);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!remaining.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(50);
            remaining = liveThreadNames(group);
        }
        return remaining;
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
}
