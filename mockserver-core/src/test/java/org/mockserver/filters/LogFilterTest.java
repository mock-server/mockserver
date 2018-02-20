package org.mockserver.filters;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.hamcrest.collection.IsIterableContainingInOrder;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.log.model.*;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.scheduler.Scheduler;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.core.StringContains.containsString;
import static org.mockito.Mockito.mock;
import static org.mockserver.filters.MockServerEventLog.*;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class LogFilterTest {

    private static Function<LogEntry, LogEntry> logEntryToLogEntry = new Function<LogEntry, LogEntry>() {
        public LogEntry apply(LogEntry logEntry) {
            return logEntry;
        }
    };
    private MockServerEventLog logFilter;
    private MockServerLogger mockLogFormatter;
    private Scheduler scheduler;

    @Before
    public void setupTestFixture() {
        scheduler = mock(Scheduler.class);
        mockLogFormatter = mock(MockServerLogger.class);
        logFilter = new MockServerEventLog(mockLogFormatter, scheduler);
    }

    @Test
    public void shouldClearWithNullRequestMatcher() {
        // given
        logFilter.add(new MessageLogEntry(null, request("request_one"), "message_one"));
        logFilter.add(new RequestLogEntry(request("request_one")));
        logFilter.add(new MessageLogEntry(null, request("request_two"), "message_two"));
        logFilter.add(new RequestLogEntry(request("request_two")));
        logFilter.add(new MessageLogEntry(null, request("request_three"), "message_three"));
        logFilter.add(new RequestResponseLogEntry(request("request_one"), response("response_one")));
        logFilter.add(new MessageLogEntry(null, request("request_four"), "message_four"));
        logFilter.add(new RequestResponseLogEntry(request("request_three"), response("response_three")));
        logFilter.add(new MessageLogEntry(null, request("request_five"), "message_five"));
        logFilter.add(new ExpectationMatchLogEntry(request("request_one"), new Expectation(request("request_one")).thenRespond(response("response_two"))));
        logFilter.add(new MessageLogEntry(null, request("request_six"), "message_six"));
        logFilter.add(new ExpectationMatchLogEntry(request("request_four"), new Expectation(request("request_four")).thenRespond(response("response_four"))));
        logFilter.add(new MessageLogEntry(null, request("request_seven"), "message_seven"));

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
        logFilter.add(new MessageLogEntry(null, request("request_one"), "message_one"));
        logFilter.add(new RequestLogEntry(request("request_one")));
        logFilter.add(new MessageLogEntry(null, request("request_two"), "message_two"));
        logFilter.add(new RequestLogEntry(request("request_two")));
        logFilter.add(new MessageLogEntry(null, request("request_three"), "message_three"));
        logFilter.add(new RequestResponseLogEntry(request("request_one"), response("response_one")));
        logFilter.add(new MessageLogEntry(null, request("request_four"), "message_four"));
        logFilter.add(new RequestResponseLogEntry(request("request_three"), response("response_three")));
        logFilter.add(new MessageLogEntry(null, request("request_five"), "message_five"));
        logFilter.add(new ExpectationMatchLogEntry(request("request_one"), new Expectation(request("request_one")).thenRespond(response("response_two"))));
        logFilter.add(new MessageLogEntry(null, request("request_six"), "message_six"));
        logFilter.add(new ExpectationMatchLogEntry(request("request_four"), new Expectation(request("request_four")).thenRespond(response("response_four"))));
        logFilter.add(new MessageLogEntry(null, request("request_seven"), "message_seven"));

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
            new Expectation(request("request_three"), Times.once(), TimeToLive.unlimited()).thenRespond(response("response_three"))
        ));
        assertThat(logFilter.retrieveLogEntries(null, expectationLogPredicate, logEntryToLogEntry), IsIterableContainingInOrder.<LogEntry>contains(
            new RequestResponseLogEntry(request("request_three"), response("response_three"))
        ));
        List<String> logMessages = Lists.transform(logFilter.retrieveMessages(null), new Function<MessageLogEntry, String>() {
            public String apply(MessageLogEntry input) {
                return input.getMessage();
            }
        });
        assertThat(logMessages, contains(
            containsString("message_two"),
            containsString("message_three"),
            containsString("message_four"),
            containsString("message_five"),
            containsString("message_six"),
            containsString("message_seven")
        ));
        List<LogEntry> logEntries = logFilter.retrieveLogEntries(null, messageLogPredicate, logEntryToLogEntry);
        List<MessageLogEntry> messageLogEntries = Arrays.asList(
            new MessageLogEntry(null, request("request_two"), "message_two"),
            new MessageLogEntry(null, request("request_three"), "message_three"),
            new MessageLogEntry(null, request("request_four"), "message_four"),
            new MessageLogEntry(null, request("request_five"), "message_five"),
            new MessageLogEntry(null, request("request_six"), "message_six"),
            new MessageLogEntry(null, request("request_seven"), "message_seven")
        );
        for (int i = 0; i < logEntries.size(); i++) {
            MessageLogEntry messageLogEntry = (MessageLogEntry) logEntries.get(i);
            assertThat(messageLogEntry.getHttpRequests(), is(messageLogEntries.get(i).getHttpRequests()));
            assertThat(messageLogEntry.getMessage(), endsWith(messageLogEntries.get(i).getMessage()));
        }
    }

    @Test
    public void shouldReset() {
        // given
        logFilter.add(new MessageLogEntry(null, request("request_one"), "message_one"));
        logFilter.add(new RequestLogEntry(request("request_one")));
        logFilter.add(new MessageLogEntry(null, request("request_two"), "message_two"));
        logFilter.add(new RequestLogEntry(request("request_two")));
        logFilter.add(new MessageLogEntry(null, request("request_three"), "message_three"));
        logFilter.add(new RequestResponseLogEntry(request("request_one"), response("response_one")));
        logFilter.add(new MessageLogEntry(null, request("request_four"), "message_four"));
        logFilter.add(new RequestResponseLogEntry(request("request_three"), response("response_three")));
        logFilter.add(new MessageLogEntry(null, request("request_five"), "message_five"));
        logFilter.add(new ExpectationMatchLogEntry(request("request_one"), new Expectation(request("request_one")).thenRespond(response("response_two"))));
        logFilter.add(new MessageLogEntry(null, request("request_six"), "message_six"));
        logFilter.add(new ExpectationMatchLogEntry(request("request_four"), new Expectation(request("request_four")).thenRespond(response("response_four"))));
        logFilter.add(new MessageLogEntry(null, request("request_seven"), "message_seven"));

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
        logFilter.add(new MessageLogEntry(null, request("request_one"), "message_one"));
        logFilter.add(new RequestLogEntry(request("request_one")));
        logFilter.add(new MessageLogEntry(null, request("request_two"), "message_two"));
        logFilter.add(new RequestLogEntry(request("request_two")));
        logFilter.add(new MessageLogEntry(null, request("request_three"), "message_three"));
        logFilter.add(new RequestResponseLogEntry(request("request_one"), response("response_one")));
        logFilter.add(new MessageLogEntry(null, request("request_four"), "message_four"));
        logFilter.add(new RequestResponseLogEntry(request("request_three"), response("response_three")));
        logFilter.add(new MessageLogEntry(null, request("request_five"), "message_five"));
        logFilter.add(new ExpectationMatchLogEntry(request("request_one"), new Expectation(request("request_one")).thenRespond(response("response_two"))));
        logFilter.add(new MessageLogEntry(null, request("request_six"), "message_six"));
        logFilter.add(new ExpectationMatchLogEntry(request("request_four"), new Expectation(request("request_four")).thenRespond(response("response_four"))));
        logFilter.add(new MessageLogEntry(null, request("request_seven"), "message_seven"));

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
        logFilter.add(new MessageLogEntry(null, request("request_one"), "message_one"));
        logFilter.add(new RequestLogEntry(request("request_one")));
        logFilter.add(new MessageLogEntry(null, request("request_two"), "message_two"));
        logFilter.add(new RequestLogEntry(request("request_two")));
        logFilter.add(new MessageLogEntry(null, request("request_three"), "message_three"));
        logFilter.add(new RequestResponseLogEntry(request("request_one"), response("response_one")));
        logFilter.add(new MessageLogEntry(null, request("request_four"), "message_four"));
        logFilter.add(new RequestResponseLogEntry(request("request_three"), response("response_three")));
        logFilter.add(new MessageLogEntry(null, request("request_five"), "message_five"));
        logFilter.add(new ExpectationMatchLogEntry(request("request_one"), new Expectation(request("request_one")).thenRespond(response("response_two"))));
        logFilter.add(new MessageLogEntry(null, request("request_six"), "message_six"));
        logFilter.add(new ExpectationMatchLogEntry(request("request_four"), new Expectation(request("request_four")).thenRespond(response("response_four"))));
        logFilter.add(new MessageLogEntry(null, request("request_seven"), "message_seven"));

        // then
        assertThat(logFilter.retrieveExpectations(null), contains(
            new Expectation(request("request_one"), Times.once(), TimeToLive.unlimited()).thenRespond(response("response_one")),
            new Expectation(request("request_three"), Times.once(), TimeToLive.unlimited()).thenRespond(response("response_three"))
        ));
        assertThat(logFilter.retrieveLogEntries(null, expectationLogPredicate, logEntryToLogEntry), IsIterableContainingInOrder.<LogEntry>contains(
            new RequestResponseLogEntry(request("request_one"), response("response_one")),
            new RequestResponseLogEntry(request("request_three"), response("response_three"))
        ));
    }

    @Test
    public void shouldRetrieveMessages() {
        // when
        logFilter.add(new MessageLogEntry(null, request("request_one"), "message_one"));
        logFilter.add(new RequestLogEntry(request("request_one")));
        logFilter.add(new MessageLogEntry(null, request("request_two"), "message_two"));
        logFilter.add(new RequestLogEntry(request("request_two")));
        logFilter.add(new MessageLogEntry(null, request("request_three"), "message_three"));
        logFilter.add(new RequestResponseLogEntry(request("request_one"), response("response_one")));
        logFilter.add(new MessageLogEntry(null, request("request_four"), "message_four"));
        logFilter.add(new RequestResponseLogEntry(request("request_three"), response("response_three")));
        logFilter.add(new MessageLogEntry(null, request("request_five"), "message_five"));
        logFilter.add(new ExpectationMatchLogEntry(request("request_one"), new Expectation(request("request_one")).thenRespond(response("response_two"))));
        logFilter.add(new MessageLogEntry(null, request("request_six"), "message_six"));
        logFilter.add(new ExpectationMatchLogEntry(request("request_four"), new Expectation(request("request_four")).thenRespond(response("response_four"))));
        logFilter.add(new MessageLogEntry(null, request("request_seven"), "message_seven"));

        // then
        List<String> logMessages = Lists.transform(logFilter.retrieveMessages(null), new Function<MessageLogEntry, String>() {
            public String apply(MessageLogEntry input) {
                return input.getMessage();
            }
        });
        assertThat(logMessages, contains(
            containsString("message_one"),
            containsString("message_two"),
            containsString("message_three"),
            containsString("message_four"),
            containsString("message_five"),
            containsString("message_six"),
            containsString("message_seven")
        ));
        List<LogEntry> logEntries = logFilter.retrieveLogEntries(null, messageLogPredicate, logEntryToLogEntry);
        List<MessageLogEntry> messageLogEntries = Arrays.asList(
            new MessageLogEntry(null, request("request_one"), "message_one"),
            new MessageLogEntry(null, request("request_two"), "message_two"),
            new MessageLogEntry(null, request("request_three"), "message_three"),
            new MessageLogEntry(null, request("request_four"), "message_four"),
            new MessageLogEntry(null, request("request_five"), "message_five"),
            new MessageLogEntry(null, request("request_six"), "message_six"),
            new MessageLogEntry(null, request("request_seven"), "message_seven")
        );
        for (int i = 0; i < logEntries.size(); i++) {
            MessageLogEntry messageLogEntry = (MessageLogEntry) logEntries.get(i);
            assertThat(messageLogEntry.getHttpRequests(), is(messageLogEntries.get(i).getHttpRequests()));
            assertThat(messageLogEntry.getMessage(), is(messageLogEntries.get(i).getMessage()));
        }
    }

    @Test
    public void shouldRetrieveLogEntries() {
        // when
        logFilter.add(new RequestLogEntry(request("request_one")));
        logFilter.add(new RequestLogEntry(request("request_two")));
        logFilter.add(new RequestResponseLogEntry(request("request_one"), response("response_one")));
        logFilter.add(new RequestResponseLogEntry(request("request_three"), response("response_three")));
        logFilter.add(new ExpectationMatchLogEntry(request("request_one"), new Expectation(request("request_one")).thenRespond(response("response_two"))));
        logFilter.add(new ExpectationMatchLogEntry(request("request_four"), new Expectation(request("request_four")).thenRespond(response("response_four"))));

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
