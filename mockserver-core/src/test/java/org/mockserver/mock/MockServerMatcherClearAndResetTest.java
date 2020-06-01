package org.mockserver.mock;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.matchers.Times;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Parameter;
import org.mockserver.scheduler.Scheduler;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockserver.matchers.TimeToLive.unlimited;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.ui.MockServerMatcherNotifier.Cause.API;

/**
 * @author jamesdbloom
 */
public class MockServerMatcherClearAndResetTest {

    private RequestMatchers requestMatchers;
    private MockServerLogger mockServerLogger;

    @Before
    public void prepareTestFixture() {
        mockServerLogger = new MockServerLogger();
        WebSocketClientRegistry webSocketClientRegistry = mock(WebSocketClientRegistry.class);
        requestMatchers = new RequestMatchers(mockServerLogger, new Scheduler(mockServerLogger, true), webSocketClientRegistry);
    }

    @Test
    public void shouldRemoveExpectationWhenNoMoreTimes() {
        // given
        Expectation expectation = new Expectation(
            request()
                .withPath("somepath"),
            Times.exactly(2),
            unlimited(),
                0).thenRespond(
            response()
                .withBody("somebody")
        );

        // when
        requestMatchers.add(expectation, API);

        // and
        assertEquals(expectation, requestMatchers.postProcess(requestMatchers.firstMatchingExpectation(request().withPath("somepath"))));
        assertEquals(expectation, requestMatchers.postProcess(requestMatchers.firstMatchingExpectation(request().withPath("somepath"))));

        // then
        assertThat(requestMatchers.httpRequestMatchers, is(empty()));
        assertNull(requestMatchers.firstMatchingExpectation(request().withPath("somepath")));
    }

    @Test
    public void shouldClearAllExpectations() {
        // given
        // given
        String pathToMatchOn = "somePath";
        Expectation[] expectation = {
            new Expectation(
                request()
                    .withPath(pathToMatchOn),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withPath(pathToMatchOn),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            )
        };
        requestMatchers.add(expectation[0], API);
        requestMatchers.add(expectation[1], API);

        // when
        requestMatchers.clear(request().withPath(pathToMatchOn));

        // then
        assertThat(requestMatchers.httpRequestMatchers, is(empty()));
    }

    @Test
    public void shouldResetAllExpectationsWhenHttpRequestNull() {
        // given
        Expectation[] expectation = {
            new Expectation(
                request()
                    .withPath("abc"),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withPath("def"),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            )
        };
        requestMatchers.add(expectation[0], API);
        requestMatchers.add(expectation[1], API);

        // when
        requestMatchers.clear(null);

        // then
        assertThat(requestMatchers.httpRequestMatchers, is(empty()));
    }

    @Test
    public void shouldResetAllExpectations() {
        // given
        Expectation[] expectation = {
            new Expectation(
                request()
                    .withPath("abc"),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withPath("def"),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            )
        };
        requestMatchers.add(expectation[0], API);
        requestMatchers.add(expectation[1], API);

        // when
        requestMatchers.reset();

        // then
        assertThat(requestMatchers.httpRequestMatchers, is(empty()));
    }

    @Test
    public void shouldClearMatchingExpectationsByPathOnly() {
        // given
        String pathToMatchOn = "abc";
        Expectation[] expectation = {
            new Expectation(
                request()
                    .withPath(pathToMatchOn),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withPath("def"),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            )
        };
        requestMatchers.add(expectation[0], API);
        requestMatchers.add(expectation[1], API);

        // when
        requestMatchers.clear(request().withPath(pathToMatchOn));

        // then
        assertThat(requestMatchers.httpRequestMatchers.size(), is(1));
        assertThat(requestMatchers.httpRequestMatchers, containsInAnyOrder(new MatcherBuilder(mockServerLogger).transformsToMatcher(expectation[1])));
    }

    @Test
    public void shouldClearMatchingExpectationsByMethodOnly() {
        // given
        String methodToMatchOn = "GET";
        Expectation[] expectation = {
            new Expectation(
                request()
                    .withMethod(methodToMatchOn)
                    .withPath("abc"),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod(methodToMatchOn)
                    .withPath("def"),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("POST")
                    .withPath("def"),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            )
        };
        requestMatchers.add(expectation[0], API);
        requestMatchers.add(expectation[1], API);
        requestMatchers.add(expectation[2], API);

        // when
        requestMatchers.clear(request().withMethod(methodToMatchOn));

        // then
        assertThat(requestMatchers.httpRequestMatchers.size(), is(1));
        assertThat(requestMatchers.httpRequestMatchers, containsInAnyOrder(new MatcherBuilder(mockServerLogger).transformsToMatcher(expectation[2])));
    }

