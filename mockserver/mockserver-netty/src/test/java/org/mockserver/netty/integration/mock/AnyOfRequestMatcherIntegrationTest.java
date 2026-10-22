package org.mockserver.netty.integration.mock;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.mock.Expectation;
import org.mockserver.model.AnyOfRequestDefinition;
import org.mockserver.netty.MockServer;
import org.mockserver.verify.VerificationTimes;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockserver.model.AnyOfRequestDefinition.requestAnyOf;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * An anyOf request matcher, registered through {@link MockServerClient} and as raw JSON, serves a request that
 * matches any one of its branches, and filters verification and retrieval the same way.
 */
public class AnyOfRequestMatcherIntegrationTest {

    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();

    private static MockServer mockServer;
    private static MockServerClient client;

    @BeforeClass
    public static void startServer() {
        mockServer = new MockServer();
        client = new MockServerClient("localhost", mockServer.getLocalPort());
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServer);
    }

    @Before
    public void reset() {
        client.reset();
    }

    @Test
    public void shouldServeEveryBranchRegisteredThroughTheClient() throws Exception {
        client.when(requestAnyOf(
            request().withMethod("GET").withPath("/pets"),
            request().withMethod("GET").withPath("/animals")
        )).respond(response().withBody("either"));

        assertThat(send("GET", "/pets").body(), is("either"));
        assertThat(send("GET", "/animals").body(), is("either"));
        assertThat(send("POST", "/pets").statusCode(), is(404));
    }

    @Test
    public void shouldServeANegatedAnyOfRegisteredAsRawJson() throws Exception {
        HttpResponse<String> created = putExpectation("{\"httpRequest\":{\"not\":true,\"anyOf\":[{\"path\":\"/health\"},{\"path\":\"/ready\"}]},\"httpResponse\":{\"body\":\"other\"}}");
        assertThat(created.body(), created.statusCode(), is(201));

        assertThat(send("GET", "/health").statusCode(), is(404));
        assertThat(send("GET", "/anything").body(), is("other"));
    }

    @Test
    public void shouldReturnAnAnyOfFromRetrieveActiveExpectations() {
        client.when(requestAnyOf(request().withPath("/a"), request().withPath("/b"))).respond(response().withBody("x"));

        Expectation[] active = client.retrieveActiveExpectations(null);

        assertThat(active.length, is(1));
        assertThat(active[0].getHttpRequest(), instanceOf(AnyOfRequestDefinition.class));
        assertThat(active[0].getHttpRequest(), is(requestAnyOf(request().withPath("/a"), request().withPath("/b"))));
    }

    @Test
    public void shouldVerifyAndRetrieveRecordedRequestsMatchingAnyBranch() throws Exception {
        send("GET", "/one");
        send("GET", "/two");
        send("GET", "/three");

        client.verify(requestAnyOf(request().withPath("/one"), request().withPath("/two")), VerificationTimes.exactly(2));
        client.verify(requestAnyOf(request().withPath("/four"), request().withPath("/five")), VerificationTimes.never());
        assertThat(client.retrieveRecordedRequests(requestAnyOf(request().withPath("/one"), request().withPath("/three"))).length, is(2));
    }

    @Test
    public void shouldRejectAnEmptyAnyOf() throws Exception {
        HttpResponse<String> response = putExpectation("{\"httpRequest\":{\"anyOf\":[]},\"httpResponse\":{\"body\":\"x\"}}");
        assertThat(response.body(), response.statusCode(), is(400));
        assertThat(response.body(), containsString("anyOf"));
    }

    private static HttpResponse<String> send(String method, String path) throws Exception {
        return HTTP.send(java.net.http.HttpRequest.newBuilder(URI.create("http://localhost:" + mockServer.getLocalPort() + path))
            .timeout(Duration.ofSeconds(10))
            .method(method, BodyPublishers.noBody())
            .build(), BodyHandlers.ofString());
    }

    private static HttpResponse<String> putExpectation(String json) throws Exception {
        return HTTP.send(java.net.http.HttpRequest.newBuilder(URI.create("http://localhost:" + mockServer.getLocalPort() + "/mockserver/expectation"))
            .timeout(Duration.ofSeconds(10))
            .PUT(BodyPublishers.ofString(json))
            .build(), BodyHandlers.ofString());
    }
}
