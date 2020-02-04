package org.mockserver.mock;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.scheduler.Scheduler;

import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class MockServerMatcherManageExpectationsTest {

    private MockServerMatcher mockServerMatcher;

    @Before
    public void prepareTestFixture() {
        Scheduler scheduler = mock(Scheduler.class);
        WebSocketClientRegistry webSocketClientRegistry = mock(WebSocketClientRegistry.class);
        mockServerMatcher = new MockServerMatcher(new MockServerLogger(), scheduler, webSocketClientRegistry);
    }

    @Test
    public void shouldRemoveExpiredExpectationWhenMatching() {
        // when
        mockServerMatcher.add(new Expectation(request().withPath("somePath"), Times.unlimited(), TimeToLive.exactly(TimeUnit.MICROSECONDS, 0L)).thenRespond(response().withBody("someBody")));

        // then
        assertThat(mockServerMatcher.postProcess(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somePath"))), nullValue());
        assertThat(mockServerMatcher.httpRequestMatchers, empty());
    }

    @Test
    public void shouldRemoveExpiredExpectationWhenNotMatching() {
        // when
        mockServerMatcher.add(new Expectation(request().withPath("somePath"), Times.unlimited(), TimeToLive.exactly(TimeUnit.MICROSECONDS, 0L)).thenRespond(response().withBody("someBody")));

        // then
        assertThat(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("someOtherPath")), nullValue());
        assertThat(mockServerMatcher.httpRequestMatchers, empty());
    }

    @Test
    public void shouldRemoveMultipleExpiredExpectations() throws InterruptedException {
        // when
        mockServerMatcher
            .add(
                new Expectation(request().withPath("somePath"), Times.unlimited(), TimeToLive.exactly(TimeUnit.MICROSECONDS, 0L))
                    .thenRespond(response().withBody("someBody"))
            );
        Expectation expectationToExpireAfter3Seconds =
            new Expectation(request().withPath("somePath"), Times.unlimited(), TimeToLive.exactly(MILLISECONDS, 1500L))
                .thenRespond(response().withBody("someBodyOtherBody"));
        mockServerMatcher
            .add(
                expectationToExpireAfter3Seconds.thenRespond(response().withBody("someBody"))
            );

        // then
        assertThat(mockServerMatcher.postProcess(mockServerMatcher.postProcess(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somePath")))), is(expectationToExpireAfter3Seconds));
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(1));

        // when
        SECONDS.sleep(2);

        // then - after 3 seconds
        assertThat(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("someOtherPath")), nullValue());
        assertThat(mockServerMatcher.httpRequestMatchers, empty());
    }

    @Test
    public void shouldNotRemoveNotExpiredExpectationsWhenMatching() {
        // when
        Expectation expectation = new Expectation(request().withPath("somePath"), Times.unlimited(), TimeToLive.exactly(TimeUnit.HOURS, 1L)).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertThat(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somePath")), is(expectation));
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(1));
    }

    @Test
    public void shouldNotRemoveNotExpiredExpectationsWhenNotMatching() {
        // when
        mockServerMatcher.add(new Expectation(request().withPath("somePath"), Times.unlimited(), TimeToLive.exactly(TimeUnit.HOURS, 1L)).thenRespond(response().withBody("someBody")));

        // then
        assertThat(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("someOtherPath")), nullValue());
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(1));
    }

    @Test
    public void shouldRemoveUsedExpectations() {
        // when
        Expectation expectation = new Expectation(request().withPath("somePath"), Times.exactly(1), TimeToLive.unlimited()).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertThat(mockServerMatcher.postProcess(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somePath"))), is(expectation));
        assertThat(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somePath")), nullValue());
        assertThat(mockServerMatcher.httpRequestMatchers, empty());
    }

    @Test
    public void shouldNotRemoveNotUsedExpectations() {
        // when
        Expectation expectation = new Expectation(request().withPath("somePath"), Times.exactly(2), TimeToLive.unlimited()).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertThat(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somePath")), is(expectation));
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(1));
    }

    @Test
    public void shouldNotRemoveAfterTimesComplete() {
        // when
        Expectation expectation = new Expectation(new HttpRequest().withPath("somepath"), Times.exactly(2), TimeToLive.unlimited()).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation);
        Expectation notRemovedExpectation = new Expectation(request().withPath("someOtherPath"), Times.exactly(2), TimeToLive.unlimited());
        mockServerMatcher.add(notRemovedExpectation.thenRespond(response().withBody("someOtherBody")));

        // then
        assertEquals(expectation, mockServerMatcher.postProcess(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath"))));
        assertEquals(expectation, mockServerMatcher.postProcess(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath"))));
        assertEquals(notRemovedExpectation, mockServerMatcher.postProcess(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("someOtherPath"))));
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(1));

        // then
        assertEquals(notRemovedExpectation, mockServerMatcher.postProcess(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("someOtherPath"))));
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(0));
    }

    @Test
    public void shouldNotRemoveAfterTimesCompleteOrExpired() {
        // when
        mockServerMatcher.add(new Expectation(new HttpRequest().withPath("somepath"), Times.exactly(0), TimeToLive.unlimited()).thenRespond(response().withBody("someBody")));
        mockServerMatcher.add(new Expectation(request().withPath("someOtherPath"), Times.unlimited(), TimeToLive.exactly(TimeUnit.MICROSECONDS, 0L)).thenRespond(response().withBody("someOtherBody")));
        mockServerMatcher.add(new Expectation(request().withPath("someOtherPath"), Times.exactly(0), TimeToLive.exactly(TimeUnit.MICROSECONDS, 0L)).thenRespond(response().withBody("someOtherBody")));

        // then
        assertThat(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath")), nullValue());
        assertThat(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("someOtherPath")), nullValue());
        assertThat(mockServerMatcher.httpRequestMatchers, empty());
    }
}
