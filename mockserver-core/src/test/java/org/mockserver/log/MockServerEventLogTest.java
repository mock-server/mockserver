package org.mockserver.log;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.model.HttpRequest;
import org.mockserver.scheduler.Scheduler;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.mockito.Mockito.mock;
import static org.mockserver.log.model.LogEntry.LogMessageType.*;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.slf4j.event.Level.INFO;

public class MockServerEventLogTest {

    private MockServerLogger mockServerLogger;
    private MockServerEventLog mockServerLog;

    @BeforeClass
    public static void fixTime() {
        TimeService.fixedTime = true;
    }

    @Before
    public void setupTestFixture() {
        Scheduler scheduler = mock(Scheduler.class);
        HttpStateHandler httpStateHandler = new HttpStateHandler(scheduler);
        mockServerLogger = new MockServerLogger(httpStateHandler);
        mockServerLog = httpStateHandler.getMockServerLog();
    }

    @Test
    public void shouldRetrieveLogEntriesWithNullRequestMatcher() throws InterruptedException {
        // given
        mockServerLogger.logEvent(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(RECEIVED_REQUEST)
                .setHttpRequest(request("request_one"))
                .setMessageFormat("received request:{}")
                .setArguments(request("request_one"))
        );
        mockServerLogger.logEvent(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(EXPECTATION_NOT_MATCHED_RESPONSE)
                .setHttpRequest(request("request_one"))
                .setExpectation(new Expectation(request("request_one")).thenRespond(response("response_two")))
                .setMessageFormat("no expectation for:{}returning response:{}")
                .setArguments(request("request_one"), notFoundResponse())
        );
        mockServerLogger.logEvent(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(RECEIVED_REQUEST)
                .setHttpRequest(request("request_two"))
                .setMessageFormat("received request:{}")
                .setArguments(request("request_two"))
        );
        mockServerLogger.logEvent(
            new LogEntry()
                .setType(EXPECTATION_MATCHED)
                .setLogLevel(INFO)
                .setHttpRequest(request("request_two"))
                .setExpectation(new Expectation(request("request_two")).thenRespond(response("response_two")))
                .setMessageFormat("request:{}matched expectation:{}")
                .setArguments(request("request_two"), new Expectation(request("request_two")).thenRespond(response("response_two")))
        );
        mockServerLogger.logEvent(
            new LogEntry()
                .setType(EXPECTATION_RESPONSE)
                .setLogLevel(INFO)
                .setHttpRequest(request("request_two"))
                .setHttpResponse(response("response_two"))
                .setMessageFormat("request:{}matched expectation:{}")
                .setMessageFormat("returning response:{}for request:{}for action:{}")
                .setArguments(request("request_two"), response("response_two"), response("response_two"))
        );
        mockServerLogger.logEvent(
            new LogEntry()
                .setType(TRACE)
                .setHttpRequest(request("request_four"))
                .setExpectation(new Expectation(request("request_four")).thenRespond(response("response_four")))
                .setMessageFormat("some random {} message")
                .setArguments("argument_one")
        );
        mockServerLogger.logEvent(
            new LogEntry()
                .setType(FORWARDED_REQUEST)
                .setHttpRequest(request("request_five"))
                .setHttpResponse(response("response_five"))
        );
        MILLISECONDS.sleep(100);

        // then
        assertThat(mockServerLog.retrieveRequests(null), contains(
            request("request_one"),
            request("request_two")
        ));
        assertThat(mockServerLog.retrieveRequestResponseMessageLogEntries(null), contains(
            new LogEntry()
                .setEpochTime(TimeService.currentTimeMillis())
                .setLogLevel(INFO)
                .setType(EXPECTATION_NOT_MATCHED_RESPONSE)
                .setHttpRequest(request("request_one"))
                .setExpectation(new Expectation(request("request_one")).thenRespond(response("response_two")))
                .setMessageFormat("no expectation for:{}returning response:{}")
                .setArguments(request("request_one"), notFoundResponse()),
            new LogEntry()
                .setEpochTime(TimeService.currentTimeMillis())
                .setLogLevel(INFO)
                .setType(EXPECTATION_RESPONSE)
                .setHttpRequest(request("request_two"))
                .setHttpResponse(response("response_two"))
                .setMessageFormat("request:{}matched expectation:{}")
                .setMessageFormat("returning response:{}for request:{}for action:{}")
                .setArguments(request("request_two"), response("response_two"), response("response_two")),
            new LogEntry()
                .setType(FORWARDED_REQUEST)
                .setHttpRequest(request("request_five"))
                .setHttpResponse(response("response_five"))
        ));
        assertThat(mockServerLog.retrieveRecordedExpectations(null), contains(
            new Expectation(request("request_five"), Times.once(), TimeToLive.unlimited()).thenRespond(response("response_five"))
        ));
        assertThat(mockServerLog.retrieveMessageLogEntries(null), contains(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(RECEIVED_REQUEST)
                .setHttpRequest(request("request_one"))
                .setMessageFormat("received request:{}")
                .setArguments(request("request_one")),
            new LogEntry()
                .setLogLevel(INFO)
                .setType(EXPECTATION_NOT_MATCHED_RESPONSE)
                .setHttpRequest(request("request_one"))
                .setExpectation(new Expectation(request("request_one")).thenRespond(response("response_two")))
                .setMessageFormat("no expectation for:{}returning response:{}")
                .setArguments(request("request_one"), notFoundResponse()),
            new LogEntry()
                .setLogLevel(INFO)
                .setType(RECEIVED_REQUEST)
                .setHttpRequest(request("request_two"))
                .setMessageFormat("received request:{}")
                .setArguments(request("request_two")),
            new LogEntry()
                .setType(EXPECTATION_MATCHED)
                .setLogLevel(INFO)
                .setHttpRequest(request("request_two"))
                .setExpectation(new Expectation(request("request_two")).thenRespond(response("response_two")))
                .setMessageFormat("request:{}matched expectation:{}")
                .setArguments(request("request_two"), new Expectation(request("request_two")).thenRespond(response("response_two"))),
            new LogEntry()
                .setType(EXPECTATION_RESPONSE)
                .setLogLevel(INFO)
                .setHttpRequest(request("request_two"))
                .setHttpResponse(response("response_two"))
                .setMessageFormat("request:{}matched expectation:{}")
                .setMessageFormat("returning response:{}for request:{}for action:{}")
                .setArguments(request("request_two"), response("response_two"), response("response_two")),
            new LogEntry()
                .setType(TRACE)
                .setHttpRequest(request("request_four"))
                .setExpectation(new Expectation(request("request_four")).thenRespond(response("response_four")))
                .setMessageFormat("some random {} message")
                .setArguments("argument_one"),
            new LogEntry()
                .setType(FORWARDED_REQUEST)
                .setHttpRequest(request("request_five"))
                .setHttpResponse(response("response_five"))
        ));
    }

