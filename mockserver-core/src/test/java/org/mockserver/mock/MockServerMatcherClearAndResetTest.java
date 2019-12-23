package org.mockserver.mock;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.callback.WebSocketClientRegistry;
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
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockserver.matchers.TimeToLive.unlimited;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class MockServerMatcherClearAndResetTest {

    private MockServerMatcher mockServerMatcher;
    private MockServerLogger logFormatter;

    @Before
    public void prepareTestFixture() {
        logFormatter = new MockServerLogger();
        Scheduler scheduler = mock(Scheduler.class);
        WebSocketClientRegistry webSocketClientRegistry = mock(WebSocketClientRegistry.class);
        mockServerMatcher = new MockServerMatcher(logFormatter, scheduler, webSocketClientRegistry);
    }

    @Test
    public void shouldRemoveExpectationWhenNoMoreTimes() {
        // given
        Expectation expectation = new Expectation(
            request()
                .withPath("somepath"),
            Times.exactly(2),
            unlimited()
        ).thenRespond(
            response()
                .withBody("somebody")
        );

        // when
        mockServerMatcher.add(expectation);

        // and
        assertEquals(expectation, mockServerMatcher.postProcess(mockServerMatcher.firstMatchingExpectation(request().withPath("somepath"))));
        assertEquals(expectation, mockServerMatcher.postProcess(mockServerMatcher.firstMatchingExpectation(request().withPath("somepath"))));

        // then
        assertThat(mockServerMatcher.httpRequestMatchers, is(empty()));
        assertNull(mockServerMatcher.firstMatchingExpectation(request().withPath("somepath")));
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
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withPath(pathToMatchOn),
                Times.unlimited(),
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            )
        };
        mockServerMatcher.add(expectation[0]);
        mockServerMatcher.add(expectation[1]);

        // when
        mockServerMatcher.clear(request().withPath(pathToMatchOn));

        // then
        assertThat(mockServerMatcher.httpRequestMatchers, is(empty()));
    }

    @Test
    public void shouldResetAllExpectationsWhenHttpRequestNull() {
        // given
        Expectation[] expectation = {
            new Expectation(
                request()
                    .withPath("abc"),
                Times.unlimited(),
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withPath("def"),
                Times.unlimited(),
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            )
        };
        mockServerMatcher.add(expectation[0]);
        mockServerMatcher.add(expectation[1]);

        // when
        mockServerMatcher.clear(null);

        // then
        assertThat(mockServerMatcher.httpRequestMatchers, is(empty()));
    }

    @Test
    public void shouldResetAllExpectations() {
        // given
        Expectation[] expectation = {
            new Expectation(
                request()
                    .withPath("abc"),
                Times.unlimited(),
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withPath("def"),
                Times.unlimited(),
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            )
        };
        mockServerMatcher.add(expectation[0]);
        mockServerMatcher.add(expectation[1]);

        // when
        mockServerMatcher.reset();

        // then
        assertThat(mockServerMatcher.httpRequestMatchers, is(empty()));
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
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withPath("def"),
                Times.unlimited(),
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            )
        };
        mockServerMatcher.add(expectation[0]);
        mockServerMatcher.add(expectation[1]);

        // when
        mockServerMatcher.clear(request().withPath(pathToMatchOn));

        // then
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(1));
        assertThat(mockServerMatcher.httpRequestMatchers, containsInAnyOrder(new MatcherBuilder(logFormatter).transformsToMatcher(expectation[1])));
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
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod(methodToMatchOn)
                    .withPath("def"),
                Times.unlimited(),
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("POST")
                    .withPath("def"),
                Times.unlimited(),
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            )
        };
        mockServerMatcher.add(expectation[0]);
        mockServerMatcher.add(expectation[1]);
        mockServerMatcher.add(expectation[2]);

        // when
        mockServerMatcher.clear(request().withMethod(methodToMatchOn));

        // then
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(1));
        assertThat(mockServerMatcher.httpRequestMatchers, containsInAnyOrder(new MatcherBuilder(logFormatter).transformsToMatcher(expectation[2])));
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
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("PUT")
                    .withPath("def")
                    .withHeaders(headerToMatchOn),
                Times.unlimited(),
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("POST")
                    .withPath("def"),
                Times.unlimited(),
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            )
        };
        mockServerMatcher.add(expectation[0]);
        mockServerMatcher.add(expectation[1]);
        mockServerMatcher.add(expectation[2]);

        // when
        mockServerMatcher.clear(request().withHeader(headerToMatchOn));

        // then
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(1));
        assertThat(mockServerMatcher.httpRequestMatchers, containsInAnyOrder(new MatcherBuilder(logFormatter).transformsToMatcher(expectation[2])));
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
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("PUT")
                    .withPath("def")
                    .withHeaders(headerToMatchOn),
                Times.unlimited(),
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("POST")
                    .withPath("def")
                    .withHeaders(new Header("headerOneName", "headerOneValue")),
                Times.unlimited(),
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            )
        };
        mockServerMatcher.add(expectation[0]);
        mockServerMatcher.add(expectation[1]);
        mockServerMatcher.add(expectation[2]);

        // when
        mockServerMatcher.clear(request().withHeader(headerToMatchOn));

        // then
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(1));
        assertThat(mockServerMatcher.httpRequestMatchers, containsInAnyOrder(new MatcherBuilder(logFormatter).transformsToMatcher(expectation[2])));
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
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("GET")
                    .withPath("abc")
                    .withHeader(headerToMatchOn),
                Times.unlimited(),
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("POST")
                    .withPath("def"),
                Times.unlimited(),
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            )
        };
        mockServerMatcher.add(expectation[0]);
        mockServerMatcher.add(expectation[1]);
        mockServerMatcher.add(expectation[2]);

        // when
        mockServerMatcher.clear(request().withHeader(headerToMatchOn));

        // then
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(0));
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
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("PUT")
                    .withPath("def")
                    .withHeaders(headersToMatchOn),
                Times.unlimited(),
                unlimited()
            ).thenRespond(
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
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            )
        };
        mockServerMatcher.add(expectation[0]);
        mockServerMatcher.add(expectation[1]);
        mockServerMatcher.add(expectation[2]);

        // when
        mockServerMatcher.clear(request().withHeaders(headersToMatchOn));

        // then
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(1));
        assertThat(mockServerMatcher.httpRequestMatchers, containsInAnyOrder(new MatcherBuilder(logFormatter).transformsToMatcher(expectation[2])));
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
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("GET")
                    .withPath("abc")
                    .withHeaders(headersToMatchOn),
                Times.unlimited(),
                unlimited()
            ).thenRespond(
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
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            )
        };
        mockServerMatcher.add(expectation[0]);
        mockServerMatcher.add(expectation[1]);
        mockServerMatcher.add(expectation[2]);

        // when
        mockServerMatcher.clear(request().withHeaders(headersToMatchOn));

        // then
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(0));
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
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("PUT")
                    .withPath("def")
                    .withHeaders(headersToMatchOn),
                Times.unlimited(),
                unlimited()
            ).thenRespond(
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
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            )
        };
        mockServerMatcher.add(expectation[0]);
        mockServerMatcher.add(expectation[1]);
        mockServerMatcher.add(expectation[2]);

        // when
        mockServerMatcher.clear(request().withHeaders(headersToMatchOn));

        // then
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(1));
        assertThat(mockServerMatcher.httpRequestMatchers, containsInAnyOrder(new MatcherBuilder(logFormatter).transformsToMatcher(expectation[2])));
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
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("GET")
                    .withPath("abc")
                    .withHeaders(headersToMatchOn),
                Times.unlimited(),
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("POST")
                    .withPath("def"),
                Times.unlimited(),
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            )
        };
        mockServerMatcher.add(expectation[0]);
        mockServerMatcher.add(expectation[1]);
        mockServerMatcher.add(expectation[2]);

        // when
        mockServerMatcher.clear(request().withHeaders(headersToMatchOn));

        // then
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(0));
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
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("PUT")
                    .withPath("def")
                    .withCookies(cookiesToMatchOn),
                Times.unlimited(),
                unlimited()
            ).thenRespond(
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
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            )
        };
        mockServerMatcher.add(expectation[0]);
        mockServerMatcher.add(expectation[1]);
        mockServerMatcher.add(expectation[2]);

        // when
        mockServerMatcher.clear(request().withCookies(cookiesToMatchOn));

        // then
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(1));
        assertThat(mockServerMatcher.httpRequestMatchers, containsInAnyOrder(new MatcherBuilder(logFormatter).transformsToMatcher(expectation[2])));
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
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("GET")
                    .withPath("abc")
                    .withCookies(cookiesToMatchOn),
                Times.unlimited(),
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("POST")
                    .withPath("def"),
                Times.unlimited(),
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            )
        };
        mockServerMatcher.add(expectation[0]);
        mockServerMatcher.add(expectation[1]);
        mockServerMatcher.add(expectation[2]);

        // when
        mockServerMatcher.clear(request().withCookies(cookiesToMatchOn));

        // then
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(0));
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
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("PUT")
                    .withPath("def")
                    .withQueryStringParameters(parametersToMatchOn),
                Times.unlimited(),
                unlimited()
            ).thenRespond(
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
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            )
        };
        mockServerMatcher.add(expectation[0]);
        mockServerMatcher.add(expectation[1]);
        mockServerMatcher.add(expectation[2]);

        // when
        mockServerMatcher.clear(request().withQueryStringParameters(parametersToMatchOn));

        // then
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(1));
        assertThat(mockServerMatcher.httpRequestMatchers, containsInAnyOrder(new MatcherBuilder(logFormatter).transformsToMatcher(expectation[2])));
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
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("GET")
                    .withPath("abc")
                    .withQueryStringParameters(parametersToMatchOn),
                Times.unlimited(),
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            ),
            new Expectation(
                request()
                    .withMethod("POST")
                    .withPath("def"),
                Times.unlimited(),
                unlimited()
            ).thenRespond(
                response()
                    .withBody("somebody")
            )
        };
        mockServerMatcher.add(expectation[0]);
        mockServerMatcher.add(expectation[1]);
        mockServerMatcher.add(expectation[2]);

        // when
        mockServerMatcher.clear(request().withQueryStringParameters(parametersToMatchOn));

        // then
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(0));
    }

    @Test
    public void shouldClearNoExpectations() {
        // given
        HttpResponse httpResponse = response().withBody("somebody");
        Expectation[] expectations = new Expectation[]{
            new Expectation(request().withPath("somepath"), Times.unlimited(), unlimited()).thenRespond(httpResponse),
            new Expectation(request().withPath("somepath"), Times.unlimited(), unlimited()).thenRespond(httpResponse)
        };
        for (Expectation expectation : expectations) {
            mockServerMatcher.add(expectation);
        }
        List<HttpRequestMatcher> httpRequestMatchers = new ArrayList<>(mockServerMatcher.httpRequestMatchers);

        // when
        mockServerMatcher.clear(request().withPath("foobar"));

        // then
        assertThat(mockServerMatcher.httpRequestMatchers, is(httpRequestMatchers));
    }

}
