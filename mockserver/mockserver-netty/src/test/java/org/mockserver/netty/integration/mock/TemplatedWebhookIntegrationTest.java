package org.mockserver.netty.integration.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpTemplate;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.templates.engine.javascript.JavaScriptTemplateEngine;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assume.assumeTrue;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.AfterAction.afterAction;
import static org.mockserver.model.AfterAction.beforeAction;
import static org.mockserver.model.FailurePolicy.FAIL_FAST;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpTemplate.template;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.stop.Stop.stopQuietly;
import static org.mockserver.test.Retries.tryWaitForSuccess;
import static org.mockserver.verify.VerificationTimes.exactly;

/**
 * A webhook whose request is an {@code httpRequestTemplate} is sent with the values the template renders from the
 * triggering request, for each template engine.
 */
public class TemplatedWebhookIntegrationTest {

    private static final long TIMEOUT_SECONDS = 30;

    private static ClientAndServer server;
    private static EventLoopGroup clientEventLoopGroup;

    @BeforeClass
    public static void startServer() {
        server = startClientAndServer();
        clientEventLoopGroup = new NioEventLoopGroup(1, new Scheduler.SchedulerThreadFactory(TemplatedWebhookIntegrationTest.class.getSimpleName() + "-eventLoop"));
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(server);
        clientEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
    }

    @Before
    public void reset() {
        server.reset();
        server.when(request().withPath("/hook/.*")).respond(response().withStatusCode(202));
    }

    @Test
    public void shouldSendVelocityTemplatedAfterActionWebhookWithRenderedValues() throws Exception {
        assertWebhookRendered("velocity", template(HttpTemplate.TemplateType.VELOCITY, "{" +
            "  \"method\": \"POST\"," +
            "  \"path\": \"/hook/velocity/$!request.headers['orderid'][0]\"," +
            "  \"headers\": [ { \"name\": \"Host\", \"values\": [ \"" + hostAndPort() + "\" ] }," +
            "                 { \"name\": \"X-Order\", \"values\": [ \"$!request.headers['orderid'][0]\" ] } ]," +
            "  \"body\": \"received $!request.method $!request.path with $!request.body\"" +
            "}"));
    }

    @Test
    public void shouldSendMustacheTemplatedAfterActionWebhookWithRenderedValues() throws Exception {
        assertWebhookRendered("mustache", template(HttpTemplate.TemplateType.MUSTACHE, "{" +
            "  \"method\": \"POST\"," +
            "  \"path\": \"/hook/mustache/{{ request.headers.orderid.0 }}\"," +
            "  \"headers\": [ { \"name\": \"Host\", \"values\": [ \"" + hostAndPort() + "\" ] }," +
            "                 { \"name\": \"X-Order\", \"values\": [ \"{{ request.headers.orderid.0 }}\" ] } ]," +
            "  \"body\": \"received {{ request.method }} {{ request.path }} with {{ request.body }}\"" +
            "}"));
    }

    @Test
    public void shouldSendJavaScriptTemplatedAfterActionWebhookWithRenderedValues() throws Exception {
        assumeTrue("JavaScript engine not available", JavaScriptTemplateEngine.isPolyglotAvailable());
        assertWebhookRendered("javascript", template(HttpTemplate.TemplateType.JAVASCRIPT, "return {" +
            "  'method': 'POST'," +
            "  'path': '/hook/javascript/' + request.headers['orderid'][0]," +
            "  'headers': [ { 'name': 'Host', 'values': [ '" + hostAndPort() + "' ] }," +
            "               { 'name': 'X-Order', 'values': [ request.headers['orderid'][0] ] } ]," +
            "  'body': 'received ' + request.method + ' ' + request.path + ' with ' + request.body" +
            "};"));
    }

    @Test
    public void shouldSendTheDocumentedWebhookWithEscapedRequestValues() throws Exception {
        // given
        server
            .when(request().withPath("/trigger"))
            .withAfterAction(afterAction().withHttpRequestTemplate(documentedTemplate()))
            .respond(response().withBody("triggered"));

        // when
        HttpResponse response = trigger("{\"item\": \"book\"}");

        // then
        assertThat(response.getBodyAsString(), is("triggered"));
        tryWaitForSuccess(() -> server.verify(
            request()
                .withMethod("POST")
                .withPath("/hook/orders/A-1/created")
                .withBody(json("{\"received\": \"{\\\"item\\\": \\\"book\\\"}\"}")),
            exactly(1)
        ));
    }

