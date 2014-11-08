package org.mockserver.proxy.filters;

import org.junit.Test;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;
import org.mockserver.verify.Verification;

import java.util.Arrays;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.mockserver.verify.VerificationTimes.atLeast;
import static org.mockserver.verify.VerificationTimes.exactly;

/**
 * @author jamesdbloom
 */
public class LogFilterTest {

    @Test
    public void shouldPassThroughResponseUnchanged() {
        // given
        HttpResponse httpResponse =
                new HttpResponse()
                        .withBody("some_body")
                        .withCookies(
                                new Cookie("some_cookie_name", "some_cookie_value")
                        )
                        .withHeaders(
                                new Header("some_header_name", "some_header_value")
                        )
                        .withStatusCode(304);

        // then
        assertEquals(httpResponse, new LogFilter().onResponse(new HttpRequest(), httpResponse));
    }

    @Test
    public void shouldRecordRequests() {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");
        HttpRequest anotherHttpRequest = new HttpRequest().withPath("some_path");
        LogFilter logFilter = new LogFilter();

        // when
        logFilter.onResponse(httpRequest, new HttpResponse());
        logFilter.onResponse(otherHttpRequest, new HttpResponse());
        logFilter.onResponse(anotherHttpRequest, new HttpResponse());

        // then
        assertEquals(logFilter.httpRequests(new HttpRequest()), Arrays.asList(httpRequest, otherHttpRequest));
        assertEquals(logFilter.httpRequests(new HttpRequest().withPath("some_path")), Arrays.asList(httpRequest));
        assertEquals(logFilter.httpRequests(new HttpRequest().withPath("some_other_path")), Arrays.asList(otherHttpRequest));
    }

    @Test
    public void shouldRecordResponses() {
        // given
        HttpResponse httpResponse = new HttpResponse().withBody("some_body");
        HttpResponse otherHttpResponse = new HttpResponse().withBody("some_other_body");
        HttpResponse anotherHttpResponse = new HttpResponse().withBody("some_body");
        LogFilter logFilter = new LogFilter();

        // when
        logFilter.onResponse(new HttpRequest().withPath("some_path"), httpResponse);
        logFilter.onResponse(new HttpRequest().withPath("some_other_path"), otherHttpResponse);
        logFilter.onResponse(new HttpRequest().withPath("some_path"), anotherHttpResponse);

        // then
        assertEquals(logFilter.httpResponses(new HttpRequest()), Arrays.asList(httpResponse, anotherHttpResponse, otherHttpResponse));
        assertEquals(logFilter.httpResponses(new HttpRequest().withPath("some_path")), Arrays.asList(httpResponse, anotherHttpResponse));
        assertEquals(logFilter.httpResponses(new HttpRequest().withPath("some_other_path")), Arrays.asList(otherHttpResponse));
    }

    @Test
    public void shouldRetrieve() {
        // given
        HttpResponse httpResponseOne = new HttpResponse().withBody("body_one");
        HttpResponse httpResponseTwo = new HttpResponse().withBody("body_two");
        HttpResponse httpResponseThree = new HttpResponse().withBody("body_three");
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");
        LogFilter logFilter = new LogFilter();

        // when
        logFilter.onResponse(httpRequest, httpResponseOne);
        logFilter.onResponse(otherHttpRequest, httpResponseTwo);
        logFilter.onResponse(httpRequest, httpResponseThree);

        // then
        assertArrayEquals(logFilter.retrieve(null),
                new Expectation[]{
                        new Expectation(httpRequest, Times.once()).thenRespond(httpResponseOne),
                        new Expectation(httpRequest, Times.once()).thenRespond(httpResponseThree),
                        new Expectation(otherHttpRequest, Times.once()).thenRespond(httpResponseTwo)
                });
        assertArrayEquals(logFilter.retrieve(new HttpRequest()),
                new Expectation[]{
                        new Expectation(httpRequest, Times.once()).thenRespond(httpResponseOne),
                        new Expectation(httpRequest, Times.once()).thenRespond(httpResponseThree),
                        new Expectation(otherHttpRequest, Times.once()).thenRespond(httpResponseTwo)
                });
        assertArrayEquals(logFilter.retrieve(new HttpRequest().withPath("some_path")),
                new Expectation[]{
                        new Expectation(httpRequest, Times.once()).thenRespond(httpResponseOne),
                        new Expectation(httpRequest, Times.once()).thenRespond(httpResponseThree)
                });
        assertArrayEquals(logFilter.retrieve(new HttpRequest().withPath("some_other_path")),
                new Expectation[]{
                        new Expectation(otherHttpRequest, Times.once()).thenRespond(httpResponseTwo)
                });
    }

