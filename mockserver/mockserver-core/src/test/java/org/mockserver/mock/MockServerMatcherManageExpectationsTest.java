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
import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause.API;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class MockServerMatcherManageExpectationsTest {

    private RequestMatchers requestMatchers;

    @Before
    public void prepareTestFixture() {
        WebSocketClientRegistry webSocketClientRegistry = mock(WebSocketClientRegistry.class);
        requestMatchers = new RequestMatchers(configuration(), new MockServerLogger(), new Scheduler(configuration(), new MockServerLogger(), true), webSocketClientRegistry);
    }

    @Test
    public void shouldRemoveExpiredExpectationWhenMatching() {
        // when
        requestMatchers.add(new Expectation(request().withPath("somePath"), Times.unlimited(), TimeToLive.exactly(TimeUnit.MICROSECONDS, 0L), 0).thenRespond(response().withBody("someBody")), API);

        // then
        assertThat(requestMatchers.postProcess(requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somePath"))), nullValue());
        assertThat(requestMatchers.httpRequestMatchers.toSortedList(), empty());
    }

    @Test
    public void shouldRemoveExpiredExpectationWhenNotMatching() {
        // when
        requestMatchers.add(new Expectation(request().withPath("somePath"), Times.unlimited(), TimeToLive.exactly(TimeUnit.MICROSECONDS, 0L), 0).thenRespond(response().withBody("someBody")), API);

        // then
        assertThat(requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("someOtherPath")), nullValue());
        assertThat(requestMatchers.httpRequestMatchers.toSortedList(), empty());
    }

    @Test
    public void shouldRemoveMultipleExpiredExpectations() throws InterruptedException {
        // when
        requestMatchers.add(new Expectation(request().withPath("somePath"), Times.unlimited(), TimeToLive.exactly(TimeUnit.MICROSECONDS, 0L), 0)
            .thenRespond(response().withBody("someBody")), API);
        Expectation expectationToExpireAfter3Seconds =
            new Expectation(request().withPath("somePath"), Times.unlimited(), TimeToLive.exactly(MILLISECONDS, 1500L), 0)
                .thenRespond(response().withBody("someBodyOtherBody"));
        requestMatchers.add(expectationToExpireAfter3Seconds.thenRespond(response().withBody("someBody")), API);

        // then
        assertThat(requestMatchers.postProcess(requestMatchers.postProcess(requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somePath")))), is(expectationToExpireAfter3Seconds));
        assertThat(requestMatchers.httpRequestMatchers.size(), is(1));

        // when
        SECONDS.sleep(2);

        // then - after 3 seconds
        assertThat(requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("someOtherPath")), nullValue());
        assertThat(requestMatchers.httpRequestMatchers.toSortedList(), empty());
    }

    @Test
    public void shouldNotRemoveNotExpiredExpectationsWhenMatching() {
        // when
        Expectation expectation = new Expectation(request().withPath("somePath"), Times.unlimited(), TimeToLive.exactly(TimeUnit.HOURS, 1L), 0).thenRespond(response().withBody("someBody"));
        requestMatchers.add(expectation, API);

        // then
        assertThat(requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somePath")), is(expectation));
        assertThat(requestMatchers.httpRequestMatchers.size(), is(1));
    }

    @Test
    public void shouldNotRemoveNotExpiredExpectationsWhenNotMatching() {
        // when
        requestMatchers.add(new Expectation(request().withPath("somePath"), Times.unlimited(), TimeToLive.exactly(TimeUnit.HOURS, 1L), 0).thenRespond(response().withBody("someBody")), API);

        // then
        assertThat(requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("someOtherPath")), nullValue());
        assertThat(requestMatchers.httpRequestMatchers.size(), is(1));
    }

    @Test
    public void shouldRemoveUsedExpectations() {
        // when
        Expectation expectation = new Expectation(request().withPath("somePath"), Times.exactly(1), TimeToLive.unlimited(), 0).thenRespond(response().withBody("someBody"));
        requestMatchers.add(expectation, API);

        // then
        assertThat(requestMatchers.postProcess(requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somePath"))), is(expectation));
        assertThat(requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somePath")), nullValue());
        assertThat(requestMatchers.httpRequestMatchers.toSortedList(), empty());
    }

    @Test
    public void shouldNotRemoveNotUsedExpectations() {
        // when
        Expectation expectation = new Expectation(request().withPath("somePath"), Times.exactly(2), TimeToLive.unlimited(), 0).thenRespond(response().withBody("someBody"));
        requestMatchers.add(expectation, API);

        // then
        assertThat(requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somePath")), is(expectation));
        assertThat(requestMatchers.httpRequestMatchers.size(), is(1));
    }

    @Test
    public void shouldNotRemoveAfterTimesComplete() {
        // when
        Expectation expectation = new Expectation(new HttpRequest().withPath("somepath"), Times.exactly(2), TimeToLive.unlimited(), 0).thenRespond(response().withBody("someBody"));
        requestMatchers.add(expectation, API);
        Expectation notRemovedExpectation = new Expectation(request().withPath("someOtherPath"), Times.exactly(2), TimeToLive.unlimited(), 0);
        requestMatchers.add(notRemovedExpectation.thenRespond(response().withBody("someOtherBody")), API);

        // then
        assertEquals(expectation, requestMatchers.postProcess(requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somepath"))));
        assertEquals(expectation, requestMatchers.postProcess(requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somepath"))));
        assertEquals(notRemovedExpectation, requestMatchers.postProcess(requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("someOtherPath"))));
        assertThat(requestMatchers.httpRequestMatchers.size(), is(1));

        // then
        assertEquals(notRemovedExpectation, requestMatchers.postProcess(requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("someOtherPath"))));
        assertThat(requestMatchers.httpRequestMatchers.size(), is(0));
    }

    @Test
    public void shouldNotRemoveAfterTimesCompleteOrExpired() {
        // when
        requestMatchers.add(new Expectation(new HttpRequest().withPath("somepath"), Times.exactly(0), TimeToLive.unlimited(), 0).thenRespond(response().withBody("someBody")), API);
        requestMatchers.add(new Expectation(request().withPath("someOtherPath"), Times.unlimited(), TimeToLive.exactly(TimeUnit.MICROSECONDS, 0L), 0).thenRespond(response().withBody("someOtherBody")), API);
        requestMatchers.add(new Expectation(request().withPath("someOtherPath"), Times.exactly(0), TimeToLive.exactly(TimeUnit.MICROSECONDS, 0L), 0).thenRespond(response().withBody("someOtherBody")), API);

        // then
        assertThat(requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somepath")), nullValue());
        assertThat(requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("someOtherPath")), nullValue());
        assertThat(requestMatchers.httpRequestMatchers.toSortedList(), empty());
    }
}