    @Test
    public void shouldNotLetARequestBodyRedirectTheDocumentedWebhook() throws Exception {
        // given
        server
            .when(request().withPath("/trigger"))
            .withAfterAction(afterAction().withHttpRequestTemplate(documentedTemplate()))
            .respond(response().withBody("triggered"));
        String hostile = "1\" }, \"socketAddress\": { \"host\": \"attacker.example\", \"port\": 80 }, \"cookies\": { \"x\": \"y";

        // when
        HttpResponse response = trigger(hostile);

        // then the webhook still reaches its own destination, carrying the body as text
        assertThat(response.getBodyAsString(), is("triggered"));
        tryWaitForSuccess(() -> server.verify(
            request()
                .withMethod("POST")
                .withPath("/hook/orders/A-1/created")
                .withBody(json("{\"received\": " + new ObjectMapper().writeValueAsString(hostile) + "}")),
            exactly(1)
        ));
    }

    @Test
    public void shouldAnswerTheClientWhenABlockingBeforeActionTemplateHasNoTemplateType() throws Exception {
        // given
        server
            .when(request().withPath("/trigger"))
            .withBeforeAction(beforeAction()
                .withFailurePolicy(FAIL_FAST)
                .withHttpRequestTemplate(new HttpTemplate(null).withTemplate("{ \"path\": \"/hook/untyped\", \"headers\": { \"Host\": [ \"" + hostAndPort() + "\" ] } }")))
            .respond(response().withBody("triggered"));

        // when
        HttpResponse response = trigger();

        // then
        assertThat(response.getStatusCode(), is(502));
        assertThat(response.getBodyAsString(), is("before-action failed: webhook template failed: templateType is required"));
        server.verify(request().withPath("/hook/.*"), exactly(0));
    }

    /** The consumer docs' example, with its destination pointed at this server. */
    private static HttpTemplate documentedTemplate() {
        return template(HttpTemplate.TemplateType.VELOCITY, "{" +
            "  \"method\": \"POST\"," +
            "  \"path\": \"/hook/orders/$!esc.url($request.headers['orderid'][0])/created\"," +
            "  \"headers\": { \"Host\": [ \"" + hostAndPort() + "\" ], \"Content-Type\": [ \"application/json\" ] }," +
            "  \"body\": { \"received\": \"$!esc.java($request.body)\", \"at\": \"$!now_iso_8601\" }" +
            "}");
    }

    @Test
    public void shouldSendTemplatedBlockingBeforeActionWebhookBeforeResponding() throws Exception {
        // given
        server
            .when(request().withPath("/trigger"))
            .withBeforeAction(beforeAction()
                .withFailurePolicy(FAIL_FAST)
                .withHttpRequestTemplate(template(HttpTemplate.TemplateType.VELOCITY, "{" +
                    "  \"method\": \"PUT\"," +
                    "  \"path\": \"/hook/before/$!request.headers['orderid'][0]\"," +
                    "  \"headers\": [ { \"name\": \"Host\", \"values\": [ \"" + hostAndPort() + "\" ] } ]" +
                    "}")))
            .respond(response().withBody("triggered"));

        // when
        HttpResponse response = trigger();

        // then the blocking before-action has completed by the time the response arrives
        assertThat(response.getBodyAsString(), is("triggered"));
        server.verify(request().withMethod("PUT").withPath("/hook/before/A-1"), exactly(1));
    }

    @Test
    public void shouldFailFastWhenBlockingBeforeActionTemplateDoesNotRenderARequest() throws Exception {
        // given
        server
            .when(request().withPath("/trigger"))
            .withBeforeAction(beforeAction()
                .withFailurePolicy(FAIL_FAST)
                .withHttpRequestTemplate(template(HttpTemplate.TemplateType.VELOCITY, "not a request")))
            .respond(response().withBody("triggered"));

        // when
        HttpResponse response = trigger();

        // then
        assertThat(response.getStatusCode(), is(502));
        server.verify(request().withPath("/hook/.*"), exactly(0));
    }

    private void assertWebhookRendered(String engine, HttpTemplate webhook) throws Exception {
        // given
        server
            .when(request().withPath("/trigger"))
            .withAfterAction(afterAction().withHttpRequestTemplate(webhook))
            .respond(response().withBody("triggered"));

        // when
        HttpResponse response = trigger();

        // then
        assertThat(response.getBodyAsString(), is("triggered"));
        tryWaitForSuccess(() -> server.verify(
            request()
                .withMethod("POST")
                .withPath("/hook/" + engine + "/A-1")
                .withHeader("X-Order", "A-1")
                .withBody("received POST /trigger with order body"),
            exactly(1)
        ));
    }

    private static HttpResponse trigger() throws Exception {
        return trigger("order body");
    }

    private static HttpResponse trigger(String body) throws Exception {
        HttpRequest trigger = request()
            .withMethod("POST")
            .withPath("/trigger")
            .withHeader(HOST.toString(), hostAndPort())
            .withHeader("orderid", "A-1")
            .withBody(body);
        return new NettyHttpClient(configuration(), new MockServerLogger(), clientEventLoopGroup, null, false)
            .sendRequest(trigger, new InetSocketAddress("127.0.0.1", server.getLocalPort()))
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static String hostAndPort() {
        return "127.0.0.1:" + server.getLocalPort();
    }
}
