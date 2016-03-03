package org.mockserver.filters;

import org.junit.Test;
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
public class RequestLogFilterTest {

    public static final List<HttpRequest> EMPTY_REQUEST_LIST = Arrays.<HttpRequest>asList();

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
        assertEquals(httpRequest, new RequestLogFilter().onRequest(httpRequest));
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
        assertEquals(httpResponse, new RequestLogFilter().onResponse(request(), httpResponse));
    }

    @Test
    public void shouldRecordRequests() {
        // given
        RequestLogFilter requestLogFilter = new RequestLogFilter();

        // when - called for requests
        requestLogFilter.onRequest(request("some_path"));
        requestLogFilter.onRequest(request("some_other_path"));
        requestLogFilter.onRequest(request("some_path"));

        // then - request log
        assertEquals(requestLogFilter.httpRequests(request()), Arrays.asList(request("some_path"), request("some_other_path"), request("some_path")));
        assertEquals(requestLogFilter.httpRequests(request("some_path")), Arrays.asList(request("some_path"), request("some_path")));
        assertEquals(requestLogFilter.httpRequests(request("some_other_path")), Arrays.asList(request("some_other_path")));
    }

    @Test
    public void shouldRetrieve() {
        // given
        RequestLogFilter requestLogFilter = new RequestLogFilter();

        // when
        requestLogFilter.onRequest(request("some_path"));
        requestLogFilter.onRequest(request("some_other_path"));
        requestLogFilter.onRequest(request("some_path"));

        // then
        assertArrayEquals(requestLogFilter.retrieve(null),
                new HttpRequest[]{
                        request("some_path"),
                        request("some_other_path"),
                        request("some_path")
                });
        assertArrayEquals(requestLogFilter.retrieve(request()),
                new HttpRequest[]{
                        request("some_path"),
                        request("some_other_path"),
                        request("some_path")
                });
        assertArrayEquals(requestLogFilter.retrieve(request("some_path")),
                new HttpRequest[]{
                        request("some_path"),
                        request("some_path")
                });
        assertArrayEquals(requestLogFilter.retrieve(request("some_other_path")),
                new HttpRequest[]{
                        request("some_other_path")
                });
    }

    @Test
    public void shouldReset() {
        // given
        RequestLogFilter requestLogFilter = new RequestLogFilter();
        // and - called for requests
        requestLogFilter.onRequest(request("some_path"));
        requestLogFilter.onRequest(request("some_other_path"));
        requestLogFilter.onRequest(request("some_path"));
        // and - called for responses
        requestLogFilter.onResponse(request("some_path"), response("some_body"));
        requestLogFilter.onResponse(request("some_other_path"), response("some_other_body"));
        requestLogFilter.onResponse(request("some_path"), response("some_body"));

        // when
        requestLogFilter.reset();

        // then - request log cleared
        assertEquals(requestLogFilter.httpRequests(request()), EMPTY_REQUEST_LIST);
        assertEquals(requestLogFilter.httpRequests(request("some_path")), EMPTY_REQUEST_LIST);
        assertEquals(requestLogFilter.httpRequests(request("some_other_path")), EMPTY_REQUEST_LIST);
    }

    @Test
    public void shouldClearAllIfNull() {
        // given
        RequestLogFilter requestLogFilter = new RequestLogFilter();
        // and - called for requests
        requestLogFilter.onRequest(request("some_path"));
        requestLogFilter.onRequest(request("some_other_path"));
        requestLogFilter.onRequest(request("some_path"));
        // and - called for responses
        requestLogFilter.onResponse(request("some_path"), response("some_body"));
        requestLogFilter.onResponse(request("some_other_path"), response("some_other_body"));
        requestLogFilter.onResponse(request("some_path"), response("some_body"));

        // when
        requestLogFilter.clear(null);

        // then - request log cleared
        assertEquals(requestLogFilter.httpRequests(request()), EMPTY_REQUEST_LIST);
        assertEquals(requestLogFilter.httpRequests(request("some_path")), EMPTY_REQUEST_LIST);
        assertEquals(requestLogFilter.httpRequests(request("some_other_path")), EMPTY_REQUEST_LIST);
    }

    @Test
    public void shouldClearAllIfMatchesAll() {
        // given
        RequestLogFilter requestLogFilter = new RequestLogFilter();
        // and - called for requests
        requestLogFilter.onRequest(request("some_path"));
        requestLogFilter.onRequest(request("some_other_path"));
        requestLogFilter.onRequest(request("some_path"));
        // and - called for responses
        requestLogFilter.onResponse(request("some_path"), response("some_body"));
        requestLogFilter.onResponse(request("some_other_path"), response("some_other_body"));
        requestLogFilter.onResponse(request("some_path"), response("some_body"));

        // when
        requestLogFilter.clear(request());

        // then - request log cleared
        assertEquals(requestLogFilter.httpRequests(request()), EMPTY_REQUEST_LIST);
        assertEquals(requestLogFilter.httpRequests(request("some_path")), EMPTY_REQUEST_LIST);
        assertEquals(requestLogFilter.httpRequests(request("some_other_path")), EMPTY_REQUEST_LIST);
    }

    @Test
    public void shouldClearMatching() {
        // given
        RequestLogFilter requestLogFilter = new RequestLogFilter();
        // and - called for requests
        requestLogFilter.onRequest(request("some_path"));
        requestLogFilter.onRequest(request("some_other_path"));
        requestLogFilter.onRequest(request("some_path"));
        // and - called for responses
        requestLogFilter.onResponse(request("some_path"), response("some_body"));
        requestLogFilter.onResponse(request("some_other_path"), response("some_other_body"));
        requestLogFilter.onResponse(request("some_path"), response("some_body"));

        // when
        requestLogFilter.clear(request("some_path"));

        // then - request log cleared
        assertEquals(requestLogFilter.httpRequests(request()), Arrays.asList(request("some_other_path")));
        assertEquals(requestLogFilter.httpRequests(request("some_path")), EMPTY_REQUEST_LIST);
        assertEquals(requestLogFilter.httpRequests(request("some_other_path")), Arrays.asList(request("some_other_path")));
    }
}
