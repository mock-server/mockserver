package org.mockserver.mock;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * @author jamesdbloom
 */
public class MockServerMatcherClearAndResetTest {

    private MockServerMatcher mockServerMatcher;
    private MockServerLogger logFormatter;

    @Before
    public void prepareTestFixture() {
        logFormatter = mock(MockServerLogger.class);
        mockServerMatcher = new MockServerMatcher(logFormatter);
    }

    @Test
    public void shouldRemoveExpectationWhenNoMoreTimes() {
        // given
        HttpResponse httpResponse = new HttpResponse().withBody("somebody");
        Expectation expectation = new Expectation(new HttpRequest().withPath("somepath"), Times.exactly(2), TimeToLive.unlimited()).thenRespond(httpResponse);

        // when
        mockServerMatcher.add(expectation);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
        assertThat(mockServerMatcher.httpRequestMatchers, is(empty()));
        assertEquals(null, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
    }

    @Test
    public void shouldClearAllExpectations() {
        // given
        HttpResponse httpResponse = new HttpResponse().withBody("somebody");
        mockServerMatcher.add(new Expectation(new HttpRequest().withPath("somepath"), Times.unlimited(), TimeToLive.unlimited()).thenRespond(httpResponse));
        mockServerMatcher.add(new Expectation(new HttpRequest().withPath("somepath"), Times.unlimited(), TimeToLive.unlimited()).thenRespond(httpResponse));

        // when
        mockServerMatcher.clear(new HttpRequest().withPath("somepath"));

        // then
        assertThat(mockServerMatcher.httpRequestMatchers, is(empty()));
    }

    @Test
    public void shouldResetAllExpectationsWhenHttpRequestNull() {
        // given
        HttpResponse httpResponse = new HttpResponse().withBody("somebody");
        mockServerMatcher.add(new Expectation(new HttpRequest().withPath("somepath"), Times.unlimited(), TimeToLive.unlimited()).thenRespond(httpResponse));
        mockServerMatcher.add(new Expectation(new HttpRequest().withPath("somepath"), Times.unlimited(), TimeToLive.unlimited()).thenRespond(httpResponse));

        // when
        mockServerMatcher.clear(null);

        // then
        assertThat(mockServerMatcher.httpRequestMatchers, is(empty()));
    }

    @Test
    public void shouldResetAllExpectations() {
        // given
        HttpResponse httpResponse = new HttpResponse().withBody("somebody");
        mockServerMatcher.add(new Expectation(new HttpRequest().withPath("somepath"), Times.unlimited(), TimeToLive.unlimited()).thenRespond(httpResponse));
        mockServerMatcher.add(new Expectation(new HttpRequest().withPath("somepath"), Times.unlimited(), TimeToLive.unlimited()).thenRespond(httpResponse));

        // when
        mockServerMatcher.reset();

        // then
        assertThat(mockServerMatcher.httpRequestMatchers, is(empty()));
    }

    @Test
    public void shouldClearMatchingExpectationsByPathOnly() {
        // given
        HttpResponse httpResponse = new HttpResponse().withBody("somebody");
        mockServerMatcher.add(new Expectation(new HttpRequest().withPath("abc"), Times.unlimited(), TimeToLive.unlimited()).thenRespond(httpResponse));
        Expectation expectation = new Expectation(new HttpRequest().withPath("def"), Times.unlimited(), TimeToLive.unlimited()).thenRespond(httpResponse);
        mockServerMatcher.add(expectation);

        // when
        mockServerMatcher.clear(new HttpRequest().withPath("abc"));

        // then
        assertThat(mockServerMatcher.httpRequestMatchers, hasItems(new MatcherBuilder(logFormatter).transformsToMatcher(expectation)));
    }

    @Test
    public void shouldClearMatchingExpectationsByMethodOnly() {
        // given
        HttpResponse httpResponse = new HttpResponse().withBody("somebody");
        mockServerMatcher.add(new Expectation(new HttpRequest().withMethod("GET").withPath("abc"), Times.unlimited(), TimeToLive.unlimited()).thenRespond(httpResponse));
        mockServerMatcher.add(new Expectation(new HttpRequest().withMethod("GET").withPath("def"), Times.unlimited(), TimeToLive.unlimited()).thenRespond(httpResponse));
        Expectation expectation = new Expectation(new HttpRequest().withMethod("POST").withPath("def"), Times.unlimited(), TimeToLive.unlimited()).thenRespond(httpResponse);
        mockServerMatcher.add(expectation);

        // when
        mockServerMatcher.clear(new HttpRequest().withMethod("GET"));

        // then
        assertThat(mockServerMatcher.httpRequestMatchers, hasItems(new MatcherBuilder(logFormatter).transformsToMatcher(expectation)));
    }

    @Test
    public void shouldClearMatchingExpectationsByHeaderOnly() {
        // given
        HttpResponse httpResponse = new HttpResponse().withBody("somebody");
        mockServerMatcher.add(new Expectation(new HttpRequest().withMethod("GET").withPath("abc").withHeader(new Header("headerOneName", "headerOneValue")), Times.unlimited(), TimeToLive.unlimited()).thenRespond(httpResponse));
        mockServerMatcher.add(new Expectation(new HttpRequest().withMethod("PUT").withPath("def").withHeaders(new Header("headerOneName", "headerOneValue")), Times.unlimited(), TimeToLive.unlimited()).thenRespond(httpResponse));
        Expectation expectation = new Expectation(new HttpRequest().withMethod("POST").withPath("def"), Times.unlimited(), TimeToLive.unlimited()).thenRespond(httpResponse);
        mockServerMatcher.add(expectation);

        // when
        mockServerMatcher.clear(new HttpRequest().withHeader(new Header("headerOneName", "headerOneValue")));

        // then
        assertThat(mockServerMatcher.httpRequestMatchers, hasItems(new MatcherBuilder(logFormatter).transformsToMatcher(expectation)));
    }
    @Test
    public void shouldClearNoExpectations() {
        // given
        HttpResponse httpResponse = new HttpResponse().withBody("somebody");
        Expectation[] expectations = new Expectation[]{
                new Expectation(new HttpRequest().withPath("somepath"), Times.unlimited(), TimeToLive.unlimited()).thenRespond(httpResponse),
                new Expectation(new HttpRequest().withPath("somepath"), Times.unlimited(), TimeToLive.unlimited()).thenRespond(httpResponse)
        };
        for (Expectation expectation : expectations) {
            mockServerMatcher.add(expectation);
        }
        List<HttpRequestMatcher> httpRequestMatchers = new ArrayList<>(mockServerMatcher.httpRequestMatchers);

        // when
        mockServerMatcher.clear(new HttpRequest().withPath("foobar"));

        // then
        assertThat(mockServerMatcher.httpRequestMatchers, is(httpRequestMatchers));
    }

}