    @Test
    public void shouldRetrieveLogEntriesWithRequestMatcher() throws InterruptedException {
        // given
        mockServerLogger.logEvent(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(RECEIVED_REQUEST)
                .setHttpRequest(request("request_one"))
                .setMessageFormat("received request:{}")
                .setArguments(request("request_one"))
        );
        mockServerLogger.logEvent(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(EXPECTATION_NOT_MATCHED_RESPONSE)
                .setHttpRequest(request("request_one"))
                .setExpectation(new Expectation(request("request_one")).thenRespond(response("response_two")))
                .setMessageFormat("no expectation for:{}returning response:{}")
                .setArguments(request("request_one"), notFoundResponse())
        );
        mockServerLogger.logEvent(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(RECEIVED_REQUEST)
                .setHttpRequest(request("request_two"))
                .setMessageFormat("received request:{}")
                .setArguments(request("request_two"))
        );
        mockServerLogger.logEvent(
            new LogEntry()
                .setType(EXPECTATION_MATCHED)
                .setLogLevel(INFO)
                .setHttpRequest(request("request_two"))
                .setExpectation(new Expectation(request("request_two")).thenRespond(response("response_two")))
                .setMessageFormat("request:{}matched expectation:{}")
                .setArguments(request("request_two"), new Expectation(request("request_two")).thenRespond(response("response_two")))
        );
        mockServerLogger.logEvent(
            new LogEntry()
                .setType(EXPECTATION_RESPONSE)
                .setLogLevel(INFO)
                .setHttpRequest(request("request_two"))
                .setHttpResponse(response("response_two"))
                .setMessageFormat("request:{}matched expectation:{}")
                .setMessageFormat("returning response:{}for request:{}for action:{}")
                .setArguments(request("request_two"), response("response_two"), response("response_two"))
        );
        mockServerLogger.logEvent(
            new LogEntry()
                .setType(TRACE)
                .setHttpRequest(request("request_four"))
                .setExpectation(new Expectation(request("request_four")).thenRespond(response("response_four")))
                .setMessageFormat("some random {} message")
                .setArguments("argument_one")
        );
        mockServerLogger.logEvent(
            new LogEntry()
                .setType(FORWARDED_REQUEST)
                .setHttpRequest(request("request_five"))
                .setHttpResponse(response("response_five"))
        );
        MILLISECONDS.sleep(100);

        // then
        HttpRequest requestMatcher = request("request_one");
        assertThat(mockServerLog.retrieveRequests(requestMatcher), contains(
            request("request_one")
        ));
        assertThat(mockServerLog.retrieveRequestResponseMessageLogEntries(requestMatcher), contains(
            new LogEntry()
                .setEpochTime(TimeService.currentTimeMillis())
                .setLogLevel(INFO)
                .setType(EXPECTATION_NOT_MATCHED_RESPONSE)
                .setHttpRequest(request("request_one"))
                .setExpectation(new Expectation(request("request_one")).thenRespond(response("response_two")))
                .setMessageFormat("no expectation for:{}returning response:{}")
                .setArguments(request("request_one"), notFoundResponse())
        ));
        assertThat(mockServerLog.retrieveRecordedExpectations(requestMatcher), empty());
        assertThat(mockServerLog.retrieveMessageLogEntries(requestMatcher), contains(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(RECEIVED_REQUEST)
                .setHttpRequest(request("request_one"))
                .setMessageFormat("received request:{}")
                .setArguments(request("request_one")),
            new LogEntry()
                .setLogLevel(INFO)
                .setType(EXPECTATION_NOT_MATCHED_RESPONSE)
                .setHttpRequest(request("request_one"))
                .setExpectation(new Expectation(request("request_one")).thenRespond(response("response_two")))
                .setMessageFormat("no expectation for:{}returning response:{}")
                .setArguments(request("request_one"), notFoundResponse())
        ));
    }

