package org.mockserver.netty.integration.mock;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.netty.MockServer;
import org.mockserver.serialization.ObjectMapperFactory;
import org.slf4j.event.Level;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.mockserver.configuration.Configuration.configuration;

/**
 * End to end over HTTP: {@code PUT /mockserver/expectation} stores an expectation that can never match and says so in
 * a header, and {@code PUT /mockserver/lint} reports the stored set, including a shadowed expectation.
 */
public class LintExpectationsIntegrationTest {

    private static MockServer mockServer;
    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();

    @BeforeClass
    public static void startServer() {
        mockServer = new MockServer(configuration().logLevel(Level.INFO));
    }

    @AfterClass
    public static void stopServer() {
        if (mockServer != null) {
            mockServer.stop();
        }
    }

    @Test
    public void shouldWarnOnPutAndReportShadowingOnDemand() throws Exception {
        HttpResponse<String> created = put("/mockserver/expectation", "[" +
            "{\"id\":\"catch-all\",\"httpRequest\":{\"method\":\"GET\",\"path\":\"/lint-it/.*\"},\"httpResponse\":{\"statusCode\":200}}," +
            "{\"id\":\"specific\",\"httpRequest\":{\"method\":\"GET\",\"path\":\"/lint-it/1\"},\"httpResponse\":{\"statusCode\":201}}," +
            "{\"id\":\"no-slash\",\"httpRequest\":{\"path\":\"lint-it\"},\"httpResponse\":{\"statusCode\":202}}" +
            "]");

        assertThat(created.statusCode(), is(201));
        assertThat(created.headers().firstValue("x-mockserver-lint-warnings").orElse(""), is("1"));

        HttpResponse<String> linted = put("/mockserver/lint", "");

        assertThat(linted.statusCode(), is(200));
        JsonNode report = ObjectMapperFactory.createObjectMapper().readTree(linted.body());
        assertThat(report.path("source").asText(), is("stored"));
        assertThat(report.path("warningCount").asInt(), is(2));
        assertThat(report.toString(), containsString("\"expectationId\":\"specific\",\"position\":2"));
        assertThat(report.toString(), containsString("\"rule\":\"PATH_MISSING_LEADING_SLASH\""));
    }

    private static HttpResponse<String> put(String path, String body) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://localhost:" + mockServer.getLocalPort() + path))
            .timeout(Duration.ofSeconds(30))
            .PUT(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
