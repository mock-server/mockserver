package org.mockserver.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Not;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.RequestDefinitionSerializer;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * {@code PUT /mockserver/retrieve?type=ACTIVE_EXPECTATIONS} with no filter must list an expectation whose request
 * matcher is {@code not(...)}: it serves requests, so it is active.
 */
public class HttpStateRetrieveNotExpectationTest {

    private final ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private HttpState httpState;

    @Before
    public void setUp() {
        Configuration configuration = configuration();
        MockServerLogger logger = new MockServerLogger(configuration, HttpStateRetrieveNotExpectationTest.class);
        httpState = new HttpState(configuration, logger, new Scheduler(configuration, logger, true));
        httpState.add(
            new Expectation(request().withMethod("GET").withPath("/api/users")).withId("http-1").thenRespond(response()),
            new Expectation(request().withMethod("GET").withPath("/api/orders")).withId("http-2").thenRespond(response()),
            new Expectation(Not.not(request().withPath("/secret"))).withId("not-1").thenRespond(response())
        );
    }

    @After
    public void stopHttpState() {
        httpState.stop();
    }

    @Test
    public void emptyBodyListsTheNotExpectation() throws Exception {
        assertThat(activeExpectationIds(retrieveActiveExpectations()), containsInAnyOrder("http-1", "http-2", "not-1"));
    }

    @Test
    public void emptyJsonObjectBodyListsTheNotExpectation() throws Exception {
        assertThat(activeExpectationIds(retrieveActiveExpectations().withBody("{}")), containsInAnyOrder("http-1", "http-2", "not-1"));
    }

    @Test
    public void clientSerialisedEmptyRequestListsTheNotExpectation() throws Exception {
        // what MockServerClient.retrieveActiveExpectations(request()) sends
        String body = new RequestDefinitionSerializer(new MockServerLogger()).serialize(request());
        assertThat(activeExpectationIds(retrieveActiveExpectations().withBody(body)), containsInAnyOrder("http-1", "http-2", "not-1"));
    }

    @Test
    public void notExpectationStillServesRequests() {
        assertThat(httpState.getRequestMatchers().firstMatchingExpectation(request().withMethod("POST").withPath("/other")).getId(), is("not-1"));
    }

    @Test
    public void pathFilterStillNarrows() throws Exception {
        List<String> ids = activeExpectationIds(retrieveActiveExpectations().withBody("{\"path\":\"/api/users\"}"));
        assertThat(ids, hasItem("http-1"));
        assertThat(ids, not(hasItem("http-2")));
    }

    private static HttpRequest retrieveActiveExpectations() {
        return request("/mockserver/retrieve")
            .withMethod("PUT")
            .withQueryStringParameter("type", "ACTIVE_EXPECTATIONS")
            .withQueryStringParameter("format", "JSON");
    }

    private List<String> activeExpectationIds(HttpRequest retrieveRequest) throws Exception {
        HttpResponse response = httpState.retrieve(retrieveRequest);
        assertThat(response.getStatusCode(), is(200));
        List<String> ids = new ArrayList<>();
        for (JsonNode expectation : objectMapper.readTree(response.getBodyAsString())) {
            ids.add(expectation.path("id").asText());
        }
        return ids;
    }
}
