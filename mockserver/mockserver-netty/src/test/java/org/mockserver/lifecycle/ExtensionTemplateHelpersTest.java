package org.mockserver.lifecycle;

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http3.DefaultHttp3Headers;
import io.netty.handler.codec.http3.Http3Headers;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.templates.engine.velocity.VelocityTemplateEngine;
import org.mockserver.extension.ExtensionHook;
import org.mockserver.extension.MustacheHelper;
import org.mockserver.extension.TemplateHelperProvider;
import org.mockserver.model.Delay;
import org.mockserver.netty.MockServer;
import org.mockserver.netty.http3.Http3Server;
import org.mockserver.netty.http3.Http3TestClient;
import org.mockserver.templates.engine.javascript.JavaScriptTemplateEngine;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpTemplate.TemplateType.JAVASCRIPT;
import static org.mockserver.model.HttpTemplate.TemplateType.MUSTACHE;
import static org.mockserver.model.HttpTemplate.TemplateType.VELOCITY;
import static org.mockserver.model.HttpTemplate.template;
import static org.mockserver.netty.http3.Http3TestServer.startWithHttp3;

/**
 * Extension template helpers on a Netty server: called from Velocity, Mustache and JavaScript response templates on
 * the helper executor, over HTTP/1.1 and HTTP/3, and a render waiting on a slow helper never holds the event loop.
 */
@SuppressWarnings("deprecation") // NioEventLoopGroup deprecation in Netty 4.2
public class ExtensionTemplateHelpersTest {

    static final List<String> HELPER_THREADS = new CopyOnWriteArrayList<>();
    static volatile CountDownLatch slowEntered = new CountDownLatch(1);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public interface Greetings {
        String greet(String name);

        String slow(long millis) throws InterruptedException;
    }

    public static class GreetingsHelper implements Greetings {
        @Override
        public String greet(String name) {
            HELPER_THREADS.add(Thread.currentThread().getName());
            return "hello " + name;
        }

        @Override
        public String slow(long millis) throws InterruptedException {
            slowEntered.countDown();
            MILLISECONDS.sleep(millis);
            return "slow";
        }
    }

    public static class Greeter implements TemplateHelperProvider {
        @Override
        public String name() {
            return "greeter";
        }

        @Override
        public Map<Class<?>, Object> helpers() {
            return Map.of(Greetings.class, new GreetingsHelper());
        }

        @Override
        public Map<String, MustacheHelper> mustacheHelpers() {
            return Map.of("greet", section -> {
                HELPER_THREADS.add(Thread.currentThread().getName());
                return "hello " + section;
            });
        }
    }

    @Before
    public void reset() {
        HELPER_THREADS.clear();
        slowEntered = new CountDownLatch(1);
    }

    @Test
    public void shouldCallHelpersFromVelocityMustacheAndJavaScriptResponseTemplates() throws Exception {
        MockServer mockServer = new MockServer(configuration().extensions(Greeter.class));
        try (MockServerClient client = new MockServerClient("127.0.0.1", mockServer.getLocalPort())) {
            client.when(request("/velocity")).respond(template(VELOCITY, "{ \"statusCode\": 200, \"body\": \"$ext.greeter.Greetings.greet('velocity')\" }"));
            client.when(request("/mustache")).respond(template(MUSTACHE, "{ \"statusCode\": 200, \"body\": \"{{#ext.greeter.greet}}mustache{{/ext.greeter.greet}}\" }"));

            assertThat(get(mockServer, "/velocity").body(), is("hello velocity"));
            assertThat(get(mockServer, "/mustache").body(), is("hello mustache"));
            if (JavaScriptTemplateEngine.isPolyglotAvailable()) {
                client.when(request("/javascript")).respond(template(JAVASCRIPT, "return { statusCode: 200, body: ext.greeter.Greetings.greet('javascript') };"));
                assertThat(get(mockServer, "/javascript").body(), is("hello javascript"));
            }
            for (String thread : HELPER_THREADS) {
                assertThat(thread, startsWith("MockServer-ext-helper-"));
            }
        } finally {
            mockServer.stop();
        }
    }

