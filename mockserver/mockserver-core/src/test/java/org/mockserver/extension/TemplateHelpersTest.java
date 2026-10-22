package org.mockserver.extension;

import io.netty.channel.DefaultEventLoop;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.action.http.HttpForwardTemplateActionHandler;
import org.mockserver.mock.action.http.HttpResponseTemplateActionHandler;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpTemplate;
import org.mockserver.serialization.model.HttpResponseDTO;
import org.mockserver.templates.engine.javascript.JavaScriptTemplateEngine;
import org.mockserver.templates.engine.mustache.MustacheTemplateEngine;
import org.mockserver.templates.engine.velocity.VelocityTemplateEngine;
import org.slf4j.event.Level;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpTemplate.template;

/**
 * Extension template helpers ({@code ext}): reachable from the primary response and forward template engines
 * (Velocity, Mustache and JavaScript), run on the helper executor with a real timeout and one budget per render,
 * refused on an event loop, from inside another helper and over the in-flight cap, a stub everywhere else, and
 * disabled with a WARN when HTTP/3 is enabled.
 */
public class TemplateHelpersTest {

    static final List<String> HELPER_THREADS = new CopyOnWriteArrayList<>();
    static final AtomicInteger SLOW_CALLS = new AtomicInteger();
    static volatile CountDownLatch blockEntered = new CountDownLatch(1);
    static volatile CountDownLatch blockRelease = new CountDownLatch(1);
    static volatile CountDownLatch interrupted = new CountDownLatch(1);
    static volatile Strings nestedTarget;

    public interface Strings {
        String upper(String value);

        String slow(long millis) throws InterruptedException;

        String hang();

        String block() throws InterruptedException;

        String nested();

        String boom();
    }

    public static class StringsHelper implements Strings {
        @Override
        public String upper(String value) {
            HELPER_THREADS.add(Thread.currentThread().getName());
            return value.toUpperCase();
        }

        @Override
        public String slow(long millis) throws InterruptedException {
            SLOW_CALLS.incrementAndGet();
            MILLISECONDS.sleep(millis);
            return "s";
        }

        @Override
        public String hang() {
            try {
                SECONDS.sleep(30);
            } catch (InterruptedException e) {
                interrupted.countDown();
            }
            return "never";
        }

        @Override
        public String block() throws InterruptedException {
            blockEntered.countDown();
            blockRelease.await(30, SECONDS);
            return "released";
        }

        @Override
        public String nested() {
            return nestedTarget.upper("inner");
        }

        @Override
        public String boom() {
            throw new IllegalStateException("jdbc://internal-db:5432 password=secret");
        }
    }

    public static class Acme implements TemplateHelperProvider {
        @Override
        public String name() {
            return "acme";
        }

        @Override
        public Map<Class<?>, Object> helpers() {
            return Map.of(Strings.class, new StringsHelper());
        }

        @Override
        public Map<String, MustacheHelper> mustacheHelpers() {
            return Map.of(
                "upper", section -> {
                    HELPER_THREADS.add(Thread.currentThread().getName());
                    return section.toUpperCase();
                },
                "hang", section -> {
                    SECONDS.sleep(30);
                    return "WRITTEN-AFTER-TIMEOUT";
                }
            );
        }
    }

    public static class NoHelpers implements Extension {
        @Override
        public String name() {
            return "no-helpers";
        }
    }

    public static class NotAnInterface implements TemplateHelperProvider {
        @Override
        public String name() {
            return "not-an-interface";
        }

        @Override
        public Map<Class<?>, Object> helpers() {
            return Map.of(StringsHelper.class, new StringsHelper());
        }
    }

    private CapturingLogger logger;
    private ExtensionRegistry registry;

    @Before
    @After
    public void reset() {
        HELPER_THREADS.clear();
        SLOW_CALLS.set(0);
        blockEntered = new CountDownLatch(1);
        blockRelease.countDown();
        blockRelease = new CountDownLatch(1);
        interrupted = new CountDownLatch(1);
        nestedTarget = null;
        if (registry != null) {
            registry.stop();
            registry = null;
        }
        logger = new CapturingLogger(configuration().logLevel(Level.INFO));
    }

    private TemplateHelpers start(Configuration configuration) {
        registry = ExtensionRegistry.start(configuration.extensions(Acme.class), logger);
        return registry.templateHelpers();
    }

    private static HttpResponse respond(HttpResponseTemplateActionHandler handler, HttpTemplate.TemplateType type, String template) {
        return handler.handle(template(type, template), request("/hello").withHeader("Host", "localhost"));
    }