    @Test
    public void shouldClearMatchingExpectationsByHeaderOnly() {
        // given
        Header headerToMatchOn = new Header("headerOneName", "headerOneValue");
        Expectation[] expectation = {
            new Expectation(
                request()
                    .withMethod("GET")
                    .withPath("abc")
                    .withHeader(headerToMatchOn),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("PUT")
                    .withPath("def")
                    .withHeaders(headerToMatchOn),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("POST")
                    .withPath("def"),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            )
        };
        requestMatchers.add(expectation[0], API);
        requestMatchers.add(expectation[1], API);
        requestMatchers.add(expectation[2], API);

        // when
        requestMatchers.clear(request().withHeader(headerToMatchOn));

        // then
        assertThat(requestMatchers.httpRequestMatchers.size(), is(1));
        assertThat(requestMatchers.httpRequestMatchers, containsInAnyOrder(new MatcherBuilder(mockServerLogger).transformsToMatcher(expectation[2])));
    }

    @Test
    public void shouldClearMatchingExpectationsWithNottedHeaders() {
        // given
        Header headerToMatchOn = new Header("!headerOneName", "!headerOneValue");
        Expectation[] expectation = {
            new Expectation(
                request()
                    .withMethod("GET")
                    .withPath("abc")
                    .withHeader(headerToMatchOn),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("PUT")
                    .withPath("def")
                    .withHeaders(headerToMatchOn),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("POST")
                    .withPath("def")
                    .withHeaders(new Header("headerOneName", "headerOneValue")),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            )
        };
        requestMatchers.add(expectation[0], API);
        requestMatchers.add(expectation[1], API);
        requestMatchers.add(expectation[2], API);

        // when
        requestMatchers.clear(request().withHeader(headerToMatchOn));

        // then
        assertThat(requestMatchers.httpRequestMatchers.size(), is(1));
        assertThat(requestMatchers.httpRequestMatchers, containsInAnyOrder(new MatcherBuilder(mockServerLogger).transformsToMatcher(expectation[2])));
    }

    @Test
    public void shouldClearAllMatchingExpectationsWithNottedHeaders() {
        // given
        Header headerToMatchOn = new Header("!headerOneName", "!headerOneValue");
        Expectation[] expectation = {
            new Expectation(
                request()
                    .withMethod("GET")
                    .withPath("abc")
                    .withHeader(headerToMatchOn),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("GET")
                    .withPath("abc")
                    .withHeader(headerToMatchOn),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("POST")
                    .withPath("def"),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            )
        };
        requestMatchers.add(expectation[0], API);
        requestMatchers.add(expectation[1], API);
        requestMatchers.add(expectation[2], API);

        // when
        requestMatchers.clear(request().withHeader(headerToMatchOn));

        // then
        assertThat(requestMatchers.httpRequestMatchers.size(), is(0));
    }

    @Test
    public void shouldClearMatchingExpectationsWithHeadersAndNottedHeaders() {
        // given
        Header[] headersToMatchOn = new Header[]{
            new Header("!headerOneName", "!headerOneValue"),
            new Header("headerTwoName", "headerTwoName")
        };
        Expectation[] expectation = {
            new Expectation(
                request()
                    .withMethod("GET")
                    .withPath("abc")
                    .withHeaders(headersToMatchOn),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("PUT")
                    .withPath("def")
                    .withHeaders(headersToMatchOn),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("POST")
                    .withPath("def")
                    .withHeaders(
                        new Header("headerOneName", "headerOneValue")
                    ),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            )
        };
        requestMatchers.add(expectation[0], API);
        requestMatchers.add(expectation[1], API);
        requestMatchers.add(expectation[2], API);

        // when
        requestMatchers.clear(request().withHeaders(headersToMatchOn));

        // then
        assertThat(requestMatchers.httpRequestMatchers.size(), is(1));
        assertThat(requestMatchers.httpRequestMatchers, containsInAnyOrder(new MatcherBuilder(mockServerLogger).transformsToMatcher(expectation[2])));
    }