    @Test
    public void shouldVerifyWithDefaultTimes() {
        // given
        HttpResponse httpResponseOne = new HttpResponse().withBody("body_one");
        HttpResponse httpResponseTwo = new HttpResponse().withBody("body_two");
        HttpResponse httpResponseThree = new HttpResponse().withBody("body_three");
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");
        LogFilter logFilter = new LogFilter();

        // when
        logFilter.onResponse(httpRequest, httpResponseOne);
        logFilter.onResponse(otherHttpRequest, httpResponseTwo);
        logFilter.onResponse(httpRequest, httpResponseThree);

        // then
        assertThat(logFilter.verify(null), is(""));
        assertThat(logFilter.verify(
                        new Verification()
                                .withRequest(new HttpRequest())
                ),
                is("expected:<{ }> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"some_path\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"some_other_path\"" + System.getProperty("line.separator") +
                        "} ]>"));
        assertThat(logFilter.verify(
                        new Verification()
                                .withRequest(
                                        new HttpRequest()
                                                .withPath("some_path")
                                )
                ),
                is(""));
        assertThat(logFilter.verify(
                        new Verification()
                                .withRequest(
                                        new HttpRequest()
                                                .withPath("some_other_path")
                                )
                ),
                is(""));
    }

    @Test
    public void shouldVerifyWithAtLeastTimes() {
        // given
        HttpResponse httpResponseOne = new HttpResponse().withBody("body_one");
        HttpResponse httpResponseTwo = new HttpResponse().withBody("body_two");
        HttpResponse httpResponseThree = new HttpResponse().withBody("body_three");
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");
        LogFilter logFilter = new LogFilter();

        // when
        logFilter.onResponse(httpRequest, httpResponseOne);
        logFilter.onResponse(otherHttpRequest, httpResponseTwo);
        logFilter.onResponse(httpRequest, httpResponseThree);

        // then
        assertThat(logFilter.verify(null), is(""));
        assertThat(logFilter.verify(
                        new Verification()
                                .withRequest(new HttpRequest())
                                .withTimes(atLeast(0))
                ),
                is(""));
        assertThat(logFilter.verify(
                        new Verification()
                                .withRequest(new HttpRequest())
                                .withTimes(atLeast(3))
                ),
                is("expected:<{ }> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"some_path\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"some_other_path\"" + System.getProperty("line.separator") +
                        "} ]>"));
        assertThat(logFilter.verify(
                        new Verification()
                                .withRequest(
                                        new HttpRequest()
                                                .withPath("some_path")
                                )
                                .withTimes(atLeast(1))
                ),
                is(""));
        assertThat(logFilter.verify(
                        new Verification()
                                .withRequest(
                                        new HttpRequest()
                                                .withPath("some_path")
                                )
                                .withTimes(atLeast(2))
                ),
                is("expected:<{" + System.getProperty("line.separator") +
                        "  \"path\" : \"some_path\"" + System.getProperty("line.separator") +
                        "}> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"some_path\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"some_other_path\"" + System.getProperty("line.separator") +
                        "} ]>"));
        assertThat(logFilter.verify(
                        new Verification()
                                .withRequest(
                                        new HttpRequest()
                                                .withPath("some_other_path")
                                )
                                .withTimes(atLeast(1))
                ),
                is(""));
    }

    @Test
    public void shouldVerifyWithExactTimes() {
        // given
        HttpResponse httpResponseOne = new HttpResponse().withBody("body_one");
        HttpResponse httpResponseTwo = new HttpResponse().withBody("body_two");
        HttpResponse httpResponseThree = new HttpResponse().withBody("body_three");
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");
        LogFilter logFilter = new LogFilter();

        // when
        logFilter.onResponse(httpRequest, httpResponseOne);
        logFilter.onResponse(otherHttpRequest, httpResponseTwo);
        logFilter.onResponse(httpRequest, httpResponseThree);

        // then
        assertThat(logFilter.verify(null), is(""));
        assertThat(logFilter.verify(
                        new Verification()
                                .withRequest(new HttpRequest())
                                .withTimes(exactly(0))
                ),
                is("expected:<{ }> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"some_path\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"some_other_path\"" + System.getProperty("line.separator") +
                        "} ]>"));
        assertThat(logFilter.verify(
                        new Verification()
                                .withRequest(
                                        new HttpRequest()
                                                .withPath("some_path")
                                )
                                .withTimes(exactly(1))
                ),
                is(""));
        assertThat(logFilter.verify(
                        new Verification()
                                .withRequest(
                                        new HttpRequest()
                                                .withPath("some_path")
                                )
                                .withTimes(exactly(2))
                ),
                is("expected:<{" + System.getProperty("line.separator") +
                        "  \"path\" : \"some_path\"" + System.getProperty("line.separator") +
                        "}> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"some_path\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"some_other_path\"" + System.getProperty("line.separator") +
                        "} ]>"));
        assertThat(logFilter.verify(
                        new Verification()
                                .withRequest(
                                        new HttpRequest()
                                                .withPath("some_other_path")
                                )
                                .withTimes(exactly(1))
                ),
                is(""));
    }