    @Test
    public void shouldClearWithNullRequestMatcher() throws InterruptedException {
        // given
        mockServerLogger.logEvent(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(EXPECTATION_NOT_MATCHED_RESPONSE)
                .setHttpRequest(request("request_one"))
                .setExpectation(new Expectation(request("request_one")).thenRespond(response("response_two")))
                .setMessageFormat("no expectation for:{}returning response:{}")
                .setArguments(request("request_one"), notFoundResponse())
        );
        mockServerLogger.logEvent(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(EXPECTATION_RESPONSE)
                .setHttpRequest(request("request_two"))
                .setHttpResponse(response("response_two"))
                .setMessageFormat("returning error:{}for request:{}for action:{}")
                .setArguments(request("request_two"), response("response_two"), response("response_two"))
        );
        mockServerLogger.logEvent(
            new LogEntry()
                .setType(EXPECTATION_MATCHED)
                .setLogLevel(INFO)
                .setHttpRequest(request("request_one"))
                .setExpectation(new Expectation(request("request_one")).thenRespond(response("response_two")))
                .setMessageFormat("request:{}matched expectation:{}")
                .setArguments(request("request_one"), new Expectation(request("request_one")).thenRespond(response("response_two")))
        );
        mockServerLogger.logEvent(
            new LogEntry()
                .setType(EXPECTATION_MATCHED)
                .setLogLevel(INFO)
                .setHttpRequest(request("request_two"))
                .setExpectation(new Expectation(request("request_two")).thenRespond(response("response_two")))
                .setMessageFormat("request:{}matched expectation:{}")
                .setArguments(request("request_two"), new Expectation(request("request_two")).thenRespond(response("response_two")))
        );
        mockServerLogger.logEvent(
            new LogEntry()
                .setType(TRACE)
                .setHttpRequest(request("request_four"))
                .setExpectation(new Expectation(request("request_four")).thenRespond(response("response_four")))
                .setMessageFormat("some random {} message")
                .setArguments("argument_one")
        );
        MILLISECONDS.sleep(100);

        // when
        mockServerLog.clear(null);

        // then
        assertThat(mockServerLog.retrieveRequests(null), empty());
        assertThat(mockServerLog.retrieveRecordedExpectations(null), empty());
        assertThat(mockServerLog.retrieveMessageLogEntries(null), empty());
        assertThat(mockServerLog.retrieveRequestLogEntries(null), empty());
        assertThat(mockServerLog.retrieveRequestResponseMessageLogEntries(null), empty());
    }