    @Test
    public void shouldCallAHelperFromAVelocityResponseTemplateOnAHelperThread() {
        Configuration configuration = configuration();
        HttpResponseTemplateActionHandler handler = new HttpResponseTemplateActionHandler(logger, configuration, start(configuration));

        HttpResponse response = respond(handler, HttpTemplate.TemplateType.VELOCITY, "{ \"statusCode\": 200, \"body\": \"$ext.acme.Strings.upper($request.path)\" }");

        assertThat(response.getBodyAsString(), is("/HELLO"));
        assertThat(HELPER_THREADS.get(0), startsWith("MockServer-ext-helper-"));
    }

    @Test
    public void shouldCallAHelperFromAVelocityTemplateUnderTheSecureUberspector() {
        Configuration configuration = configuration().velocityDisallowClassLoading(true);
        HttpResponseTemplateActionHandler handler = new HttpResponseTemplateActionHandler(logger, configuration, start(configuration));

        HttpResponse response = respond(handler, HttpTemplate.TemplateType.VELOCITY, "{ \"statusCode\": 200, \"body\": \"$ext.acme.Strings.upper('secure')\" }");

        assertThat(response.getBodyAsString(), is("SECURE"));
    }

    @Test
    public void shouldCallAHelperFromAMustacheResponseTemplateWithTheRenderedSection() {
        Configuration configuration = configuration();
        HttpResponseTemplateActionHandler handler = new HttpResponseTemplateActionHandler(logger, configuration, start(configuration));

        HttpResponse response = respond(handler, HttpTemplate.TemplateType.MUSTACHE, "{ \"statusCode\": 200, \"body\": \"{{#ext.acme.upper}}path {{request.path}}{{/ext.acme.upper}}\" }");

        assertThat(response.getBodyAsString(), is("PATH /HELLO"));
        assertThat(HELPER_THREADS.get(0), startsWith("MockServer-ext-helper-"));
    }

    @Test
    public void shouldCallAHelperFromAJavaScriptResponseTemplate() {
        assumeTrue("GraalJS is not on the classpath", JavaScriptTemplateEngine.isPolyglotAvailable());
        Configuration configuration = configuration();
        HttpResponseTemplateActionHandler handler = new HttpResponseTemplateActionHandler(logger, configuration, start(configuration));

        HttpResponse response = respond(handler, HttpTemplate.TemplateType.JAVASCRIPT, "return { statusCode: 200, body: ext.acme.Strings.upper(request.path) };");

        assertThat(response.getBodyAsString(), is("/HELLO"));
        assertThat(HELPER_THREADS.get(0), startsWith("MockServer-ext-helper-"));
    }

    @Test
    public void shouldCallAHelperFromAForwardTemplate() throws Exception {
        Configuration configuration = configuration();
        TemplateHelpers helpers = start(configuration);
        // the forward template's engine is what renders; it is the forward handler's engine for this template type
        java.lang.reflect.Method resolve = HttpForwardTemplateActionHandler.class.getDeclaredMethod("resolveTemplateEngine", HttpTemplate.class);
        resolve.setAccessible(true);
        HttpForwardTemplateActionHandler handler = new HttpForwardTemplateActionHandler(logger, configuration, null, helpers);
        org.mockserver.templates.engine.TemplateEngine engine = (org.mockserver.templates.engine.TemplateEngine) resolve.invoke(handler, template(HttpTemplate.TemplateType.VELOCITY));

        HttpRequest forwarded = engine.executeTemplate("{ \"path\": \"$ext.acme.Strings.upper($request.path)\" }", request("/forward"), org.mockserver.serialization.model.HttpRequestDTO.class);

        assertThat(forwarded.getPath().getValue(), is("/FORWARD"));
    }

    @Test
    public void shouldFailTheTemplateAndInterruptAHelperThatDoesNotReturnInTime() throws Exception {
        Configuration configuration = configuration().extensionHelperTimeoutMillis(200L);
        TemplateHelpers helpers = start(configuration);
        HttpResponseTemplateActionHandler handler = new HttpResponseTemplateActionHandler(logger, configuration, helpers);

        long started = System.nanoTime();
        RuntimeException failure = assertThrows(RuntimeException.class, () -> respond(handler, HttpTemplate.TemplateType.VELOCITY, "{ \"statusCode\": 200, \"body\": \"$ext.acme.Strings.hang()\" }"));
        long elapsedMillis = MILLISECONDS.convert(System.nanoTime() - started, java.util.concurrent.TimeUnit.NANOSECONDS);

        assertThat(fullMessage(failure), containsString("did not return within the render's budget of 200ms"));
        assertThat(elapsedMillis, lessThan(5_000L));
        assertThat("the helper thread was interrupted", interrupted.await(5, SECONDS), is(true));
        awaitNoHelperInFlight(helpers);
    }