    @Test
    public void shouldNotHoldTheEventLoopWhileARenderWaitsOnAHelper() throws Exception {
        // one event loop thread: a render blocked on it would stall the second request until the helper returns
        MockServer mockServer = new MockServer(configuration().extensions(Greeter.class).nioEventLoopThreadCount(1).extensionHelperTimeoutMillis(10_000L));
        try (MockServerClient client = new MockServerClient("127.0.0.1", mockServer.getLocalPort())) {
            client.when(request("/slow")).respond(template(VELOCITY, "{ \"statusCode\": 200, \"body\": \"$ext.greeter.Greetings.slow(3000)\" }"));
            client.when(request("/plain")).respond(response("plain"));

            CompletableFuture<HttpResponse<String>> slow = HTTP_CLIENT.sendAsync(HttpRequest.newBuilder(uri(mockServer, "/slow")).timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(slowEntered.await(10, SECONDS), is(true));

            long started = System.nanoTime();
            HttpResponse<String> plain = get(mockServer, "/plain");
            long elapsedMillis = MILLISECONDS.convert(System.nanoTime() - started, NANOSECONDS);

            assertThat(plain.body(), is("plain"));
            assertThat("served while the helper still runs", slow.isDone(), is(false));
            assertThat(elapsedMillis, lessThan(2_000L));
            assertThat(slow.get(30, SECONDS).body(), is("slow"));
        } finally {
            mockServer.stop();
        }
    }

    @Test
    public void shouldNotCallAHelperFromATemplateOutsideTheResponseTemplate() throws Exception {
        Configuration configuration = configuration().extensions(Greeter.class);
        MockServer mockServer = new MockServer(configuration);
        try (MockServerClient client = new MockServerClient("127.0.0.1", mockServer.getLocalPort())) {
            // a delay template is rendered while matching, where ext is the stub; the delay falls back to its static value
            client.when(request("/delayed")).respond(response("delayed").withDelay(Delay.template(VELOCITY, "$ext.greeter.Greetings.greet('x')")));

            assertThat(get(mockServer, "/delayed").body(), is("delayed"));
            assertThat("no helper ran", HELPER_THREADS.isEmpty(), is(true));
            // every engine built for this server outside the primary templates binds the stub, which throws
            RuntimeException failure = assertThrows(RuntimeException.class, () -> new VelocityTemplateEngine(new MockServerLogger(configuration, ExtensionTemplateHelpersTest.class), configuration)
                .renderTemplate("$ext.greeter.Greetings.greet('x')", request("/stubbed")));
            assertThat(causes(failure), containsString("can only be called from an httpResponseTemplate or httpForwardTemplate"));
        } finally {
            mockServer.stop();
        }
    }

    @Test
    public void shouldCallHelpersFromResponseTemplatesOverHttp3WithoutHoldingTheQuicEventLoop() throws Exception {
        assumeTrue("native QUIC not available on this platform", Http3Server.isQuicAvailable());
        MockServer mockServer = startWithHttp3(configuration().extensions(Greeter.class).extensionHelperTimeoutMillis(10_000L));
        NioEventLoopGroup clientGroup = new NioEventLoopGroup(1);
        try (MockServerClient client = new MockServerClient("127.0.0.1", mockServer.getLocalPort());
             Http3TestClient http3 = Http3TestClient.open(clientGroup, mockServer)) {
            assertThat(mockServer.getExtensionRegistry().extensions().get(0).isHookEnabled(ExtensionHook.TEMPLATE_HELPER), is(true));
            client.when(request("/velocity")).respond(template(VELOCITY, "{ \"statusCode\": 200, \"body\": \"$ext.greeter.Greetings.greet('velocity')\" }"));
            client.when(request("/mustache")).respond(template(MUSTACHE, "{ \"statusCode\": 200, \"body\": \"{{#ext.greeter.greet}}mustache{{/ext.greeter.greet}}\" }"));
            client.when(request("/slow")).respond(template(VELOCITY, "{ \"statusCode\": 200, \"body\": \"$ext.greeter.Greetings.slow(2000)\" }"));
            client.when(request("/plain")).respond(response("plain"));

            assertThat(http3.send(http3Get(mockServer, "/velocity")).body(), is("hello velocity"));
            assertThat(http3.send(http3Get(mockServer, "/mustache")).body(), is("hello mustache"));
            for (String thread : HELPER_THREADS) {
                assertThat(thread, startsWith("MockServer-ext-helper-"));
            }

            // the QUIC listener has one event loop: a render waiting there would stall the plain request
            Http3TestClient.Exchange slow = http3.send(http3Get(mockServer, "/slow"));
            assertThat(slowEntered.await(10, SECONDS), is(true));
            assertThat(http3.send(http3Get(mockServer, "/plain")).body(), is("plain"));
            assertThat("served while the helper still runs", slow.dataFrames(), is(0));
            assertThat(slow.body(), is("slow"));
        } finally {
            mockServer.stop();
            clientGroup.shutdownGracefully(0, 1, SECONDS).awaitUninterruptibly(10, SECONDS);
        }
    }

    private static Http3Headers http3Get(MockServer mockServer, String path) {
        return new DefaultHttp3Headers()
            .method(HttpMethod.GET.asciiName())
            .scheme("https")
            .authority("127.0.0.1:" + mockServer.getHttp3Port())
            .path(path);
    }

    private static HttpResponse<String> get(MockServer mockServer, String path) throws Exception {
        return HTTP_CLIENT.send(HttpRequest.newBuilder(uri(mockServer, path)).timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String causes(Throwable throwable) {
        StringBuilder messages = new StringBuilder();
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            messages.append(cause.getMessage()).append(" | ");
        }
        return messages.toString();
    }

    private static URI uri(MockServer mockServer, String path) {
        return URI.create("http://127.0.0.1:" + mockServer.getLocalPort() + path);
    }
}
