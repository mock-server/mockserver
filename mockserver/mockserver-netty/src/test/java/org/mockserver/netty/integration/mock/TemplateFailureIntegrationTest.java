package org.mockserver.netty.integration.mock;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpTemplate;
import org.mockserver.netty.MockServer;
import org.mockserver.scheduler.Scheduler;

import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpTemplate.template;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * A response or forward template that fails, end to end: a Velocity loop that never ends is stopped and
 * its template thread serves the next request, a failed template answers 404 by default, and 500 with an
 * x-mockserver-template-error header and the error as its body when templateFailureAsServerError is set.
 */
public class TemplateFailureIntegrationTest {

    private static final String LOOPS_FOREVER = "#foreach($i in [1..2147483647])#foreach($j in [1..2147483647])#end#end";
    private static final String DOES_NOT_PARSE = "#foreach(";
    private static final String TEMPLATE_ERROR_HEADER = "x-mockserver-template-error";

    private static MockServer defaultServer;
    private static MockServerClient defaultServerClient;
    private static MockServer serverErrorServer;
    private static MockServerClient serverErrorServerClient;
    private static NettyHttpClient httpClient;
    private static EventLoopGroup clientEventLoopGroup;

    @BeforeClass
    public static void startServersAndClient() {
        clientEventLoopGroup = new NioEventLoopGroup(3, new Scheduler.SchedulerThreadFactory(
            TemplateFailureIntegrationTest.class.getSimpleName() + "-eventLoop"));
        httpClient = new NettyHttpClient(configuration(), new MockServerLogger(), clientEventLoopGroup, null, false);

        // one template thread, so a loop that was not stopped would leave nothing to serve the next request
        defaultServer = new MockServer(serverConfiguration());
        defaultServerClient = new MockServerClient("localhost", defaultServer.getLocalPort());
        serverErrorServer = new MockServer(serverConfiguration().templateFailureAsServerError(true));
        serverErrorServerClient = new MockServerClient("localhost", serverErrorServer.getLocalPort());
    }

    private static Configuration serverConfiguration() {
        return configuration().actionHandlerThreadCount(1).velocityTemplateExecutionTimeout(250L);
    }

    @AfterClass
    public static void stopServersAndClient() {
        stopQuietly(defaultServerClient);
        stopQuietly(serverErrorServerClient);
        stopQuietly(defaultServer);
        stopQuietly(serverErrorServer);
        if (clientEventLoopGroup != null) {
            clientEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
        }
    }

    @Test
    public void shouldStopVelocityLoopAndServeNextRequestOnSameTemplateThread() throws Exception {
        // given
        defaultServerClient
            .when(request().withPath("/loops-forever"))
            .respond(template(HttpTemplate.TemplateType.VELOCITY, LOOPS_FOREVER + "{ 'statusCode': 200 }"));
        defaultServerClient
            .when(request().withPath("/after-loop"))
            .respond(template(HttpTemplate.TemplateType.VELOCITY, "{ 'statusCode': 200, 'body': 'served' }"));

        // when
        HttpResponse loopResponse = send(defaultServer, "/loops-forever");
        HttpResponse nextResponse = send(defaultServer, "/after-loop");

        // then
        assertThat(loopResponse.getStatusCode(), is(404));
        assertThat(nextResponse.getStatusCode(), is(200));
        assertThat(nextResponse.getBodyAsString(), is("served"));
    }

    @Test
    public void shouldAnswerFailedTemplateWithNotFoundByDefault() throws Exception {
        // given
        defaultServerClient
            .when(request().withPath("/does-not-parse"))
            .respond(template(HttpTemplate.TemplateType.VELOCITY, DOES_NOT_PARSE));

        // when
        HttpResponse response = send(defaultServer, "/does-not-parse");

        // then
        assertThat(response.getStatusCode(), is(404));
        assertThat(response.getFirstHeader(TEMPLATE_ERROR_HEADER), is(""));
    }

    @Test
    public void shouldAnswerFailedResponseTemplateWithServerErrorWhenConfigured() throws Exception {
        // given
        serverErrorServerClient
            .when(request().withPath("/does-not-parse"))
            .respond(template(HttpTemplate.TemplateType.VELOCITY, DOES_NOT_PARSE));

        // when
        HttpResponse response = send(serverErrorServer, "/does-not-parse");

        // then
        assertThat(response.getStatusCode(), is(500));
        assertThat(response.getFirstHeader(TEMPLATE_ERROR_HEADER), is("true"));
        assertThat(response.getBodyAsString(), startsWith("VELOCITY response template failed: "));
        assertThat(response.getBodyAsString(), not(containsString("transforming template")));
    }

    @Test
    public void shouldAnswerTimedOutTemplateWithServerErrorWhenConfigured() throws Exception {
        // given
        serverErrorServerClient
            .when(request().withPath("/loops-forever"))
            .respond(template(HttpTemplate.TemplateType.VELOCITY, LOOPS_FOREVER + "{ 'statusCode': 200 }"));

        // when
        HttpResponse response = send(serverErrorServer, "/loops-forever");

        // then
        assertThat(response.getStatusCode(), is(500));
        assertThat(response.getFirstHeader(TEMPLATE_ERROR_HEADER), is("true"));
        assertThat(response.getBodyAsString(), containsString("exceeded the configured timeout of 250ms"));
    }

    @Test
    public void shouldAnswerFailedForwardTemplateWithServerErrorWhenConfigured() throws Exception {
        // given
        serverErrorServerClient
            .when(request().withPath("/forward-does-not-parse"))
            .forward(template(HttpTemplate.TemplateType.VELOCITY, DOES_NOT_PARSE));

        // when
        HttpResponse response = send(serverErrorServer, "/forward-does-not-parse");

        // then
        assertThat(response.getStatusCode(), is(500));
        assertThat(response.getFirstHeader(TEMPLATE_ERROR_HEADER), is("true"));
        assertThat(response.getBodyAsString(), startsWith("VELOCITY forward template failed: "));
    }

    private HttpResponse send(MockServer server, String path) throws Exception {
        HttpResponse response = httpClient.sendRequest(
            request()
                .withMethod("GET")
                .withHeader(HOST.toString(), "localhost:" + server.getLocalPort())
                .withPath(path)
        ).get(30, TimeUnit.SECONDS);
        assertThat("no response for " + path, response, not(nullValue()));
        return response;
    }
}