    @Test
    public void shouldReset() {
        // given
        LogFilter logFilter = new LogFilter();
        logFilter.onResponse(new HttpRequest().withPath("some_path"), new HttpResponse().withBody("some_body"));
        logFilter.onResponse(new HttpRequest().withPath("some_other_path"), new HttpResponse().withBody("some_other_body"));
        logFilter.onResponse(new HttpRequest().withPath("some_path"), new HttpResponse().withBody("some_body"));

        // when
        logFilter.reset();

        // then
        assertEquals(logFilter.httpResponses(new HttpRequest()).size(), 0);
        assertEquals(logFilter.httpResponses(new HttpRequest().withPath("some_path")).size(), 0);
        assertEquals(logFilter.httpResponses(new HttpRequest().withPath("some_other_path")).size(), 0);
    }

    @Test
    public void shouldClearAllIfNull() {
        // given
        LogFilter logFilter = new LogFilter();
        logFilter.onResponse(new HttpRequest().withPath("some_path"), new HttpResponse().withBody("some_body"));
        logFilter.onResponse(new HttpRequest().withPath("some_other_path"), new HttpResponse().withBody("some_other_body"));
        logFilter.onResponse(new HttpRequest().withPath("some_path"), new HttpResponse().withBody("some_body"));

        // when
        logFilter.clear(null);

        // then
        assertEquals(logFilter.httpResponses(new HttpRequest()).size(), 0);
        assertEquals(logFilter.httpResponses(new HttpRequest().withPath("some_path")).size(), 0);
        assertEquals(logFilter.httpResponses(new HttpRequest().withPath("some_other_path")).size(), 0);
    }

    @Test
    public void shouldClearAllIfMatchesAll() {
        // given
        LogFilter logFilter = new LogFilter();
        logFilter.onResponse(new HttpRequest().withPath("some_path"), new HttpResponse().withBody("some_body"));
        logFilter.onResponse(new HttpRequest().withPath("some_other_path"), new HttpResponse().withBody("some_other_body"));
        logFilter.onResponse(new HttpRequest().withPath("some_path"), new HttpResponse().withBody("some_body"));

        // when
        logFilter.clear(new HttpRequest());

        // then
        assertEquals(logFilter.httpResponses(new HttpRequest()).size(), 0);
        assertEquals(logFilter.httpResponses(new HttpRequest().withPath("some_path")).size(), 0);
        assertEquals(logFilter.httpResponses(new HttpRequest().withPath("some_other_path")).size(), 0);
    }

    @Test
    public void shouldClearMatching() {
        // given
        LogFilter logFilter = new LogFilter();
        HttpResponse httpResponse = new HttpResponse().withBody("some_body");
        HttpResponse otherHttpResponse = new HttpResponse().withBody("some_other_body");
        HttpResponse anotherHttpResponse = new HttpResponse().withBody("some_body");
        logFilter.onResponse(new HttpRequest().withPath("some_path"), httpResponse);
        logFilter.onResponse(new HttpRequest().withPath("some_other_path"), otherHttpResponse);
        logFilter.onResponse(new HttpRequest().withPath("some_path"), anotherHttpResponse);

        // when
        logFilter.clear(new HttpRequest().withPath("some_path"));

        // then
        assertEquals(logFilter.httpResponses(new HttpRequest()), Arrays.asList(otherHttpResponse));
        assertEquals(logFilter.httpResponses(new HttpRequest().withPath("some_path")), Arrays.<HttpResponse>asList());
        assertEquals(logFilter.httpResponses(new HttpRequest().withPath("some_other_path")), Arrays.asList(otherHttpResponse));
    }
}
