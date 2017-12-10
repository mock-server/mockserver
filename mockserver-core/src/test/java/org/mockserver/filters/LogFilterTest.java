package org.mockserver.filters;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.log.model.ExpectationMatchLogEntry;
import org.mockserver.log.model.LogEntry;
import org.mockserver.log.model.RequestLogEntry;
import org.mockserver.log.model.RequestResponseLogEntry;
import org.mockserver.logging.LoggingFormatter;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;

import java.util.ArrayList;
import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.mockito.Mockito.mock;
import static org.mockserver.filters.LogFilter.REQUEST_LOG_TYPES;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class LogFilterTest {

    private LogFilter logFilter;

    private LoggingFormatter mockLogFormatter;

    @Before
    public void setupTestFixture() {
        mockLogFormatter = mock(LoggingFormatter.class);
        logFilter = new LogFilter(mockLogFormatter);
    }

    @Test
    public void shouldClearWithNullRequestMatcher() {
        // given
        logFilter.onRequest(new RequestLogEntry(request("request_one")));
        logFilter.onRequest(new RequestLogEntry(request("request_two")));
        logFilter.onRequest(new RequestResponseLogEntry(request("request_one"), response("response_one")));
        logFilter.onRequest(new RequestResponseLogEntry(request("request_three"), response("response_three")));
        logFilter.onRequest(new ExpectationMatchLogEntry(request("request_one"), new Expectation(request("request_one")).thenRespond(response("response_two"))));
        logFilter.onRequest(new ExpectationMatchLogEntry(request("request_four"), new Expectation(request("request_four")).thenRespond(response("response_four"))));

        // when
        logFilter.clear(null);

        // then
        assertThat(logFilter.retrieveRequests(null), empty());
        assertThat(logFilter.retrieveLogEntries(null), empty());
        assertThat(logFilter.retrieveExpectations(null), empty());
    }

    @Test
    public void shouldClearWithRequestMatcher() {
        // given
        HttpRequest request = request("request_one");
        logFilter.onRequest(new RequestLogEntry(request("request_one")));
        logFilter.onRequest(new RequestLogEntry(request("request_two")));
        logFilter.onRequest(new RequestResponseLogEntry(request("request_one"), response("response_one")));
        logFilter.onRequest(new RequestResponseLogEntry(request("request_three"), response("response_three")));
        logFilter.onRequest(new ExpectationMatchLogEntry(request("request_one"), new Expectation(request("request_one")).thenRespond(response("response_two"))));
        logFilter.onRequest(new ExpectationMatchLogEntry(request("request_four"), new Expectation(request("request_four")).thenRespond(response("response_four"))));

        // when
        logFilter.clear(request);

        // then
        assertThat(logFilter.retrieveRequests(null), contains(
                request("request_two"),
                request("request_three"),
                request("request_four")
        ));
        assertThat(logFilter.retrieveLogEntries(null), contains(
                new RequestLogEntry(request("request_two")),
                new RequestResponseLogEntry(request("request_three"), response("response_three")),
                new ExpectationMatchLogEntry(request("request_four"), new Expectation(request("request_four")).thenRespond(response("response_four")))
        ));
        assertThat(logFilter.retrieveExpectations(null), contains(
                new Expectation(request("request_three"), Times.once(), TimeToLive.unlimited()).thenRespond(response("response_three")),
                new Expectation(request("request_four")).thenRespond(response("response_four"))
        ));
    }

    @Test
    public void shouldReset() {
        // given
        logFilter.onRequest(new RequestLogEntry(request("request_one")));
        logFilter.onRequest(new RequestLogEntry(request("request_two")));
        logFilter.onRequest(new RequestResponseLogEntry(request("request_one"), response("response_one")));
        logFilter.onRequest(new RequestResponseLogEntry(request("request_three"), response("response_three")));
        logFilter.onRequest(new ExpectationMatchLogEntry(request("request_one"), new Expectation(request("request_one")).thenRespond(response("response_two"))));
        logFilter.onRequest(new ExpectationMatchLogEntry(request("request_four"), new Expectation(request("request_four")).thenRespond(response("response_four"))));

        // when
        logFilter.reset();

        // then
        assertThat(logFilter.retrieveRequests(null), empty());
        assertThat(logFilter.retrieveLogEntries(null), empty());
        assertThat(logFilter.retrieveExpectations(null), empty());
    }

    @Test
    public void shouldRetrieveRecordedRequests() {
        // when
        logFilter.onRequest(new RequestLogEntry(request("request_one")));
        logFilter.onRequest(new RequestLogEntry(request("request_two")));
        logFilter.onRequest(new RequestResponseLogEntry(request("request_one"), response("response_one")));
        logFilter.onRequest(new RequestResponseLogEntry(request("request_three"), response("response_three")));
        logFilter.onRequest(new ExpectationMatchLogEntry(request("request_one"), new Expectation(request("request_one")).thenRespond(response("response_two"))));
        logFilter.onRequest(new ExpectationMatchLogEntry(request("request_four"), new Expectation(request("request_four")).thenRespond(response("response_four"))));

        // then
        assertThat(logFilter.retrieveRequests(null), contains(
                request("request_one"),
                request("request_two"),
                request("request_one"),
                request("request_three"),
                request("request_one"),
                request("request_four")
        ));
    }

    @Test
    public void shouldRetrieveRecordedExpectations() {
        // when
        logFilter.onRequest(new RequestLogEntry(request("request_one")));
        logFilter.onRequest(new RequestLogEntry(request("request_two")));
        logFilter.onRequest(new RequestResponseLogEntry(request("request_one"), response("response_one")));
        logFilter.onRequest(new RequestResponseLogEntry(request("request_three"), response("response_three")));
        logFilter.onRequest(new ExpectationMatchLogEntry(request("request_one"), new Expectation(request("request_one")).thenRespond(response("response_two"))));
        logFilter.onRequest(new ExpectationMatchLogEntry(request("request_four"), new Expectation(request("request_four")).thenRespond(response("response_four"))));

        // then
        assertThat(logFilter.retrieveExpectations(null), contains(
                new Expectation(request("request_one"), Times.once(), TimeToLive.unlimited()).thenRespond(response("response_one")),
                new Expectation(request("request_three"), Times.once(), TimeToLive.unlimited()).thenRespond(response("response_three")),
                new Expectation(request("request_one")).thenRespond(response("response_two")),
                new Expectation(request("request_four")).thenRespond(response("response_four"))
        ));
    }

    @Test
    public void shouldRetrieveLogEnteries() {
        // when
        logFilter.onRequest(new RequestLogEntry(request("request_one")));
        logFilter.onRequest(new RequestLogEntry(request("request_two")));
        logFilter.onRequest(new RequestResponseLogEntry(request("request_one"), response("response_one")));
        logFilter.onRequest(new RequestResponseLogEntry(request("request_three"), response("response_three")));
        logFilter.onRequest(new ExpectationMatchLogEntry(request("request_one"), new Expectation(request("request_one")).thenRespond(response("response_two"))));
        logFilter.onRequest(new ExpectationMatchLogEntry(request("request_four"), new Expectation(request("request_four")).thenRespond(response("response_four"))));

        // then
        assertThat(logFilter.retrieveLogEntries(null), contains(
                new RequestLogEntry(request("request_one")),
                new RequestLogEntry(request("request_two")),
                new RequestResponseLogEntry(request("request_one"), response("response_one")),
                new RequestResponseLogEntry(request("request_three"), response("response_three")),
                new ExpectationMatchLogEntry(request("request_one"), new Expectation(request("request_one")).thenRespond(response("response_two"))),
                new ExpectationMatchLogEntry(request("request_four"), new Expectation(request("request_four")).thenRespond(response("response_four")))
        ));
    }

}
