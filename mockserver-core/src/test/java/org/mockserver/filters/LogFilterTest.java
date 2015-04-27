package org.mockserver.filters;

import org.junit.Test;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class LogFilterTest {

    public static final List<HttpRequest> EMPTY_REQUEST_LIST = Arrays.<HttpRequest>asList();
    public static final List<HttpResponse> EMPTY_RESPONSE_LIST = Arrays.<HttpResponse>asList();

    @Test
    public void shouldPassThroughRequestsUnchanged() {
        // given
        HttpRequest httpRequest =
                request()
                        .withBody("some_body")
                        .withCookies(
                                new Cookie("some_cookie_name", "some_cookie_value")
                        )
                        .withHeaders(
                                new Header("some_header_name", "some_header_value")
                        );

        // then
        assertEquals(httpRequest, new LogFilter().onRequest(httpRequest));
    }

    @Test
    public void shouldPassThroughResponseUnchanged() {
        // given
        HttpResponse httpResponse =
                response()
                        .withBody("some_body")
                        .withCookies(
                                new Cookie("some_cookie_name", "some_cookie_value")
                        )
                        .withHeaders(
                                new Header("some_header_name", "some_header_value")
                        )
                        .withStatusCode(304);

        // then
        assertEquals(httpResponse, new LogFilter().onResponse(request(), httpResponse));
    }

    @Test
    public void shouldRecordRequests() {
        // given
        LogFilter logFilter = new LogFilter();

        // when - called for requests
        logFilter.onRequest(request("some_path"));
        logFilter.onRequest(request("some_other_path"));
        logFilter.onRequest(request("some_path"));

        // then - request log
        assertEquals(logFilter.httpRequests(request()), Arrays.asList(request("some_path"), request("some_other_path"), request("some_path")));
        assertEquals(logFilter.httpRequests(request("some_path")), Arrays.asList(request("some_path"), request("some_path")));
        assertEquals(logFilter.httpRequests(request("some_other_path")), Arrays.asList(request("some_other_path")));
    }

    @Test
    public void shouldRecordResponses() {
        // given
        LogFilter logFilter = new LogFilter();

        // and - called for responses
        logFilter.onResponse(request("some_path"), response("some_body"));
        logFilter.onResponse(request("some_other_path"), response("some_other_body"));
        logFilter.onResponse(request("some_path"), response("some_body"));
        logFilter.onResponse(request("some_path"), null);
        logFilter.onResponse(request("some_path"), notFoundResponse());

        // then - request-response log
        assertEquals(logFilter.httpResponses(request()), Arrays.asList(response("some_body"), response("some_body"), notFoundResponse(), notFoundResponse(), response("some_other_body")));
        assertEquals(logFilter.httpResponses(request("some_path")), Arrays.asList(response("some_body"), response("some_body"), notFoundResponse(), notFoundResponse()));
        assertEquals(logFilter.httpResponses(request("some_other_path")), Arrays.asList(response("some_other_body")));
    }

    @Test
    public void shouldRetrieve() {
        // given
        LogFilter logFilter = new LogFilter();

        // when
        logFilter.onResponse(request("some_path"), response("body_one"));
        logFilter.onResponse(request("some_other_path"), response("body_two"));
        logFilter.onResponse(request("some_path"), response("body_three"));

        // then
        assertArrayEquals(logFilter.retrieve(null),
                new Expectation[]{
                        new Expectation(request("some_path"), Times.once(), TimeToLive.unlimited()).thenRespond(response("body_one")),
                        new Expectation(request("some_path"), Times.once(), TimeToLive.unlimited()).thenRespond(response("body_three")),
                        new Expectation(request("some_other_path"), Times.once(), TimeToLive.unlimited()).thenRespond(response("body_two"))
                });
        assertArrayEquals(logFilter.retrieve(request()),
                new Expectation[]{
                        new Expectation(request("some_path"), Times.once(), TimeToLive.unlimited()).thenRespond(response("body_one")),
                        new Expectation(request("some_path"), Times.once(), TimeToLive.unlimited()).thenRespond(response("body_three")),
                        new Expectation(request("some_other_path"), Times.once(), TimeToLive.unlimited()).thenRespond(response("body_two"))
                });
        assertArrayEquals(logFilter.retrieve(request("some_path")),
                new Expectation[]{
                        new Expectation(request("some_path"), Times.once(), TimeToLive.unlimited()).thenRespond(response("body_one")),
                        new Expectation(request("some_path"), Times.once(), TimeToLive.unlimited()).thenRespond(response("body_three"))
                });
        assertArrayEquals(logFilter.retrieve(request("some_other_path")),
                new Expectation[]{
                        new Expectation(request("some_other_path"), Times.once(), TimeToLive.unlimited()).thenRespond(response("body_two"))
                });
    }

    @Test
    public void shouldReset() {
        // given
        LogFilter logFilter = new LogFilter();
        // and - called for requests
        logFilter.onRequest(request("some_path"));
        logFilter.onRequest(request("some_other_path"));
        logFilter.onRequest(request("some_path"));
        // and - called for responses
        logFilter.onResponse(request("some_path"), response("some_body"));
        logFilter.onResponse(request("some_other_path"), response("some_other_body"));
        logFilter.onResponse(request("some_path"), response("some_body"));

        // when
        logFilter.reset();

        // then - request log cleared
        assertEquals(logFilter.httpRequests(request()), EMPTY_REQUEST_LIST);
        assertEquals(logFilter.httpRequests(request("some_path")), EMPTY_REQUEST_LIST);
        assertEquals(logFilter.httpRequests(request("some_other_path")), EMPTY_REQUEST_LIST);
        // then - request-response log cleared
        assertEquals(logFilter.httpResponses(request()), EMPTY_RESPONSE_LIST);
        assertEquals(logFilter.httpResponses(request("some_path")), EMPTY_RESPONSE_LIST);
        assertEquals(logFilter.httpResponses(request("some_other_path")), EMPTY_RESPONSE_LIST);
    }

    @Test
    public void shouldClearAllIfNull() {
        // given
        LogFilter logFilter = new LogFilter();
        // and - called for requests
        logFilter.onRequest(request("some_path"));
        logFilter.onRequest(request("some_other_path"));
        logFilter.onRequest(request("some_path"));
        // and - called for responses
        logFilter.onResponse(request("some_path"), response("some_body"));
        logFilter.onResponse(request("some_other_path"), response("some_other_body"));
        logFilter.onResponse(request("some_path"), response("some_body"));

        // when
        logFilter.clear(null);

        // then - request log cleared
        assertEquals(logFilter.httpRequests(request()), EMPTY_REQUEST_LIST);
        assertEquals(logFilter.httpRequests(request("some_path")), EMPTY_REQUEST_LIST);
        assertEquals(logFilter.httpRequests(request("some_other_path")), EMPTY_REQUEST_LIST);
        // then - request-response log cleared
        assertEquals(logFilter.httpResponses(request()), EMPTY_RESPONSE_LIST);
        assertEquals(logFilter.httpResponses(request("some_path")), EMPTY_RESPONSE_LIST);
        assertEquals(logFilter.httpResponses(request("some_other_path")), EMPTY_RESPONSE_LIST);
    }

    @Test
    public void shouldClearAllIfMatchesAll() {
        // given
        LogFilter logFilter = new LogFilter();
        // and - called for requests
        logFilter.onRequest(request("some_path"));
        logFilter.onRequest(request("some_other_path"));
        logFilter.onRequest(request("some_path"));
        // and - called for responses
        logFilter.onResponse(request("some_path"), response("some_body"));
        logFilter.onResponse(request("some_other_path"), response("some_other_body"));
        logFilter.onResponse(request("some_path"), response("some_body"));

        // when
        logFilter.clear(request());

        // then - request log cleared
        assertEquals(logFilter.httpRequests(request()), EMPTY_REQUEST_LIST);
        assertEquals(logFilter.httpRequests(request("some_path")), EMPTY_REQUEST_LIST);
        assertEquals(logFilter.httpRequests(request("some_other_path")), EMPTY_REQUEST_LIST);
        // then - request-response log cleared
        assertEquals(logFilter.httpResponses(request()), EMPTY_RESPONSE_LIST);
        assertEquals(logFilter.httpResponses(request("some_path")), EMPTY_RESPONSE_LIST);
        assertEquals(logFilter.httpResponses(request("some_other_path")), EMPTY_RESPONSE_LIST);
    }

    @Test
    public void shouldClearMatching() {
        // given
        LogFilter logFilter = new LogFilter();
        // and - called for requests
        logFilter.onRequest(request("some_path"));
        logFilter.onRequest(request("some_other_path"));
        logFilter.onRequest(request("some_path"));
        // and - called for responses
        logFilter.onResponse(request("some_path"), response("some_body"));
        logFilter.onResponse(request("some_other_path"), response("some_other_body"));
        logFilter.onResponse(request("some_path"), response("some_body"));

        // when
        logFilter.clear(request("some_path"));

        // then - request log cleared
        assertEquals(logFilter.httpRequests(request()), Arrays.asList(request("some_other_path")));
        assertEquals(logFilter.httpRequests(request("some_path")), EMPTY_REQUEST_LIST);
        assertEquals(logFilter.httpRequests(request("some_other_path")), Arrays.asList(request("some_other_path")));
        // then - request-response log cleared
        assertEquals(logFilter.httpResponses(request()), Arrays.asList(response("some_other_body")));
        assertEquals(logFilter.httpResponses(request("some_path")), EMPTY_RESPONSE_LIST);
        assertEquals(logFilter.httpResponses(request("some_other_path")), Arrays.asList(response("some_other_body")));
    }
}