    @Test
    public void shouldClearWithRequestMatcher() throws InterruptedException {
        // given
        mockServerLogger.logEvent(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(RECEIVED_REQUEST)
                .setHttpRequest(request("request_one"))
                .setMessageFormat("received request:{}")
                .setArguments(request("request_one"))
        );
        mockServerLogger.logEvent(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(EXPECTATION_NOT_MATCHED_RESPONSE)
                .setHttpRequest(request("request_one"))
                .setExpectation(new Expectation(request("request_one")).thenRespond(response("response_two")))
                .setMessageFormat("no expectation for:{}returning response:{}")
                .setArguments(request("request_one"), notFoundResponse())
        );
        mockServerLogger.logEvent(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(RECEIVED_REQUEST)
                .setHttpRequest(request("request_two"))
                .setMessageFormat("received request:{}")
                .setArguments(request("request_two"))
        );
        mockServerLogger.logEvent(
            new LogEntry()
                .setType(EXPECTATION_MATCHED)
                .setLogLevel(INFO)
                .setHttpRequest(request("request_two"))
                .setExpectation(new Expectation(request("request_two")).thenRespond(response("response_two")))
                .setMessageFormat("request:{}matched expectation:{}")
                .setArguments(request("request_two"), new Expectation(request("request_two")).thenRespond(response("response_two")))
        );
        mockServerLogger.logEvent(
            new LogEntry()
                .setType(EXPECTATION_RESPONSE)
                .setLogLevel(INFO)
                .setHttpRequest(request("request_two"))
                .setHttpResponse(response("response_two"))
                .setMessageFormat("request:{}matched expectation:{}")
                .setMessageFormat("returning response:{}for request:{}for action:{}")
                .setArguments(request("request_two"), response("response_two"), response("response_two"))
        );
        mockServerLogger.logEvent(
            new LogEntry()
                .setType(TRACE)
                .setHttpRequest(request("request_four"))
                .setExpectation(new Expectation(request("request_four")).thenRespond(response("response_four")))
                .setMessageFormat("some random {} message")
                .setArguments("argument_one")
        );
        mockServerLogger.logEvent(
            new LogEntry()
                .setType(FORWARDED_REQUEST)
                .setHttpRequest(request("request_five"))
                .setHttpResponse(response("response_five"))
        );
        MILLISECONDS.sleep(100);

        // when
        mockServerLog.clear(request("request_one"));

        // then
        assertThat(mockServerLog.retrieveRequests(null), contains(
            request("request_two")
        ));
        assertThat(mockServerLog.retrieveRequestResponseMessageLogEntries(null), contains(
            new LogEntry()
                .setEpochTime(TimeService.currentTimeMillis())
                .setLogLevel(INFO)
                .setType(EXPECTATION_RESPONSE)
                .setHttpRequest(request("request_two"))
                .setHttpResponse(response("response_two"))
                .setMessageFormat("request:{}matched expectation:{}")
                .setMessageFormat("returning response:{}for request:{}for action:{}")
                .setArguments(request("request_two"), response("response_two"), response("response_two")),
            new LogEntry()
                .setType(FORWARDED_REQUEST)
                .setHttpRequest(request("request_five"))
                .setHttpResponse(response("response_five"))
        ));
        assertThat(mockServerLog.retrieveRecordedExpectations(null), contains(
            new Expectation(request("request_five"), Times.once(), TimeToLive.unlimited()).thenRespond(response("response_five"))
        ));
        assertThat(mockServerLog.retrieveMessageLogEntries(null), contains(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(RECEIVED_REQUEST)
                .setHttpRequest(request("request_two"))
                .setMessageFormat("received request:{}")
                .setArguments(request("request_two")),
            new LogEntry()
                .setType(EXPECTATION_MATCHED)
                .setLogLevel(INFO)
                .setHttpRequest(request("request_two"))
                .setExpectation(new Expectation(request("request_two")).thenRespond(response("response_two")))
                .setMessageFormat("request:{}matched expectation:{}")
                .setArguments(request("request_two"), new Expectation(request("request_two")).thenRespond(response("response_two"))),
            new LogEntry()
                .setType(EXPECTATION_RESPONSE)
                .setLogLevel(INFO)
                .setHttpRequest(request("request_two"))
                .setHttpResponse(response("response_two"))
                .setMessageFormat("request:{}matched expectation:{}")
                .setMessageFormat("returning response:{}for request:{}for action:{}")
                .setArguments(request("request_two"), response("response_two"), response("response_two")),
            new LogEntry()
                .setType(TRACE)
                .setHttpRequest(request("request_four"))
                .setExpectation(new Expectation(request("request_four")).thenRespond(response("response_four")))
                .setMessageFormat("some random {} message")
                .setArguments("argument_one"),
            new LogEntry()
                .setType(FORWARDED_REQUEST)
                .setHttpRequest(request("request_five"))
                .setHttpResponse(response("response_five"))
        ));
    }

