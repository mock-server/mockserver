package org.mockserver.filters;

import org.junit.Test;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class RequestResponseLogFilterTest {

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
        assertEquals(httpRequest, new RequestResponseLogFilter().onRequest(httpRequest));
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
        assertEquals(httpResponse, new RequestResponseLogFilter().onResponse(request(), httpResponse));
    }

    @Test
    public void shouldRecordResponses() {
        // given
        RequestResponseLogFilter requestResponseLogFilter = new RequestResponseLogFilter();

        // and - called for responses
        requestResponseLogFilter.onResponse(request("some_path"), response("some_body"));
        requestResponseLogFilter.onResponse(request("some_other_path"), response("some_other_body"));
        requestResponseLogFilter.onResponse(request("some_path"), response("some_body"));
        requestResponseLogFilter.onResponse(request("some_path"), null);
        requestResponseLogFilter.onResponse(request("some_path"), notFoundResponse());

        // then - request-response log
        assertEquals(requestResponseLogFilter.httpResponses(request()), Arrays.asList(response("some_body"), response("some_body"), notFoundResponse(), notFoundResponse(), response("some_other_body")));
        assertEquals(requestResponseLogFilter.httpResponses(request("some_path")), Arrays.asList(response("some_body"), response("some_body"), notFoundResponse(), notFoundResponse()));
        assertEquals(requestResponseLogFilter.httpResponses(request("some_other_path")), Arrays.asList(response("some_other_body")));
    }

    @Test
    public void shouldReturnExpectations(){
        // given
        RequestResponseLogFilter requestResponseLogFilter = new RequestResponseLogFilter();

        //and - called for responses
        requestResponseLogFilter.onResponse(request("some_path"), response("some_body"));
        requestResponseLogFilter.onResponse(request("some_other_path"), response("some_other_body"));
        requestResponseLogFilter.onResponse(request("some_path"), response("some_body"));
        requestResponseLogFilter.onResponse(request("some_path"), null);
        requestResponseLogFilter.onResponse(request("some_path"), notFoundResponse());
        //and - non matching request
        HttpRequest should_not_match = request("should_not_match");
        should_not_match.setNot(true);
        requestResponseLogFilter.onResponse(should_not_match, response("should_not_match_body"));

        //when
        Expectation[] expectations = requestResponseLogFilter.getExpectations(request());

        System.out.println("new ExpectationSerializer().serialize(expectations) = " + new ExpectationSerializer().serialize(expectations));
        //then
        assertEquals("should match only 5", 5, expectations.length);
        List<HttpResponse> somePath = new ArrayList<HttpResponse>();
        List<HttpResponse> someOtherPath = new ArrayList<HttpResponse>();
        for (Expectation expectation : expectations) {
            HttpRequest httpRequest = expectation.getHttpRequest();
            HttpResponse httpResponse = expectation.getHttpResponse();
            String httpRequestPath =  httpRequest.getPath().getValue();
            if("some_path".equals(httpRequestPath)){
                somePath.add(httpResponse);
            }else if("some_other_path".equals(httpRequestPath)){
                someOtherPath.add(httpResponse);
            }else {
                fail("http request path should only be some_path or some_other_path");
            }
        }
        assertEquals(somePath, Arrays.asList(response("some_body"), response("some_body"), notFoundResponse(), notFoundResponse()));
        assertEquals(someOtherPath,Arrays.asList(response("some_other_body")));
    }

    @Test
    public void shouldReset() {
        // given
        RequestResponseLogFilter requestResponseLogFilter = new RequestResponseLogFilter();
        // and - called for requests
        requestResponseLogFilter.onRequest(request("some_path"));
        requestResponseLogFilter.onRequest(request("some_other_path"));
        requestResponseLogFilter.onRequest(request("some_path"));
        // and - called for responses
        requestResponseLogFilter.onResponse(request("some_path"), response("some_body"));
        requestResponseLogFilter.onResponse(request("some_other_path"), response("some_other_body"));
        requestResponseLogFilter.onResponse(request("some_path"), response("some_body"));

        // when
        requestResponseLogFilter.reset();

        // then - request-response log cleared
        assertEquals(requestResponseLogFilter.httpResponses(request()), EMPTY_RESPONSE_LIST);
        assertEquals(requestResponseLogFilter.httpResponses(request("some_path")), EMPTY_RESPONSE_LIST);
        assertEquals(requestResponseLogFilter.httpResponses(request("some_other_path")), EMPTY_RESPONSE_LIST);
    }

    @Test
    public void shouldClearAllIfNull() {
        // given
        RequestResponseLogFilter requestResponseLogFilter = new RequestResponseLogFilter();
        // and - called for requests
        requestResponseLogFilter.onRequest(request("some_path"));
        requestResponseLogFilter.onRequest(request("some_other_path"));
        requestResponseLogFilter.onRequest(request("some_path"));
        // and - called for responses
        requestResponseLogFilter.onResponse(request("some_path"), response("some_body"));
        requestResponseLogFilter.onResponse(request("some_other_path"), response("some_other_body"));
        requestResponseLogFilter.onResponse(request("some_path"), response("some_body"));

        // when
        requestResponseLogFilter.clear(null);

        // then - request-response log cleared
        assertEquals(requestResponseLogFilter.httpResponses(request()), EMPTY_RESPONSE_LIST);
        assertEquals(requestResponseLogFilter.httpResponses(request("some_path")), EMPTY_RESPONSE_LIST);
        assertEquals(requestResponseLogFilter.httpResponses(request("some_other_path")), EMPTY_RESPONSE_LIST);
    }

    @Test
    public void shouldClearAllIfMatchesAll() {
        // given
        RequestResponseLogFilter requestResponseLogFilter = new RequestResponseLogFilter();
        // and - called for requests
        requestResponseLogFilter.onRequest(request("some_path"));
        requestResponseLogFilter.onRequest(request("some_other_path"));
        requestResponseLogFilter.onRequest(request("some_path"));
        // and - called for responses
        requestResponseLogFilter.onResponse(request("some_path"), response("some_body"));
        requestResponseLogFilter.onResponse(request("some_other_path"), response("some_other_body"));
        requestResponseLogFilter.onResponse(request("some_path"), response("some_body"));

        // when
        requestResponseLogFilter.clear(request());

        // then - request-response log cleared
        assertEquals(requestResponseLogFilter.httpResponses(request()), EMPTY_RESPONSE_LIST);
        assertEquals(requestResponseLogFilter.httpResponses(request("some_path")), EMPTY_RESPONSE_LIST);
        assertEquals(requestResponseLogFilter.httpResponses(request("some_other_path")), EMPTY_RESPONSE_LIST);
    }

    @Test
    public void shouldClearMatching() {
        // given
        RequestResponseLogFilter requestResponseLogFilter = new RequestResponseLogFilter();
        // and - called for requests
        requestResponseLogFilter.onRequest(request("some_path"));
        requestResponseLogFilter.onRequest(request("some_other_path"));
        requestResponseLogFilter.onRequest(request("some_path"));
        // and - called for responses
        requestResponseLogFilter.onResponse(request("some_path"), response("some_body"));
        requestResponseLogFilter.onResponse(request("some_other_path"), response("some_other_body"));
        requestResponseLogFilter.onResponse(request("some_path"), response("some_body"));

        // when
        requestResponseLogFilter.clear(request("some_path"));

        // then - request-response log cleared
        assertEquals(requestResponseLogFilter.httpResponses(request()), Arrays.asList(response("some_other_body")));
        assertEquals(requestResponseLogFilter.httpResponses(request("some_path")), EMPTY_RESPONSE_LIST);
        assertEquals(requestResponseLogFilter.httpResponses(request("some_other_path")), Arrays.asList(response("some_other_body")));
    }
}