    @Test
    public void shouldNeverWriteTheOutputOfAMustacheHelperThatTimedOut() {
        Configuration configuration = configuration().extensionHelperTimeoutMillis(150L);
        HttpResponseTemplateActionHandler handler = new HttpResponseTemplateActionHandler(logger, configuration, start(configuration));

        RuntimeException failure = assertThrows(RuntimeException.class, () -> respond(handler, HttpTemplate.TemplateType.MUSTACHE, "{ \"statusCode\": 200, \"body\": \"{{#ext.acme.hang}}x{{/ext.acme.hang}}\" }"));

        assertThat(fullMessage(failure), containsString("did not return within the render's budget of 150ms"));
        assertThat(fullMessage(failure).contains("WRITTEN-AFTER-TIMEOUT"), is(false));
    }

    @Test
    public void shouldBoundEveryHelperCallOfOneRenderByOneBudget() {
        Configuration configuration = configuration().extensionHelperTimeoutMillis(300L);
        HttpResponseTemplateActionHandler handler = new HttpResponseTemplateActionHandler(logger, configuration, start(configuration));

        long started = System.nanoTime();
        RuntimeException failure = assertThrows(RuntimeException.class, () -> respond(handler, HttpTemplate.TemplateType.VELOCITY,
            "{ \"statusCode\": 200, \"body\": \"#foreach($i in [1..20])$ext.acme.Strings.slow(100)#end\" }"));
        long elapsedMillis = MILLISECONDS.convert(System.nanoTime() - started, java.util.concurrent.TimeUnit.NANOSECONDS);

        assertThat(fullMessage(failure), containsString("budget of 300ms"));
        assertThat("twenty 100ms calls stop at one 300ms budget", elapsedMillis, lessThan(1_500L));
        assertThat(SLOW_CALLS.get(), lessThanOrEqualTo(4));
    }

    @Test
    public void shouldGiveEachRenderItsOwnBudget() {
        Configuration configuration = configuration().extensionHelperTimeoutMillis(400L);
        HttpResponseTemplateActionHandler handler = new HttpResponseTemplateActionHandler(logger, configuration, start(configuration));
        String template = "{ \"statusCode\": 200, \"body\": \"$ext.acme.Strings.slow(250)\" }";

        assertThat(respond(handler, HttpTemplate.TemplateType.VELOCITY, template).getBodyAsString(), is("s"));
        assertThat("a second render on the same thread starts a new budget", respond(handler, HttpTemplate.TemplateType.VELOCITY, template).getBodyAsString(), is("s"));
    }

    @Test
    public void shouldRefuseAHelperCallBeyondTheInFlightCap() throws Exception {
        Configuration configuration = configuration().extensionHelperMaxInFlight(1).extensionHelperTimeoutMillis(10_000L);
        TemplateHelpers helpers = start(configuration);
        HttpResponseTemplateActionHandler handler = new HttpResponseTemplateActionHandler(logger, configuration, helpers);
        CompletableFuture<HttpResponse> blocked = CompletableFuture.supplyAsync(() -> respond(handler, HttpTemplate.TemplateType.VELOCITY, "{ \"statusCode\": 200, \"body\": \"$ext.acme.Strings.block()\" }"));
        try {
            assertThat(blockEntered.await(10, SECONDS), is(true));

            RuntimeException failure = assertThrows(RuntimeException.class, () -> respond(handler, HttpTemplate.TemplateType.VELOCITY, "{ \"statusCode\": 200, \"body\": \"$ext.acme.Strings.upper('x')\" }"));

            assertThat(fullMessage(failure), containsString("1 helper calls are already running (mockserver.extensionHelperMaxInFlight)"));
        } finally {
            blockRelease.countDown();
        }
        assertThat(blocked.get(10, SECONDS).getBodyAsString(), is("released"));
    }

    @Test
    public void shouldRefuseAHelperCalledFromInsideAnotherHelper() {
        Configuration configuration = configuration();
        TemplateHelpers helpers = start(configuration);
        nestedTarget = (Strings) ((Map<?, ?>) ((Map<?, ?>) helpers.namespace()).get("acme")).get("Strings");
        HttpResponseTemplateActionHandler handler = new HttpResponseTemplateActionHandler(logger, configuration, helpers);

        RuntimeException failure = assertThrows(RuntimeException.class, () -> respond(handler, HttpTemplate.TemplateType.VELOCITY, "{ \"statusCode\": 200, \"body\": \"$ext.acme.Strings.nested()\" }"));

        assertThat(fullMessage(failure), containsString("a helper cannot call another helper"));
    }

