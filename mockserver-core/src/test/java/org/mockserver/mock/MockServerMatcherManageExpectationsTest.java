package org.mockserver.mock;

import org.hamcrest.core.Is;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.*;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class MockServerMatcherManageExpectationsTest {

    private MockServerMatcher mockServerMatcher;
    private HttpRequest httpRequest;
    private HttpResponse httpResponse;

    @Before
    public void prepareTestFixture() {
        httpRequest = new HttpRequest();
        httpResponse = new HttpResponse();
        mockServerMatcher = new MockServerMatcher();
    }

    @Test
    public void shouldRemoveExpiredExpectationWhenMatching() {
        // when
        mockServerMatcher.when(httpRequest.withPath("somePath"), Times.unlimited(), TimeToLive.exactly(TimeUnit.MICROSECONDS, 0L)).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertThat(mockServerMatcher.retrieveAction(new HttpRequest().withPath("somePath")), nullValue());
        assertThat(mockServerMatcher.expectations, empty());
    }

    @Test
    public void shouldRemoveExpiredExpectationWhenNotMatching() {
        // when
        mockServerMatcher.when(httpRequest.withPath("somePath"), Times.unlimited(), TimeToLive.exactly(TimeUnit.MICROSECONDS, 0L)).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertThat(mockServerMatcher.retrieveAction(new HttpRequest().withPath("someOtherPath")), nullValue());
        assertThat(mockServerMatcher.expectations, empty());
    }

    @Test
    public void shouldRemoveMultipleExpiredExpectations() throws InterruptedException {
        // when
        mockServerMatcher.when(httpRequest.withPath("somePath"), Times.unlimited(), TimeToLive.exactly(TimeUnit.MICROSECONDS, 0L)).thenRespond(httpResponse.withBody("someBody"));
        Expectation expectationToExpireAfter3Seconds = mockServerMatcher.when(httpRequest.withPath("somePath"), Times.unlimited(), TimeToLive.exactly(TimeUnit.SECONDS, 3L));
        expectationToExpireAfter3Seconds.thenRespond(httpResponse.withBody("someBody"));

        // then
        assertThat(mockServerMatcher.retrieveAction(new HttpRequest().withPath("somePath")),
                is(expectationToExpireAfter3Seconds.getAction()));
        assertThat(mockServerMatcher.expectations, contains(expectationToExpireAfter3Seconds));

        // when
        TimeUnit.SECONDS.sleep(3);

        // then - after 3 seconds
        assertThat(mockServerMatcher.retrieveAction(new HttpRequest().withPath("someOtherPath")), nullValue());
        assertThat(mockServerMatcher.expectations, empty());
    }

    @Test
    public void shouldNotRemoveNotExpiredExpectationsWhenMatching() {
        // when
        mockServerMatcher.when(httpRequest.withPath("somePath"), Times.unlimited(), TimeToLive.exactly(TimeUnit.HOURS, 1L)).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertThat(mockServerMatcher.retrieveAction(new HttpRequest().withPath("somePath")), Is.<Action>is(httpResponse.withBody("someBody")));
        assertThat(mockServerMatcher.expectations.size(), is(1));
    }

    @Test
    public void shouldNotRemoveNotExpiredExpectationsWhenNotMatching() {
        // when
        mockServerMatcher.when(httpRequest.withPath("somePath"), Times.unlimited(), TimeToLive.exactly(TimeUnit.HOURS, 1L)).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertThat(mockServerMatcher.retrieveAction(new HttpRequest().withPath("someOtherPath")), nullValue());
        assertThat(mockServerMatcher.expectations.size(), is(1));
    }

    @Test
    public void shouldRemoveUsedExpectations() {
        // when
        mockServerMatcher.when(httpRequest.withPath("somePath"), Times.exactly(1), TimeToLive.unlimited()).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertThat(mockServerMatcher.retrieveAction(new HttpRequest().withPath("somePath")), Is.<Action>is(httpResponse.withBody("someBody")));
        assertThat(mockServerMatcher.retrieveAction(new HttpRequest().withPath("somePath")), nullValue());
        assertThat(mockServerMatcher.expectations, empty());
    }

    @Test
    public void shouldNotRemoveNotUsedExpectations() {
        // when
        mockServerMatcher.when(httpRequest.withPath("somePath"), Times.exactly(2), TimeToLive.unlimited()).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertThat(mockServerMatcher.retrieveAction(new HttpRequest().withPath("somePath")), Is.<Action>is(httpResponse.withBody("someBody")));
        assertThat(mockServerMatcher.expectations.size(), is(1));
    }

    @Test
    public void shouldNotRemoveAfterTimesComplete() {
        // when
        mockServerMatcher.when(new HttpRequest().withPath("somepath"), Times.exactly(2), TimeToLive.unlimited()).thenRespond(httpResponse.withBody("someBody"));
        Expectation notRemovedExpectation = mockServerMatcher.when(httpRequest.withPath("someOtherPath"), Times.exactly(2), TimeToLive.unlimited());
        notRemovedExpectation.thenRespond(response().withBody("someOtherBody"));

        // then
        assertEquals(httpResponse, mockServerMatcher.retrieveAction(new HttpRequest().withPath("somepath")));
        assertEquals(httpResponse, mockServerMatcher.retrieveAction(new HttpRequest().withPath("somepath")));
        assertEquals(response().withBody("someOtherBody"), mockServerMatcher.retrieveAction(new HttpRequest().withPath("someOtherPath")));
        assertThat(mockServerMatcher.expectations, contains(notRemovedExpectation));
    }

    @Test
    public void shouldNotRemoveAfterTimesCompleteOrExpired() {
        // when
        mockServerMatcher.when(new HttpRequest().withPath("somepath"), Times.exactly(0), TimeToLive.unlimited()).thenRespond(httpResponse.withBody("someBody"));
        mockServerMatcher.when(httpRequest.withPath("someOtherPath"), Times.unlimited(), TimeToLive.exactly(TimeUnit.MICROSECONDS, 0L)).thenRespond(response().withBody("someOtherBody"));
        mockServerMatcher.when(httpRequest.withPath("someOtherPath"), Times.exactly(0), TimeToLive.exactly(TimeUnit.MICROSECONDS, 0L)).thenRespond(response().withBody("someOtherBody"));

        // then
        assertThat(mockServerMatcher.retrieveAction(new HttpRequest().withPath("somepath")), nullValue());
        assertThat(mockServerMatcher.retrieveAction(new HttpRequest().withPath("someOtherPath")), nullValue());
        assertThat(mockServerMatcher.expectations, empty());
    }
}