    @Test
    public void shouldClearAllMatchingExpectationsWithHeadersAndNottedHeaders() {
        // given
        Header[] headersToMatchOn = new Header[]{
            new Header("!headerOneName", "!headerOneValue"),
            new Header("headerTwoName", "headerTwoName")
        };
        Expectation[] expectation = {
            new Expectation(
                request()
                    .withMethod("GET")
                    .withPath("abc")
                    .withHeaders(headersToMatchOn),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("GET")
                    .withPath("abc")
                    .withHeaders(headersToMatchOn),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("POST")
                    .withPath("def")
                    .withHeaders(
                        new Header("headerTwoName", "headerTwoName")
                    ),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            )
        };
        requestMatchers.add(expectation[0], API);
        requestMatchers.add(expectation[1], API);
        requestMatchers.add(expectation[2], API);

        // when
        requestMatchers.clear(request().withHeaders(headersToMatchOn));

        // then
        assertThat(requestMatchers.httpRequestMatchers.size(), is(0));
    }

    @Test
    public void shouldClearMatchingExpectationsWithMultipleNottedHeaders() {
        // given
        Header[] headersToMatchOn = new Header[]{
            new Header("!headerOneName", "!headerOneValue"),
            new Header("!headerTwoName", "!headerTwoName")
        };
        Expectation[] expectation = {
            new Expectation(
                request()
                    .withMethod("GET")
                    .withPath("abc")
                    .withHeaders(headersToMatchOn),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("PUT")
                    .withPath("def")
                    .withHeaders(headersToMatchOn),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("POST")
                    .withPath("def")
                    .withHeaders(
                        new Header("headerOneName", "headerOneValue"),
                        new Header("headerTwoName", "headerTwoName")
                    ),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            )
        };
        requestMatchers.add(expectation[0], API);
        requestMatchers.add(expectation[1], API);
        requestMatchers.add(expectation[2], API);

        // when
        requestMatchers.clear(request().withHeaders(headersToMatchOn));

        // then
        assertThat(requestMatchers.httpRequestMatchers.size(), is(1));
        assertThat(requestMatchers.httpRequestMatchers, containsInAnyOrder(new MatcherBuilder(mockServerLogger).transformsToMatcher(expectation[2])));
    }

    @Test
    public void shouldClearAllMatchingExpectationsWithMultipleNottedHeaders() {
        // given
        Header[] headersToMatchOn = new Header[]{
            new Header("!headerOneName", "!headerOneValue"),
            new Header("!headerTwoName", "!headerTwoName")
        };
        Expectation[] expectation = {
            new Expectation(
                request()
                    .withMethod("GET")
                    .withPath("abc")
                    .withHeaders(headersToMatchOn),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("GET")
                    .withPath("abc")
                    .withHeaders(headersToMatchOn),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("POST")
                    .withPath("def"),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            )
        };
        requestMatchers.add(expectation[0], API);
        requestMatchers.add(expectation[1], API);
        requestMatchers.add(expectation[2], API);

        // when
        requestMatchers.clear(request().withHeaders(headersToMatchOn));

        // then
        assertThat(requestMatchers.httpRequestMatchers.size(), is(0));
    }

    @Test
    public void shouldClearMatchingExpectationsWithMultipleNottedCookies() {
        // given
        Cookie[] cookiesToMatchOn = new Cookie[]{
            new Cookie("!cookieOneName", "!cookieOneValue"),
            new Cookie("!cookieTwoName", "!cookieTwoName")
        };
        Expectation[] expectation = {
            new Expectation(
                request()
                    .withMethod("GET")
                    .withPath("abc")
                    .withCookies(cookiesToMatchOn),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("PUT")
                    .withPath("def")
                    .withCookies(cookiesToMatchOn),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("POST")
                    .withPath("def")
                    .withCookies(
                        new Cookie("cookieOneName", "cookieOneValue"),
                        new Cookie("cookieTwoName", "cookieTwoName")
                    ),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            )
        };
        requestMatchers.add(expectation[0], API);
        requestMatchers.add(expectation[1], API);
        requestMatchers.add(expectation[2], API);

        // when
        requestMatchers.clear(request().withCookies(cookiesToMatchOn));

        // then
        assertThat(requestMatchers.httpRequestMatchers.size(), is(1));
        assertThat(requestMatchers.httpRequestMatchers, containsInAnyOrder(new MatcherBuilder(mockServerLogger).transformsToMatcher(expectation[2])));
    }