    @Test
    public void shouldRefuseAHelperCallOnAnEventLoop() throws Exception {
        Configuration configuration = configuration();
        HttpResponseTemplateActionHandler handler = new HttpResponseTemplateActionHandler(logger, configuration, start(configuration));
        DefaultEventLoop eventLoop = new DefaultEventLoop();
        try {
            ExecutionException failure = assertThrows(ExecutionException.class, () -> eventLoop.submit(() ->
                respond(handler, HttpTemplate.TemplateType.VELOCITY, "{ \"statusCode\": 200, \"body\": \"$ext.acme.Strings.upper('x')\" }")).get(10, SECONDS));

            assertThat(fullMessage(failure), containsString("was called on an event loop thread"));
            assertThat("the helper never ran", HELPER_THREADS.isEmpty(), is(true));
        } finally {
            eventLoop.shutdownGracefully(0, 1, SECONDS);
        }
    }

    @Test
    public void shouldNameOnlyTheExceptionTypeOfAHelperThatThrowsAndLogTheDetail() {
        Configuration configuration = configuration();
        HttpResponseTemplateActionHandler handler = new HttpResponseTemplateActionHandler(logger, configuration, start(configuration));

        RuntimeException failure = assertThrows(RuntimeException.class, () -> respond(handler, HttpTemplate.TemplateType.VELOCITY, "{ \"statusCode\": 200, \"body\": \"$ext.acme.Strings.boom()\" }"));

        assertThat(fullMessage(failure), containsString("extension template helper ext.acme.Strings.boom failed with IllegalStateException; the MockServer log has the details"));
        // every message above the helper's own exception, which is what can reach a client, leaves the detail out
        StringBuilder aboveHelper = new StringBuilder();
        for (Throwable cause = failure; cause != null && !(cause instanceof IllegalStateException); cause = cause.getCause()) {
            aboveHelper.append(cause.getMessage()).append(" | ");
        }
        assertThat(aboveHelper.toString().contains("secret"), is(false));
        assertThat(logger.messages(Level.WARN), hasItem(containsString("ext.acme.Strings.boom")));
    }

    @Test
    public void shouldClearTheStubWhenTheServerStopsAndWhenAConfigurationIsReusedWithoutHelpers() {
        Configuration configuration = configuration();
        start(configuration);
        assertThat(configuration.extensionTemplateHelperStub(), notNullValue());

        registry.stop();
        registry = null;

        assertThat(configuration.extensionTemplateHelperStub(), nullValue());
        configuration.extensionTemplateHelperStub(ExtensionHelperNamespace.unavailable("left over"));
        registry = ExtensionRegistry.start(configuration.extensions(NoHelpers.class), logger);
        assertThat("a configuration reused without template helpers binds nothing", configuration.extensionTemplateHelperStub(), nullValue());
    }

    @Test
    public void shouldFailLoudlyForAHelperThatDoesNotExist() {
        Configuration configuration = configuration();
        HttpResponseTemplateActionHandler handler = new HttpResponseTemplateActionHandler(logger, configuration, start(configuration));

        RuntimeException failure = assertThrows(RuntimeException.class, () -> respond(handler, HttpTemplate.TemplateType.VELOCITY, "{ \"statusCode\": 200, \"body\": \"$ext.nope.Strings.upper('x')\" }"));

        assertThat(fullMessage(failure), containsString("there is no extension template helper ext.nope"));
    }

    @Test
    public void shouldGiveEveryOtherTemplateAStubThatFails() {
        assumeTrue("GraalJS is not on the classpath", JavaScriptTemplateEngine.isPolyglotAvailable());
        Configuration configuration = configuration();
        start(configuration);
        MockServerLogger engineLogger = new MockServerLogger(configuration, TemplateHelpersTest.class);
        HttpRequest request = request("/stubbed");

        // a secondary action, webhook, delay or file body renders through an engine built without the helpers
        assertStub(() -> new VelocityTemplateEngine(engineLogger, configuration).renderTemplate("$ext.acme.Strings.upper('x')", request));
        assertStub(() -> new MustacheTemplateEngine(engineLogger, configuration).renderTemplate("{{#ext.acme.upper}}x{{/ext.acme.upper}}", request));
        assertStub(() -> new JavaScriptTemplateEngine(engineLogger, configuration).executeTemplate("return { statusCode: 200, body: ext.acme.Strings.upper('x') };", request, HttpResponseDTO.class));
        assertStub(() -> new HttpResponseTemplateActionHandler(engineLogger, configuration).handle(template(HttpTemplate.TemplateType.VELOCITY, "{ \"statusCode\": 200, \"body\": \"$ext.acme.Strings.upper('x')\" }"), request));
        assertThat("no helper ran", HELPER_THREADS.isEmpty(), is(true));
    }

