package org.mockserver.persistence;

import org.junit.Test;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.ResponseMode;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class RecordedExpectationRepeatsAndHeadersTest {

    private static Expectation recorded(HttpRequest request, String body) {
        return recorded(request, response(body));
    }

    private static Expectation recorded(HttpRequest request, HttpResponse response) {
        return new Expectation(request, Times.once(), TimeToLive.unlimited(), 0).thenRespond(response);
    }

    private static List<String> bodies(List<HttpResponse> responses) {
        return responses.stream().map(HttpResponse::getBodyAsString).collect(Collectors.toList());
    }

    private static List<String> headerNames(HttpRequest request) {
        return request.getHeaders().getEntries().stream().map(header -> header.getName().getValue()).collect(Collectors.toList());
    }

    @Test
    public void shouldReplayDifferentResponsesToTheSameRequestInRecordedOrder() {
        List<Expectation> recorded = Arrays.asList(
            recorded(request("/status").withHeader("Host", "api").withRemoteAddress("10.0.0.1:1111").withStreamId(1), "pending"),
            recorded(request("/other"), "other"),
            recorded(request("/status").withHeader("Host", "api").withRemoteAddress("10.0.0.1:2222").withStreamId(3), "pending"),
            recorded(request("/status").withHeader("Host", "api").withRemoteAddress("10.0.0.1:3333"), "done")
        );

        List<Expectation> result = RecordedExpectationPostProcessor.sequenceRepeats(recorded);

        assertThat(result.size(), is(2));
        Expectation sequence = result.get(0);
        assertThat(sequence.getHttpRequest(), sameInstance(recorded.get(0).getHttpRequest()));
        assertThat(sequence.getResponseMode(), is(ResponseMode.SWITCH));
        assertThat(bodies(sequence.getHttpResponses()), contains("pending", "pending", "done"));
        assertThat(sequence.getHttpResponse(), nullValue());
        assertThat(sequence.getTimes().isUnlimited(), is(true));
        assertThat(result.get(1), sameInstance(recorded.get(1)));
    }

    @Test
    public void shouldKeepRequestsThatDifferInAHeaderAsSeparateExpectations() {
        List<Expectation> recorded = Arrays.asList(
            recorded(request("/health").withHeader("Host", "service-a"), "A"),
            recorded(request("/health").withHeader("Host", "service-b"), "B"),
            recorded(request("/doc").withHeader("Accept", "application/json"), "{}"),
            recorded(request("/doc").withHeader("Accept", "application/xml"), "<doc/>"),
            recorded(request("/me").withHeader("Authorization", "Bearer alice"), "alice"),
            recorded(request("/me").withHeader("Authorization", "Bearer bob"), "bob"),
            recorded(request("/trace").withHeader("traceparent", "00-aaa-01"), "first"),
            recorded(request("/trace").withHeader("traceparent", "00-bbb-01"), "second")
        );

        List<Expectation> result = RecordedExpectationPostProcessor.sequenceRepeats(recorded);

        assertThat(result.size(), is(recorded.size()));
        for (int i = 0; i < recorded.size(); i++) {
            assertThat(result.get(i), sameInstance(recorded.get(i)));
        }
    }

    @Test
    public void shouldKeepRequestsThatDifferInAMatchedConnectionPropertyApart() {
        List<Expectation> recorded = Arrays.asList(
            recorded(request("/status").withKeepAlive(true), "pending"),
            recorded(request("/status").withKeepAlive(false), "done"),
            recorded(request("/status").withSecure(true), "secure")
        );

        List<Expectation> result = RecordedExpectationPostProcessor.sequenceRepeats(recorded);

        assertThat(result, contains(sameInstance(recorded.get(0)), sameInstance(recorded.get(1)), sameInstance(recorded.get(2))));
    }

    @Test
    public void shouldGroupOnTheCapturedHeadersOnly() {
        List<Expectation> recorded = Arrays.asList(
            recorded(request("/job").withHeader("traceparent", "00-aaa-01").withHeader("X-Tenant", "t1"), "first"),
            recorded(request("/job").withHeader("traceparent", "00-bbb-01").withHeader("X-Tenant", "t1"), "second"),
            recorded(request("/job").withHeader("traceparent", "00-ccc-01").withHeader("X-Tenant", "t2"), "other tenant")
        );

        List<Expectation> result = RecordedExpectationPostProcessor.sequenceRepeats(
            RecordedExpectationPostProcessor.captureHeaders(recorded, Collections.singletonList("x-tenant")));

        assertThat(result.size(), is(2));
        assertThat(headerNames((HttpRequest) result.get(0).getHttpRequest()), contains("X-Tenant"));
        assertThat(bodies(result.get(0).getHttpResponses()), contains("first", "second"));
        assertThat(result.get(1).getHttpResponse().getBodyAsString(), is("other tenant"));
    }

    @Test
    public void shouldLeaveIdenticalRepeatsAndSingleRequestsUnchanged() {
        List<Expectation> recorded = Arrays.asList(
            recorded(request("/same"), "a"),
            recorded(request("/same"), "a"),
            recorded(request("/once"), "b")
        );

        List<Expectation> result = RecordedExpectationPostProcessor.sequenceRepeats(recorded);

        assertThat(result, contains(sameInstance(recorded.get(0)), sameInstance(recorded.get(1)), sameInstance(recorded.get(2))));
    }

    @Test
    public void shouldTreatResponsesThatDifferOnlyInVolatileHeadersAsTheSame() {
        List<Expectation> recorded = Arrays.asList(
            recorded(request("/same"), response("a").withHeader("Date", "Wed, 21 Oct 2026 10:00:00 GMT").withHeader("x-request-id", "1")),
            recorded(request("/same"), response("a").withHeader("Date", "Wed, 21 Oct 2026 10:00:01 GMT").withHeader("x-request-id", "2")),
            recorded(request("/changed"), response("a").withHeader("Date", "Wed, 21 Oct 2026 10:00:00 GMT").withHeader("ETag", "1")),
            recorded(request("/changed"), response("a").withHeader("Date", "Wed, 21 Oct 2026 10:00:01 GMT").withHeader("ETag", "2"))
        );

        List<Expectation> result = RecordedExpectationPostProcessor.sequenceRepeats(recorded);

        assertThat(result.size(), is(3));
        assertThat(result.get(0), sameInstance(recorded.get(0)));
        assertThat(result.get(1), sameInstance(recorded.get(1)));
        assertThat(result.get(2).getHttpResponses().size(), is(2));
    }

    @Test
    public void shouldTreatResponsesThatDifferOnlyInASessionCookieAsTheSame() {
        List<Expectation> recorded = Arrays.asList(
            recorded(request("/same"), response("a").withHeader("Set-Cookie", "session=one").withCookie("session", "one")),
            recorded(request("/same"), response("a").withHeader("Set-Cookie", "session=two").withCookie("session", "two"))
        );

        List<Expectation> result = RecordedExpectationPostProcessor.sequenceRepeats(recorded);

        assertThat(result, contains(sameInstance(recorded.get(0)), sameInstance(recorded.get(1))));
    }

    @Test(timeout = 30_000)
    public void shouldGroupALargePollingRecordingInLinearTime() {
        int polls = 20_000;
        List<Expectation> recorded = new java.util.ArrayList<>(polls * 2);
        for (int i = 0; i < polls; i++) {
            recorded.add(recorded(request("/job").withHeader("Host", "api").withRemoteAddress("10.0.0.1:" + i), i < polls - 1 ? "pending" : "done"));
            recorded.add(recorded(request("/health").withHeader("Host", "api"), response("ok").withHeader("Date", "t" + i)));
        }

        long start = System.nanoTime();
        List<Expectation> result = RecordedExpectationPostProcessor.sequenceRepeats(recorded);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertThat(result.size(), is(polls + 1));
        assertThat(result.get(0).getHttpResponses().size(), is(polls));
        assertThat("grouping " + recorded.size() + " recordings took " + elapsedMillis + "ms", elapsedMillis < 10_000, is(true));
    }

    @Test
    public void shouldNotGroupRequestsThatDifferInQueryOrBody() {
        List<Expectation> recorded = Arrays.asList(
            recorded(request("/search").withQueryStringParameter("q", "a"), "first"),
            recorded(request("/search").withQueryStringParameter("q", "b"), "second"),
            recorded(request("/submit").withMethod("POST").withBody("x"), "third"),
            recorded(request("/submit").withMethod("POST").withBody("y"), "fourth")
        );

        List<Expectation> result = RecordedExpectationPostProcessor.sequenceRepeats(recorded);

        assertThat(result.size(), is(4));
    }

    @Test
    public void shouldKeepOnlyTheCapturedHeadersCaseInsensitively() {
        Expectation original = recorded(request("/x")
            .withHeader("Accept", "application/json")
            .withHeader("X-Tenant", "t1")
            .withHeader("User-Agent", "curl"), "ok").withId("recorded-id");

        List<Expectation> result = RecordedExpectationPostProcessor.captureHeaders(Collections.singletonList(original), Arrays.asList("x-tenant", "ACCEPT"));

        HttpRequest matcher = (HttpRequest) result.get(0).getHttpRequest();
        assertThat(headerNames(matcher), containsInAnyOrder("Accept", "X-Tenant"));
        assertThat(result.get(0).getId(), is("recorded-id"));
        assertThat(result.get(0).getHttpResponse().getBodyAsString(), is("ok"));
        assertThat(((HttpRequest) original.getHttpRequest()).getHeaders().getEntries().size(), is(3));
    }

    @Test
    public void shouldRemoveEveryHeaderWhenNoHeadersAreCaptured() {
        Expectation original = recorded(request("/x").withHeader("Accept", "application/json"), "ok");

        List<Expectation> result = RecordedExpectationPostProcessor.captureHeaders(Collections.singletonList(original), Collections.emptyList());

        assertThat(((HttpRequest) result.get(0).getHttpRequest()).getHeaders(), nullValue());
    }

    @Test
    public void shouldReturnEmptyForNullOrEmptyInput() {
        assertThat(RecordedExpectationPostProcessor.sequenceRepeats(null), is(empty()));
        assertThat(RecordedExpectationPostProcessor.captureHeaders(Collections.emptyList(), Collections.singletonList("a")), is(empty()));
    }
}
