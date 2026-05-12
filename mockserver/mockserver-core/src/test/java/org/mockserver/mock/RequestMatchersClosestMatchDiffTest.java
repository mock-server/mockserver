package org.mockserver.mock;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.MatchDifference;
import org.mockserver.model.HttpRequest;
import org.mockserver.scheduler.Scheduler;

import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause.API;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class RequestMatchersClosestMatchDiffTest {

    private RequestMatchers requestMatchers;

    @Before
    public void prepareTestFixture() {
        Scheduler scheduler = mock(Scheduler.class);
        WebSocketClientRegistry webSocketClientRegistry = mock(WebSocketClientRegistry.class);
        requestMatchers = new RequestMatchers(configuration().detailedMatchFailures(true), new MockServerLogger(), scheduler, webSocketClientRegistry);
    }

    @Test
    public void shouldReturnNullWhenNoExpectations() {
        Map<MatchDifference.Field, List<String>> result = requestMatchers.findClosestMatchDiff(
            new HttpRequest().withMethod("GET").withPath("somePath")
        );
        assertThat(result, is(nullValue()));
    }

    @Test
    public void shouldReturnDiffForSingleExpectation() {
        // given
        requestMatchers.add(
            new Expectation(request().withMethod("GET").withPath("expectedPath"))
                .thenRespond(response().withBody("someBody")),
            API
        );

        // when
        Map<MatchDifference.Field, List<String>> result = requestMatchers.findClosestMatchDiff(
            new HttpRequest().withMethod("POST").withPath("differentPath")
        );

        // then - with fail-fast enabled (default), only the first failing field (method) is captured
        assertThat(result, is(notNullValue()));
        assertThat(result.isEmpty(), is(false));
        assertThat(result.containsKey(MatchDifference.Field.METHOD), is(true));
    }

    @Test
    public void shouldReturnClosestMatchWhenMultipleExpectations() {
        // given - one expectation matches method only, another matches nothing
        requestMatchers.add(
            new Expectation(request().withMethod("GET").withPath("expectedPath"))
                .thenRespond(response().withBody("someBody")),
            API
        );
        requestMatchers.add(
            new Expectation(request().withMethod("POST").withPath("otherPath"))
                .thenRespond(response().withBody("otherBody")),
            API
        );

        // when - request matches GET method of first expectation
        Map<MatchDifference.Field, List<String>> result = requestMatchers.findClosestMatchDiff(
            new HttpRequest().withMethod("GET").withPath("differentPath")
        );

        // then - closest match should have only path difference (first expectation)
        assertThat(result, is(notNullValue()));
        assertThat(result.containsKey(MatchDifference.Field.PATH), is(true));
        assertThat(result.containsKey(MatchDifference.Field.METHOD), is(false));
    }

    @Test
    public void shouldReturnNullWhenRequestMatchesExpectation() {
        // given
        requestMatchers.add(
            new Expectation(request().withMethod("GET").withPath("somePath"))
                .thenRespond(response().withBody("someBody")),
            API
        );

        // when - request matches the expectation perfectly
        Map<MatchDifference.Field, List<String>> result = requestMatchers.findClosestMatchDiff(
            new HttpRequest().withMethod("GET").withPath("somePath")
        );

        // then - no differences, result should be null (match found, not a diff scenario)
        assertThat(result, is(nullValue()));
    }
}
