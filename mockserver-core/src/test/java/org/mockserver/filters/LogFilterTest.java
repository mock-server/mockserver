package org.mockserver.filters;

import com.google.common.base.Function;
import org.hamcrest.collection.IsIterableContainingInOrder;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.log.model.*;
import org.mockserver.logging.LoggingFormatter;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.mockito.Mockito.mock;
import static org.mockserver.filters.LogFilter.*;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class LogFilterTest {

    private static Function<LogEntry, LogEntry> logEntryToLogEntry = new Function<LogEntry, LogEntry>() {
        public LogEntry apply(LogEntry logEntry) {
            return logEntry;
        }
    };
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
        logFilter.onRequest(new MessageLogEntry(request("request_one"), "message_one"));
        logFilter.onRequest(new RequestLogEntry(request("request_one")));
        logFilter.onRequest(new MessageLogEntry(request("request_two"), "message_two"));
        logFilter.onRequest(new RequestLogEntry(request("request_two")));
        logFilter.onRequest(new MessageLogEntry(request("request_three"), "message_three"));
        logFilter.onRequest(new RequestResponseLogEntry(request("request_one"), response("response_one")));
        logFilter.onRequest(new MessageLogEntry(request("request_four"), "message_four"));
        logFilter.onRequest(new RequestResponseLogEntry(request("request_three"), response("response_three")));
        logFilter.onRequest(new MessageLogEntry(request("request_five"), "message_five"));
        logFilter.onRequest(new ExpectationMatchLogEntry(request("request_one"), new Expectation(request("request_one")).thenRespond(response("response_two"))));
        logFilter.onRequest(new MessageLogEntry(request("request_six"), "message_six"));
        logFilter.onRequest(new ExpectationMatchLogEntry(request("request_four"), new Expectation(request("request_four")).thenRespond(response("response_four"))));
        logFilter.onRequest(new MessageLogEntry(request("request_seven"), "message_seven"));

        // when
        logFilter.clear(null);

        // then
        assertThat(logFilter.retrieveLogEntries(null, requestLogPredicate, logEntryToLogEntry), empty());
        assertThat(logFilter.retrieveRequests(null), empty());
        assertThat(logFilter.retrieveLogEntries(null, expectationLogPredicate, logEntryToLogEntry), empty());
        assertThat(logFilter.retrieveExpectations(null), empty());
        assertThat(logFilter.retrieveLogEntries(null, messageLogPredicate, logEntryToLogEntry), empty());
        assertThat(logFilter.retrieveMessages(null), empty());
    }

    @Test
    public void shouldClearWithRequestMatcher() {
        // given
        logFilter.onRequest(new MessageLogEntry(request("request_one"), "message_one"));
        logFilter.onRequest(new RequestLogEntry(request("request_one")));
        logFilter.onRequest(new MessageLogEntry(request("request_two"), "message_two"));
        logFilter.onRequest(new RequestLogEntry(request("request_two")));
        logFilter.onRequest(new MessageLogEntry(request("request_three"), "message_three"));
        logFilter.onRequest(new RequestResponseLogEntry(request("request_one"), response("response_one")));
        logFilter.onRequest(new MessageLogEntry(request("request_four"), "message_four"));
        logFilter.onRequest(new RequestResponseLogEntry(request("request_three"), response("response_three")));
        logFilter.onRequest(new MessageLogEntry(request("request_five"), "message_five"));
        logFilter.onRequest(new ExpectationMatchLogEntry(request("request_one"), new Expectation(request("request_one")).thenRespond(response("response_two"))));
        logFilter.onRequest(new MessageLogEntry(request("request_six"), "message_six"));
        logFilter.onRequest(new ExpectationMatchLogEntry(request("request_four"), new Expectation(request("request_four")).thenRespond(response("response_four"))));
        logFilter.onRequest(new MessageLogEntry(request("request_seven"), "message_seven"));

        // when
        logFilter.clear(request("request_one"));

        // then
        assertThat(logFilter.retrieveRequests(null), contains(
            request("request_two"),
            request("request_three"),
            request("request_four")
        ));
        assertThat(logFilter.retrieveLogEntries(null, requestLogPredicate, logEntryToLogEntry), contains(
            new RequestLogEntry(request("request_two")),
            new RequestResponseLogEntry(request("request_three"), response("response_three")),
            new ExpectationMatchLogEntry(request("request_four"), new Expectation(request("request_four")).thenRespond(response("response_four")))
        ));
        assertThat(logFilter.retrieveExpectations(null), contains(
            new Expectation(request("request_three"), Times.once(), TimeToLive.unlimited()).thenRespond(response("response_three")),
            new Expectation(request("request_four")).thenRespond(response("response_four"))
        ));
        assertThat(logFilter.retrieveLogEntries(null, expectationLogPredicate, logEntryToLogEntry), IsIterableContainingInOrder.<LogEntry>contains(
            new RequestResponseLogEntry(request("request_three"), response("response_three")),
            new ExpectationMatchLogEntry(request("request_four"), new Expectation(request("request_four")).thenRespond(response("response_four")))
        ));
        assertThat(logFilter.retrieveMessages(null), contains(
            "message_two",
            "message_three",
            "message_four",
            "message_five",
            "message_six",
            "message_seven"
        ));
        assertThat(logFilter.retrieveLogEntries(null, messageLogPredicate, logEntryToLogEntry), IsIterableContainingInOrder.<LogEntry>contains(
            new MessageLogEntry(request("request_two"), "message_two"),
            new MessageLogEntry(request("request_three"), "message_three"),
            new MessageLogEntry(request("request_four"), "message_four"),
            new MessageLogEntry(request("request_five"), "message_five"),
            new MessageLogEntry(request("request_six"), "message_six"),
            new MessageLogEntry(request("request_seven"), "message_seven")
        ));
    }

    @Test
    public void shouldReset() {
        // given
        logFilter.onRequest(new MessageLogEntry(request("request_one"), "message_one"));
        logFilter.onRequest(new RequestLogEntry(request("request_one")));
        logFilter.onRequest(new MessageLogEntry(request("request_two"), "message_two"));
        logFilter.onRequest(new RequestLogEntry(request("request_two")));
        logFilter.onRequest(new MessageLogEntry(request("request_three"), "message_three"));
        logFilter.onRequest(new RequestResponseLogEntry(request("request_one"), response("response_one")));
        logFilter.onRequest(new MessageLogEntry(request("request_four"), "message_four"));
        logFilter.onRequest(new RequestResponseLogEntry(request("request_three"), response("response_three")));
        logFilter.onRequest(new MessageLogEntry(request("request_five"), "message_five"));
        logFilter.onRequest(new ExpectationMatchLogEntry(request("request_one"), new Expectation(request("request_one")).thenRespond(response("response_two"))));
        logFilter.onRequest(new MessageLogEntry(request("request_six"), "message_six"));
        logFilter.onRequest(new ExpectationMatchLogEntry(request("request_four"), new Expectation(request("request_four")).thenRespond(response("response_four"))));
        logFilter.onRequest(new MessageLogEntry(request("request_seven"), "message_seven"));

        // when
        logFilter.reset();

        // then
        assertThat(logFilter.retrieveLogEntries(null, requestLogPredicate, logEntryToLogEntry), empty());
        assertThat(logFilter.retrieveRequests(null), empty());
        assertThat(logFilter.retrieveLogEntries(null, expectationLogPredicate, logEntryToLogEntry), empty());
        assertThat(logFilter.retrieveExpectations(null), empty());
        assertThat(logFilter.retrieveLogEntries(null, messageLogPredicate, logEntryToLogEntry), empty());
        assertThat(logFilter.retrieveMessages(null), empty());
    }

    @Test
    public void shouldRetrieveRecordedRequests() {
        // when
        logFilter.onRequest(new MessageLogEntry(request("request_one"), "message_one"));
        logFilter.onRequest(new RequestLogEntry(request("request_one")));
        logFilter.onRequest(new MessageLogEntry(request("request_two"), "message_two"));
        logFilter.onRequest(new RequestLogEntry(request("request_two")));
        logFilter.onRequest(new MessageLogEntry(request("request_three"), "message_three"));
        logFilter.onRequest(new RequestResponseLogEntry(request("request_one"), response("response_one")));
        logFilter.onRequest(new MessageLogEntry(request("request_four"), "message_four"));
        logFilter.onRequest(new RequestResponseLogEntry(request("request_three"), response("response_three")));
        logFilter.onRequest(new MessageLogEntry(request("request_five"), "message_five"));
        logFilter.onRequest(new ExpectationMatchLogEntry(request("request_one"), new Expectation(request("request_one")).thenRespond(response("response_two"))));
        logFilter.onRequest(new MessageLogEntry(request("request_six"), "message_six"));
        logFilter.onRequest(new ExpectationMatchLogEntry(request("request_four"), new Expectation(request("request_four")).thenRespond(response("response_four"))));
        logFilter.onRequest(new MessageLogEntry(request("request_seven"), "message_seven"));

        // then
        assertThat(logFilter.retrieveRequests(null), contains(
            request("request_one"),
            request("request_two"),
            request("request_one"),
            request("request_three"),
            request("request_one"),
            request("request_four")
        ));
        assertThat(logFilter.retrieveLogEntries(null, requestLogPredicate, logEntryToLogEntry), contains(
            new RequestLogEntry(request("request_one")),
            new RequestLogEntry(request("request_two")),
            new RequestResponseLogEntry(request("request_one"), response("response_one")),
            new RequestResponseLogEntry(request("request_three"), response("response_three")),
            new ExpectationMatchLogEntry(request("request_one"), new Expectation(request("request_one")).thenRespond(response("response_two"))),
            new ExpectationMatchLogEntry(request("request_four"), new Expectation(request("request_four")).thenRespond(response("response_four")))
        ));
    }

    @Test
    public void shouldRetrieveRecordedExpectations() {
        // when
        logFilter.onRequest(new MessageLogEntry(request("request_one"), "message_one"));
        logFilter.onRequest(new RequestLogEntry(request("request_one")));
        logFilter.onRequest(new MessageLogEntry(request("request_two"), "message_two"));
        logFilter.onRequest(new RequestLogEntry(request("request_two")));
        logFilter.onRequest(new MessageLogEntry(request("request_three"), "message_three"));
        logFilter.onRequest(new RequestResponseLogEntry(request("request_one"), response("response_one")));
        logFilter.onRequest(new MessageLogEntry(request("request_four"), "message_four"));
        logFilter.onRequest(new RequestResponseLogEntry(request("request_three"), response("response_three")));
        logFilter.onRequest(new MessageLogEntry(request("request_five"), "message_five"));
        logFilter.onRequest(new ExpectationMatchLogEntry(request("request_one"), new Expectation(request("request_one")).thenRespond(response("response_two"))));
        logFilter.onRequest(new MessageLogEntry(request("request_six"), "message_six"));
        logFilter.onRequest(new ExpectationMatchLogEntry(request("request_four"), new Expectation(request("request_four")).thenRespond(response("response_four"))));
        logFilter.onRequest(new MessageLogEntry(request("request_seven"), "message_seven"));

        // then
        assertThat(logFilter.retrieveExpectations(null), contains(
            new Expectation(request("request_one"), Times.once(), TimeToLive.unlimited()).thenRespond(response("response_one")),
            new Expectation(request("request_three"), Times.once(), TimeToLive.unlimited()).thenRespond(response("response_three")),
            new Expectation(request("request_one")).thenRespond(response("response_two")),
            new Expectation(request("request_four")).thenRespond(response("response_four"))
        ));
        assertThat(logFilter.retrieveLogEntries(null, expectationLogPredicate, logEntryToLogEntry), IsIterableContainingInOrder.<LogEntry>contains(
            new RequestResponseLogEntry(request("request_one"), response("response_one")),
            new RequestResponseLogEntry(request("request_three"), response("response_three")),
            new ExpectationMatchLogEntry(request("request_one"), new Expectation(request("request_one")).thenRespond(response("response_two"))),
            new ExpectationMatchLogEntry(request("request_four"), new Expectation(request("request_four")).thenRespond(response("response_four")))
        ));
    }

    @Test
    public void shouldRetrieveMessages() {
        // when
        logFilter.onRequest(new MessageLogEntry(request("request_one"), "message_one"));
        logFilter.onRequest(new RequestLogEntry(request("request_one")));
        logFilter.onRequest(new MessageLogEntry(request("request_two"), "message_two"));
        logFilter.onRequest(new RequestLogEntry(request("request_two")));
        logFilter.onRequest(new MessageLogEntry(request("request_three"), "message_three"));
        logFilter.onRequest(new RequestResponseLogEntry(request("request_one"), response("response_one")));
        logFilter.onRequest(new MessageLogEntry(request("request_four"), "message_four"));
        logFilter.onRequest(new RequestResponseLogEntry(request("request_three"), response("response_three")));
        logFilter.onRequest(new MessageLogEntry(request("request_five"), "message_five"));
        logFilter.onRequest(new ExpectationMatchLogEntry(request("request_one"), new Expectation(request("request_one")).thenRespond(response("response_two"))));
        logFilter.onRequest(new MessageLogEntry(request("request_six"), "message_six"));
        logFilter.onRequest(new ExpectationMatchLogEntry(request("request_four"), new Expectation(request("request_four")).thenRespond(response("response_four"))));
        logFilter.onRequest(new MessageLogEntry(request("request_seven"), "message_seven"));

        // then
        assertThat(logFilter.retrieveMessages(null), contains(
            "message_one",
            "message_two",
            "message_three",
            "message_four",
            "message_five",
            "message_six",
            "message_seven"
        ));
        assertThat(logFilter.retrieveLogEntries(null, messageLogPredicate, logEntryToLogEntry), IsIterableContainingInOrder.<LogEntry>contains(
            new MessageLogEntry(request("request_one"), "message_one"),
            new MessageLogEntry(request("request_two"), "message_two"),
            new MessageLogEntry(request("request_three"), "message_three"),
            new MessageLogEntry(request("request_four"), "message_four"),
            new MessageLogEntry(request("request_five"), "message_five"),
            new MessageLogEntry(request("request_six"), "message_six"),
            new MessageLogEntry(request("request_seven"), "message_seven")
        ));
    }

    @Test
    public void shouldRetrieveLogEntries() {
        // when
        logFilter.onRequest(new RequestLogEntry(request("request_one")));
        logFilter.onRequest(new RequestLogEntry(request("request_two")));
        logFilter.onRequest(new RequestResponseLogEntry(request("request_one"), response("response_one")));
        logFilter.onRequest(new RequestResponseLogEntry(request("request_three"), response("response_three")));
        logFilter.onRequest(new ExpectationMatchLogEntry(request("request_one"), new Expectation(request("request_one")).thenRespond(response("response_two"))));
        logFilter.onRequest(new ExpectationMatchLogEntry(request("request_four"), new Expectation(request("request_four")).thenRespond(response("response_four"))));

        // then
        assertThat(logFilter.retrieveLogEntries(null, requestLogPredicate, logEntryToLogEntry), contains(
            new RequestLogEntry(request("request_one")),
            new RequestLogEntry(request("request_two")),
            new RequestResponseLogEntry(request("request_one"), response("response_one")),
            new RequestResponseLogEntry(request("request_three"), response("response_three")),
            new ExpectationMatchLogEntry(request("request_one"), new Expectation(request("request_one")).thenRespond(response("response_two"))),
            new ExpectationMatchLogEntry(request("request_four"), new Expectation(request("request_four")).thenRespond(response("response_four")))
        ));
    }

}
