package org.mockserver.netty.integration.mock;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockserver.client.MockServerClient;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.FileBody;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpTemplate;
import org.mockserver.model.MediaType;
import org.mockserver.netty.MockServer;
import org.mockserver.scheduler.Scheduler;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * Drives a real server to prove a {@code templatedFilePath} FILE body picks the response file from the
 * request, and that request-controlled path values cannot read a file outside the path's base directory.
 */
public class TemplatedFilePathIntegrationTest {

    private static final String SECRET = "top-secret-contents";

    @ClassRule
    public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static MockServer mockServer;
    private static MockServerClient mockServerClient;
    private static NettyHttpClient httpClient;
    private static EventLoopGroup clientEventLoopGroup;

    @BeforeClass
    public static void startServerAndClient() throws Exception {
        File mocks = temporaryFolder.newFolder("mocks");
        Files.write(new File(mocks, "alice.json").toPath(), "{\"user\":\"alice\"}".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(temporaryFolder.getRoot(), "secret.json").toPath(), SECRET.getBytes(StandardCharsets.UTF_8));
        String base = mocks.getAbsolutePath() + "/";

        clientEventLoopGroup = new NioEventLoopGroup(2, new Scheduler.SchedulerThreadFactory(
            TemplatedFilePathIntegrationTest.class.getSimpleName() + "-eventLoop"));
        httpClient = new NettyHttpClient(configuration(), new MockServerLogger(), clientEventLoopGroup, null, false);
        mockServer = new MockServer();
        mockServerClient = new MockServerClient("localhost", mockServer.getLocalPort());

        mockServerClient
            .when(request().withPath("/files/{name}").withPathParameter("name", ".*"))
            .respond(response().withBody(templatedFile(base + "{{ request.pathParameters.name.0 }}.json")));
        mockServerClient
            .when(request().withPath("/query"))
            .respond(response().withBody(templatedFile(base + "{{ request.queryStringParameters.name.0 }}.json")));
        mockServerClient
            .when(request().withPath("/literal-missing"))
            .respond(response().withBody(new FileBody(base + "missing.json", MediaType.APPLICATION_JSON)));
    }

    @AfterClass
    public static void stopServerAndClient() {
        stopQuietly(mockServerClient);
        stopQuietly(mockServer);
        if (clientEventLoopGroup != null) {
            clientEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
        }
    }

    @Test
    public void shouldServeFileSelectedByPathParameter() throws Exception {
        HttpResponse response = send(request().withPath("/files/alice"));

        assertThat(response.getStatusCode(), is(200));
        assertThat(response.getBodyAsString(), is("{\"user\":\"alice\"}"));
    }

    @Test
    public void shouldServeFileSelectedByQueryParameter() throws Exception {
        HttpResponse response = send(request().withPath("/query").withQueryStringParameter("name", "alice"));

        assertThat(response.getStatusCode(), is(200));
        assertThat(response.getBodyAsString(), is("{\"user\":\"alice\"}"));
    }

    @Test
    public void shouldAnswerMissingRenderedFileLikeMissingLiteralFile() throws Exception {
        HttpResponse literal = send(request().withPath("/literal-missing"));
        HttpResponse rendered = send(request().withPath("/files/carol"));

        assertThat(literal.getStatusCode(), is(500));
        assertThat(rendered.getStatusCode(), is(literal.getStatusCode()));
        assertThat(rendered.getBodyAsString(), is(literal.getBodyAsString()));
    }

    @Test
    public void shouldNotServeFilesOutsideBaseDirectoryThroughQueryParameter() throws Exception {
        for (String attack : new String[]{"../secret", "..\\secret", "%2e%2e%2fsecret", "..%2Fsecret", temporaryFolder.getRoot().getAbsolutePath() + "/secret", "alice.json\u0000"}) {
            HttpResponse response = send(request().withPath("/query").withQueryStringParameter("name", attack));

            assertThat("status for " + attack, response.getStatusCode(), is(500));
            assertThat(response.getBodyAsString(), is("Error reading file for response body"));
            assertThat(response.getBodyAsString(), not(containsString(SECRET)));
        }
    }

    @Test
    public void shouldNotServeFilesOutsideBaseDirectoryThroughEncodedPathSegment() throws Exception {
        for (String attack : new String[]{"/files/..%2Fsecret", "/files/%2e%2e%2fsecret", "/files/..%5Csecret", "/files/%252e%252e%252fsecret"}) {
            HttpResponse response = send(request().withPath(attack));

            assertThat("status for " + attack, response.getStatusCode(), is(500));
            assertThat(response.getBodyAsString(), is("Error reading file for response body"));
        }
    }

    private static FileBody templatedFile(String filePath) {
        return new FileBody(filePath, MediaType.APPLICATION_JSON, HttpTemplate.TemplateType.MUSTACHE).withTemplatedFilePath(true);
    }

    private HttpResponse send(HttpRequest request) throws Exception {
        return httpClient.sendRequest(
            request
                .withMethod("GET")
                .withHeader(HOST.toString(), "localhost:" + mockServer.getLocalPort())
        ).get(20, TimeUnit.SECONDS);
    }
}