    @Test
    public void shouldReset() throws InterruptedException {
        // given
        mockServerLogger.logEvent(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(EXPECTATION_NOT_MATCHED_RESPONSE)
                .setHttpRequest(request("request_one"))
                .setExpectation(new Expectation(request("request_one")).thenRespond(response("response_two")))
                .setMessageFormat("no expectation for:{}returning response:{}")
                .setArguments(request("request_one"), notFoundResponse())
        );
        mockServerLogger.logEvent(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(EXPECTATION_RESPONSE)
                .setHttpRequest(request("request_two"))
                .setHttpResponse(response("response_two"))
                .setMessageFormat("returning error:{}for request:{}for action:{}")
                .setArguments(request("request_two"), response("response_two"), response("response_two"))
        );
        mockServerLogger.logEvent(
            new LogEntry()
                .setType(EXPECTATION_MATCHED)
                .setLogLevel(INFO)
                .setHttpRequest(request("request_one"))
                .setExpectation(new Expectation(request("request_one")).thenRespond(response("response_two")))
                .setMessageFormat("request:{}matched expectation:{}")
                .setArguments(request("request_one"), new Expectation(request("request_one")).thenRespond(response("response_two")))
        );
        mockServerLogger.logEvent(
            new LogEntry()
                .setType(EXPECTATION_MATCHED)
                .setLogLevel(INFO)
                .setHttpRequest(request("request_two"))
                .setExpectation(new Expectation(request("request_two")).thenRespond(response("response_two")))
                .setMessageFormat("request:{}matched expectation:{}")
                .setArguments(request("request_two"), new Expectation(request("request_two")).thenRespond(response("response_two")))
        );
        mockServerLogger.logEvent(
            new LogEntry()
                .setType(TRACE)
                .setHttpRequest(request("request_four"))
                .setExpectation(new Expectation(request("request_four")).thenRespond(response("response_four")))
                .setMessageFormat("some random {} message")
                .setArguments("argument_one")
        );
        MILLISECONDS.sleep(100);

        // when
        mockServerLog.reset();

        // then
        assertThat(mockServerLog.retrieveRequests(null), empty());
        assertThat(mockServerLog.retrieveRecordedExpectations(null), empty());
        assertThat(mockServerLog.retrieveMessageLogEntries(null), empty());
        assertThat(mockServerLog.retrieveRequestLogEntries(null), empty());
        assertThat(mockServerLog.retrieveRequestResponseMessageLogEntries(null), empty());
    }
}
