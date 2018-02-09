package org.mockserver.mock;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.scheduler.Scheduler;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class MockServerMatcherManageExpectationsTest {

    private MockServerMatcher mockServerMatcher;
    private HttpRequest httpRequest;
    private HttpResponse httpResponse;
    private Scheduler scheduler;

    @Before
    public void prepareTestFixture() {
        scheduler = mock(Scheduler.class);
        httpRequest = new HttpRequest();
        httpResponse = new HttpResponse();
        mockServerMatcher = new MockServerMatcher(new MockServerLogger(this.getClass()), scheduler);
    }

    @Test
    public void shouldRemoveExpiredExpectationWhenMatching() {
        // when
        mockServerMatcher.add(new Expectation(httpRequest.withPath("somePath"), Times.unlimited(), TimeToLive.exactly(TimeUnit.MICROSECONDS, 0L)).thenRespond(httpResponse.withBody("someBody")));

        // then
        assertThat(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somePath")), nullValue());
        assertThat(mockServerMatcher.httpRequestMatchers, empty());
    }

    @Test
    public void shouldRemoveExpiredExpectationWhenNotMatching() {
        // when
        mockServerMatcher.add(new Expectation(httpRequest.withPath("somePath"), Times.unlimited(), TimeToLive.exactly(TimeUnit.MICROSECONDS, 0L)).thenRespond(httpResponse.withBody("someBody")));

        // then
        assertThat(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("someOtherPath")), nullValue());
        assertThat(mockServerMatcher.httpRequestMatchers, empty());
    }

    @Test
    public void shouldRemoveMultipleExpiredExpectations() throws InterruptedException {
        // when
        mockServerMatcher.add(new Expectation(httpRequest.withPath("somePath"), Times.unlimited(), TimeToLive.exactly(TimeUnit.MICROSECONDS, 0L)).thenRespond(httpResponse.withBody("someBody")));
        Expectation expectationToExpireAfter3Seconds = new Expectation(httpRequest.withPath("somePath"), Times.unlimited(), TimeToLive.exactly(TimeUnit.SECONDS, 3L));
        mockServerMatcher.add(expectationToExpireAfter3Seconds.thenRespond(httpResponse.withBody("someBody")));

        // then
        assertThat(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somePath")), is(expectationToExpireAfter3Seconds));
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(1));

        // when
        TimeUnit.SECONDS.sleep(3);

        // then - after 3 seconds
        assertThat(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("someOtherPath")), nullValue());
        assertThat(mockServerMatcher.httpRequestMatchers, empty());
    }

    @Test
    public void shouldNotRemoveNotExpiredExpectationsWhenMatching() {
        // when
        Expectation expectation = new Expectation(httpRequest.withPath("somePath"), Times.unlimited(), TimeToLive.exactly(TimeUnit.HOURS, 1L)).thenRespond(httpResponse.withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertThat(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somePath")), is(expectation));
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(1));
    }

    @Test
    public void shouldNotRemoveNotExpiredExpectationsWhenNotMatching() {
        // when
        mockServerMatcher.add(new Expectation(httpRequest.withPath("somePath"), Times.unlimited(), TimeToLive.exactly(TimeUnit.HOURS, 1L)).thenRespond(httpResponse.withBody("someBody")));

        // then
        assertThat(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("someOtherPath")), nullValue());
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(1));
    }

    @Test
    public void shouldRemoveUsedExpectations() {
        // when
        Expectation expectation = new Expectation(httpRequest.withPath("somePath"), Times.exactly(1), TimeToLive.unlimited()).thenRespond(httpResponse.withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertThat(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somePath")), is(expectation));
        assertThat(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somePath")), nullValue());
        assertThat(mockServerMatcher.httpRequestMatchers, empty());
    }

    @Test
    public void shouldNotRemoveNotUsedExpectations() {
        // when
        Expectation expectation = new Expectation(httpRequest.withPath("somePath"), Times.exactly(2), TimeToLive.unlimited()).thenRespond(httpResponse.withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertThat(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somePath")), is(expectation));
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(1));
    }

    @Test
    public void shouldNotRemoveAfterTimesComplete() {
        // when
        Expectation expectation = new Expectation(new HttpRequest().withPath("somepath"), Times.exactly(2), TimeToLive.unlimited()).thenRespond(httpResponse.withBody("someBody"));
        mockServerMatcher.add(expectation);
        Expectation notRemovedExpectation = new Expectation(httpRequest.withPath("someOtherPath"), Times.exactly(2), TimeToLive.unlimited());
        mockServerMatcher.add(notRemovedExpectation.thenRespond(response().withBody("someOtherBody")));

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
        assertEquals(notRemovedExpectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("someOtherPath")));
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(1));

        // then
        assertEquals(notRemovedExpectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("someOtherPath")));
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(0));
    }

    @Test
    public void shouldNotRemoveAfterTimesCompleteOrExpired() {
        // when
        mockServerMatcher.add(new Expectation(new HttpRequest().withPath("somepath"), Times.exactly(0), TimeToLive.unlimited()).thenRespond(httpResponse.withBody("someBody")));
        mockServerMatcher.add(new Expectation(httpRequest.withPath("someOtherPath"), Times.unlimited(), TimeToLive.exactly(TimeUnit.MICROSECONDS, 0L)).thenRespond(response().withBody("someOtherBody")));
        mockServerMatcher.add(new Expectation(httpRequest.withPath("someOtherPath"), Times.exactly(0), TimeToLive.exactly(TimeUnit.MICROSECONDS, 0L)).thenRespond(response().withBody("someOtherBody")));

        // then
        assertThat(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath")), nullValue());
        assertThat(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("someOtherPath")), nullValue());
        assertThat(mockServerMatcher.httpRequestMatchers, empty());
    }
}
