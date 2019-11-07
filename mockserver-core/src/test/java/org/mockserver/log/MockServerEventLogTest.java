package org.mockserver.log;

import com.google.common.util.concurrent.SettableFuture;
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
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;

import java.util.List;
import java.util.concurrent.Future;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockserver.log.model.LogEntry.LogMessageType.*;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.slf4j.event.Level.INFO;

public class MockServerEventLogTest {

    private MockServerLogger mockServerLogger;
    private MockServerEventLog mockServerEventLog;

    @BeforeClass
    public static void fixTime() {
        TimeService.fixedTime = true;
    }

    @Before
    public void setupTestFixture() {
        Scheduler scheduler = mock(Scheduler.class);
        HttpStateHandler httpStateHandler = new HttpStateHandler(scheduler);
        mockServerLogger = new MockServerLogger(httpStateHandler);
        mockServerEventLog = httpStateHandler.getMockServerLog();
    }

    private List<LogEntry> retrieveMessageLogEntries(HttpRequest httpRequest) {
        SettableFuture<List<LogEntry>> future = SettableFuture.create();
        mockServerEventLog.retrieveMessageLogEntries(httpRequest, future::set);
        try {
            return future.get();
        } catch (Exception e) {
            fail(e.getMessage());
            return null;
        }
    }

    private List<HttpRequest> retrieveRequests(HttpRequest httpRequest) {
        SettableFuture<List<HttpRequest>> result = SettableFuture.create();
        mockServerEventLog.retrieveRequests(httpRequest, result::set);
        try {
            return result.get();
        } catch (Exception e) {
            fail(e.getMessage());
            return null;
        }
    }

    private List<LogEntry> retrieveRequestLogEntries(HttpRequest httpRequest) {
        SettableFuture<List<LogEntry>> future = SettableFuture.create();
        mockServerEventLog.retrieveRequestLogEntries(httpRequest, future::set);
        try {
            return future.get();
        } catch (Exception e) {
            fail(e.getMessage());
            return null;
        }
    }

    private List<LogEntry> retrieveRequestResponseMessageLogEntries(HttpRequest httpRequest) {
        SettableFuture<List<LogEntry>> future = SettableFuture.create();
        mockServerEventLog.retrieveRequestResponseMessageLogEntries(httpRequest, future::set);
        try {
            return future.get();
        } catch (Exception e) {
            fail(e.getMessage());
            return null;
        }
    }

    private List<Expectation> retrieveRecordedExpectations(HttpRequest httpRequest) {
        SettableFuture<List<Expectation>> future = SettableFuture.create();
        mockServerEventLog.retrieveRecordedExpectations(httpRequest, future::set);
        try {
            return future.get();
        } catch (Exception e) {
            fail(e.getMessage());
            return null;
        }
    }

    @Test
    public void shouldRetrieveLogEntriesWithNullRequestMatcher()  {
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

        // then
        assertThat(retrieveRequests(null), contains(
            request("request_one"),
            request("request_two")
        ));
        assertThat(retrieveRequestResponseMessageLogEntries(null), contains(
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
        assertThat(retrieveRecordedExpectations(null), contains(
            new Expectation(request("request_five"), Times.once(), TimeToLive.unlimited()).thenRespond(response("response_five"))
        ));
        assertThat(retrieveMessageLogEntries(null), contains(
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
    public void shouldRetrieveLogEntriesWithRequestMatcher()  {
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

        // then
        HttpRequest requestMatcher = request("request_one");
        assertThat(retrieveRequests(requestMatcher), contains(
            request("request_one")
        ));
        assertThat(retrieveRequestResponseMessageLogEntries(requestMatcher), contains(
            new LogEntry()
                .setEpochTime(TimeService.currentTimeMillis())
                .setLogLevel(INFO)
                .setType(EXPECTATION_NOT_MATCHED_RESPONSE)
                .setHttpRequest(request("request_one"))
                .setExpectation(new Expectation(request("request_one")).thenRespond(response("response_two")))
                .setMessageFormat("no expectation for:{}returning response:{}")
                .setArguments(request("request_one"), notFoundResponse())
        ));
        assertThat(retrieveRecordedExpectations(requestMatcher), empty());
        assertThat(retrieveMessageLogEntries(requestMatcher), contains(
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
    public void shouldClearWithNullRequestMatcher()  {
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

        // when
        mockServerEventLog.clear(null);

        // then
        assertThat(retrieveRequests(null), empty());
        assertThat(retrieveRecordedExpectations(null), empty());
        assertThat(retrieveMessageLogEntries(null), empty());
        assertThat(retrieveRequestLogEntries(null), empty());
        assertThat(retrieveRequestResponseMessageLogEntries(null), empty());
    }

    @Test
    public void shouldClearWithRequestMatcher()  {
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

        // when
        mockServerEventLog.clear(request("request_one"));

        // then
        assertThat(retrieveRequests(null), contains(
            request("request_two")
        ));
        assertThat(retrieveRequestResponseMessageLogEntries(null), contains(
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
        assertThat(retrieveRecordedExpectations(null), contains(
            new Expectation(request("request_five"), Times.once(), TimeToLive.unlimited()).thenRespond(response("response_five"))
        ));
        assertThat(retrieveMessageLogEntries(null), contains(
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
    public void shouldReset()  {
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

        // when
        mockServerEventLog.reset();

        // then
        assertThat(retrieveRequests(null), empty());
        assertThat(retrieveRecordedExpectations(null), empty());
        assertThat(retrieveMessageLogEntries(null), empty());
        assertThat(retrieveRequestLogEntries(null), empty());
        assertThat(retrieveRequestResponseMessageLogEntries(null), empty());
    }
}