    @Test
    public void shouldDisableHelpersWithAWarningWhenHttp3IsEnabled() {
        Configuration configuration = configuration().http3Port(4433);

        TemplateHelpers helpers = start(configuration);

        assertThat(helpers, nullValue());
        LoadedExtension acme = registry.extensions().get(0);
        assertThat(acme.isHookEnabled(ExtensionHook.TEMPLATE_HELPER), is(false));
        assertThat(acme.disabledReason(ExtensionHook.TEMPLATE_HELPER), containsString("HTTP/3 is enabled (http3Port 4433)"));
        assertThat(logger.messages(Level.WARN), hasItem(containsString("HTTP/3 is enabled")));
        // response templates then use the shared handler, whose engines bind the stub
        RuntimeException failure = assertThrows(RuntimeException.class, () -> respond(new HttpResponseTemplateActionHandler(logger, configuration), HttpTemplate.TemplateType.VELOCITY,
            "{ \"statusCode\": 200, \"body\": \"$ext.acme.Strings.upper('x')\" }"));
        assertThat(fullMessage(failure), containsString("extension template helpers ($ext) are disabled because HTTP/3 is enabled"));
        assertThat("no helper ran", HELPER_THREADS.isEmpty(), is(true));
    }

    @Test
    public void shouldRefuseStartUpWhenAHelperIsNotAnInterface() {
        ExtensionException failure = assertThrows(ExtensionException.class, () -> ExtensionRegistry.start(configuration().extensions(NotAnInterface.class), logger));

        assertThat(failure.getMessage(), containsString("which is not a public interface"));
    }

    @Test
    public void shouldBindNothingWhenNoExtensionOffersHelpers() {
        Configuration configuration = configuration();
        MockServerLogger engineLogger = new MockServerLogger(configuration, TemplateHelpersTest.class);

        assertThat(configuration.extensionTemplateHelperStub(), nullValue());
        assertThat(new VelocityTemplateEngine(engineLogger, configuration).renderTemplate("$ext.acme", request("/")), is("$ext.acme"));
    }

    @Test
    public void shouldShutDownTheHelperExecutorWhenTheRegistryStops() {
        Configuration configuration = configuration();
        TemplateHelpers helpers = start(configuration);
        assertThat(helpers, notNullValue());
        HttpResponseTemplateActionHandler handler = new HttpResponseTemplateActionHandler(logger, configuration, helpers);

        registry.stop();

        RuntimeException failure = assertThrows(RuntimeException.class, () -> respond(handler, HttpTemplate.TemplateType.VELOCITY, "{ \"statusCode\": 200, \"body\": \"$ext.acme.Strings.upper('x')\" }"));
        assertThat(fullMessage(failure), containsString("the server is stopping"));
    }

    private static void assertStub(Runnable render) {
        RuntimeException failure = assertThrows(RuntimeException.class, render::run);
        assertThat(fullMessage(failure), containsString("can only be called from an httpResponseTemplate or httpForwardTemplate"));
    }

    private static void awaitNoHelperInFlight(TemplateHelpers helpers) throws InterruptedException {
        long deadline = System.nanoTime() + SECONDS.toNanos(5);
        while (helpers.inFlight() > 0 && System.nanoTime() < deadline) {
            MILLISECONDS.sleep(10);
        }
        assertThat("the slot is released once the interrupted helper returns", helpers.inFlight(), is(0));
    }

    private static String fullMessage(Throwable throwable) {
        StringBuilder message = new StringBuilder();
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            message.append(cause.getMessage()).append(" | ");
        }
        return message.toString();
    }

    private static final class CapturingLogger extends MockServerLogger {
        private final List<String> entries = new CopyOnWriteArrayList<>();

        private CapturingLogger(Configuration configuration) {
            super(configuration, TemplateHelpersTest.class);
        }

        @Override
        public void logEvent(LogEntry logEntry) {
            entries.add(logEntry.getLogLevel() + " " + logEntry.getMessage());
        }

        List<String> messages(Level level) {
            return entries.stream().filter(entry -> entry.startsWith(level + " ")).collect(Collectors.toList());
        }
    }
}
