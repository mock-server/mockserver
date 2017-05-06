package org.mockserver.mock;

import org.hamcrest.core.Is;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.*;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.core.Is.is;

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
    public void shouldRemoveExpiredExpectations() {
        // when
        mockServerMatcher.when(httpRequest.withPath("somePath"), Times.unlimited(), TimeToLive.exactly(TimeUnit.MICROSECONDS, 0L)).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertThat(mockServerMatcher.retrieveAction(new HttpRequest().withPath("somePath")), nullValue());
        assertThat(mockServerMatcher.expectations, empty());
    }

    @Test
    public void shouldNotRemoveNotExpiredExpectations() {
        // when
        mockServerMatcher.when(httpRequest.withPath("somePath"), Times.unlimited(), TimeToLive.exactly(TimeUnit.HOURS, 1L)).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertThat(mockServerMatcher.retrieveAction(new HttpRequest().withPath("somePath")), Is.<Action>is(httpResponse.withBody("someBody")));
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
}