    @Test
    public void shouldClearAllMatchingExpectationsWithMultipleNottedCookies() {
        // given
        Cookie[] cookiesToMatchOn = new Cookie[]{
            new Cookie("!cookieOneName", "!cookieOneValue"),
            new Cookie("!cookieTwoName", "!cookieTwoName")
        };
        Expectation[] expectation = {
            new Expectation(
                request()
                    .withMethod("GET")
                    .withPath("abc")
                    .withCookies(cookiesToMatchOn),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("GET")
                    .withPath("abc")
                    .withCookies(cookiesToMatchOn),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("POST")
                    .withPath("def"),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            )
        };
        requestMatchers.add(expectation[0], API);
        requestMatchers.add(expectation[1], API);
        requestMatchers.add(expectation[2], API);

        // when
        requestMatchers.clear(request().withCookies(cookiesToMatchOn));

        // then
        assertThat(requestMatchers.httpRequestMatchers.size(), is(0));
    }

    @Test
    public void shouldClearMatchingExpectationsWithMultipleNottedParameters() {
        // given
        Parameter[] parametersToMatchOn = new Parameter[]{
            new Parameter("!parameterOneName", "!parameterOneValue"),
            new Parameter("!parameterTwoName", "!parameterTwoName")
        };
        Expectation[] expectation = {
            new Expectation(
                request()
                    .withMethod("GET")
                    .withPath("abc")
                    .withQueryStringParameters(parametersToMatchOn),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("PUT")
                    .withPath("def")
                    .withQueryStringParameters(parametersToMatchOn),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("POST")
                    .withPath("def")
                    .withQueryStringParameters(
                        new Parameter("parameterOneName", "parameterOneValue"),
                        new Parameter("parameterTwoName", "parameterTwoName")
                    ),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            )
        };
        requestMatchers.add(expectation[0], API);
        requestMatchers.add(expectation[1], API);
        requestMatchers.add(expectation[2], API);

        // when
        requestMatchers.clear(request().withQueryStringParameters(parametersToMatchOn));

        // then
        assertThat(requestMatchers.httpRequestMatchers.size(), is(1));
        assertThat(requestMatchers.httpRequestMatchers, containsInAnyOrder(new MatcherBuilder(mockServerLogger).transformsToMatcher(expectation[2])));
    }

    @Test
    public void shouldClearAllMatchingExpectationsWithMultipleNottedParameters() {
        // given
        Parameter[] parametersToMatchOn = new Parameter[]{
            new Parameter("!parameterOneName", "!parameterOneValue"),
            new Parameter("!parameterTwoName", "!parameterTwoName")
        };
        Expectation[] expectation = {
            new Expectation(
                request()
                    .withMethod("GET")
                    .withPath("abc")
                    .withQueryStringParameters(parametersToMatchOn),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("GET")
                    .withPath("abc")
                    .withQueryStringParameters(parametersToMatchOn),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("POST")
                    .withPath("def"),
                Times.unlimited(),
                unlimited(),
                    0).thenRespond(
                response()
                    .withBody("somebody")
            )
        };
        requestMatchers.add(expectation[0], API);
        requestMatchers.add(expectation[1], API);
        requestMatchers.add(expectation[2], API);

        // when
        requestMatchers.clear(request().withQueryStringParameters(parametersToMatchOn));

        // then
        assertThat(requestMatchers.httpRequestMatchers.size(), is(0));
    }

    @Test
    public void shouldClearNoExpectations() {
        // given
        HttpResponse httpResponse = response().withBody("somebody");
        Expectation[] expectations = new Expectation[]{
            new Expectation(request().withPath("somepath"), Times.unlimited(), unlimited(), 0).thenRespond(httpResponse),
            new Expectation(request().withPath("somepath"), Times.unlimited(), unlimited(), 0).thenRespond(httpResponse)
        };
        for (Expectation expectation : expectations) {
            requestMatchers.add(expectation, API);
        }
        List<HttpRequestMatcher> httpRequestMatchers = new ArrayList<>(requestMatchers.httpRequestMatchers);

        // when
        requestMatchers.clear(request().withPath("foobar"));

        // then
        assertThat(requestMatchers.httpRequestMatchers.toSortedList(), is(httpRequestMatchers));
    }

}
