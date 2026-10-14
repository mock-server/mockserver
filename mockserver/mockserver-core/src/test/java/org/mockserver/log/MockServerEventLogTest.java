package org.mockserver.log;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpState;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.RequestDefinition;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.time.EpochService;
import org.mockserver.time.GlobalFixedTime;
import org.slf4j.event.Level;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.fail;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.*;
import static org.mockserver.log.model.LogEntryMessages.RECEIVED_REQUEST_MESSAGE_FORMAT;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.slf4j.event.Level.INFO;

public class MockServerEventLogTest {

    private final Configuration configuration = configuration();
    private MockServerLogger mockServerLogger;
    private MockServerEventLog mockServerEventLog;
    private HttpState httpStateHandler;

    @ClassRule
    public static final GlobalFixedTime fixedTime = new GlobalFixedTime();

    @Before
    public void setupTestFixture() {
        Scheduler scheduler = mock(Scheduler.class);
        httpStateHandler = new HttpState(configuration, new MockServerLogger(configuration, MockServerLogger.class), scheduler);
        mockServerLogger = httpStateHandler.getMockServerLogger();
        mockServerEventLog = httpStateHandler.getMockServerLog();
    }

    @After
    public void stopHttpState() {
        if (httpStateHandler != null) {
            httpStateHandler.stop();
        }
    }

    private List<LogEntry> retrieveMessageLogEntries(RequestDefinition httpRequest) {
        CompletableFuture<List<LogEntry>> future = new CompletableFuture<>();
        mockServerEventLog.retrieveMessageLogEntries(httpRequest, future::complete);
        try {
            return future.get(60, SECONDS);
        } catch (Exception e) {
            fail(e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("SameParameterValue")
    private List<LogEntry> retrieveMessageLogEntriesIncludingDeleted(RequestDefinition httpRequest) {
        CompletableFuture<List<LogEntry>> future = new CompletableFuture<>();
        mockServerEventLog.retrieveMessageLogEntriesIncludingDeleted(httpRequest, future::complete);
        try {
            return future.get(60, SECONDS);
        } catch (Exception e) {
            fail(e.getMessage());
            return null;
        }
    }

    private List<RequestDefinition> retrieveRequests(RequestDefinition httpRequest) {
        CompletableFuture<List<RequestDefinition>> result = new CompletableFuture<>();
        mockServerEventLog.retrieveRequests(httpRequest, result::complete);
        try {
            return result.get(60, SECONDS);
        } catch (Exception e) {
            fail(e.getMessage());
            return null;
        }
    }

    private List<LogEntry> retrieveRequestLogEntries() {
        CompletableFuture<List<LogEntry>> future = new CompletableFuture<>();
        mockServerEventLog.retrieveRequestLogEntries(null, future::complete);
        try {
            return future.get(60, SECONDS);
        } catch (Exception e) {
            fail(e.getMessage());
            return null;
        }
    }

    private List<LogEntry> retrieveRequestResponseMessageLogEntries(RequestDefinition httpRequest) {
        CompletableFuture<List<LogEntry>> future = new CompletableFuture<>();
        mockServerEventLog.retrieveRequestResponseMessageLogEntries(httpRequest, future::complete);
        try {
            return future.get(60, SECONDS);
        } catch (Exception e) {
            fail(e.getMessage());
            return null;
        }
    }

    private List<Expectation> retrieveRecordedExpectations(RequestDefinition httpRequest) {
        CompletableFuture<List<Expectation>> future = new CompletableFuture<>();
        mockServerEventLog.retrieveRecordedExpectations(httpRequest, future::complete);
        try {
            return future.get(60, SECONDS);
        } catch (Exception e) {
            fail(e.getMessage());
            return null;
        }
    }

    @Test
    public void shouldRetrieveLogEntriesContainingNulls() {
        // given
        configuration.logLevel(Level.INFO);
        mockServerLogger.logEvent(
            new LogEntry()
                .setType(RECEIVED_REQUEST)
        );
        mockServerLogger.logEvent(
            new LogEntry()
                .setType(NO_MATCH_RESPONSE)
        );
        mockServerLogger.logEvent(
            new LogEntry()
                .setType(RECEIVED_REQUEST)
        );
        mockServerLogger.logEvent(
            new LogEntry()
                .setType(EXPECTATION_MATCHED)
        );
        mockServerLogger.logEvent(
            new LogEntry()
                .setType(EXPECTATION_RESPONSE)
        );
        mockServerLogger.logEvent(
            new LogEntry()
                .setType(TRACE)
        );
        mockServerLogger.logEvent(
            new LogEntry()
                .setType(FORWARDED_REQUEST)
        );

        // then
        assertThat(retrieveRequests(null), empty());
        assertThat(retrieveRequestResponseMessageLogEntries(null), contains(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(NO_MATCH_RESPONSE)
                .setHttpRequests(new RequestDefinition[0]),
            new LogEntry()
                .setLogLevel(INFO)
                .setType(EXPECTATION_RESPONSE)
                .setHttpRequests(new RequestDefinition[0]),
            new LogEntry()
                .setLogLevel(INFO)
                .setType(FORWARDED_REQUEST)
                .setHttpRequests(new RequestDefinition[0])
        ));
        assertThat(retrieveRecordedExpectations(null), empty());
    }

    @Test
    public void shouldRetrieveLogEntriesWithNullRequestMatcher() {
        // given
        configuration.logLevel(Level.INFO);
        mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(INFO)
                    .setType(RECEIVED_REQUEST)
                    .setHttpRequest(request("request_one"))
                    .setMessageFormat(RECEIVED_REQUEST_MESSAGE_FORMAT)
                    .setArguments(request("request_one"))
            );
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(INFO)
                    .setType(NO_MATCH_RESPONSE)
                    .setHttpRequest(request("request_one"))
                    .setExpectation(new Expectation(request("request_one")).thenRespond(response("response_two")))
                    .setMessageFormat("no expectation for:{}returning response:{}")
                    .setArguments(request("request_one"), notFoundResponse())
            );
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(INFO)
                    .setType(RECEIVED_REQUEST)
                    .setHttpRequests(new RequestDefinition[]{request("request_two"), request("request_three")})
                    .setMessageFormat(RECEIVED_REQUEST_MESSAGE_FORMAT)
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
                    .setMessageFormat("some random{}message")
                    .setArguments("argument_one")
            );
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(FORWARDED_REQUEST)
                    .setHttpRequest(request("request_five"))
                    .setHttpResponse(response("response_five"))
                    .setExpectation(request("request_five"), response("response_five"))
            );

            // then
            assertThat(retrieveRequests(null), contains(
                request("request_one"),
                request("request_two"),
                request("request_three")
            ));
            assertThat(retrieveRequestResponseMessageLogEntries(null), contains(
                new LogEntry()
                    .setEpochTime(EpochService.currentTimeMillis())
                    .setLogLevel(INFO)
                    .setType(NO_MATCH_RESPONSE)
                    .setHttpRequest(request("request_one"))
                    .setExpectation(new Expectation(request("request_one")).thenRespond(response("response_two")))
                    .setMessageFormat("no expectation for:{}returning response:{}")
                    .setArguments(request("request_one"), notFoundResponse()),
                new LogEntry()
                    .setEpochTime(EpochService.currentTimeMillis())
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
                    .setExpectation(request("request_five"), response("response_five"))
            ));
            assertThat(retrieveRecordedExpectations(null), contains(
                new Expectation(request("request_five"), Times.once(), TimeToLive.unlimited(), 0).thenRespond(response("response_five"))
            ));
            List<LogEntry> actual = retrieveMessageLogEntries(null);
            assertThat(actual, contains(
                new LogEntry()
                    .setLogLevel(INFO)
                    .setType(RECEIVED_REQUEST)
                    .setHttpRequest(request("request_one"))
                    .setMessageFormat(RECEIVED_REQUEST_MESSAGE_FORMAT)
                    .setArguments(request("request_one")),
                new LogEntry()
                    .setLogLevel(INFO)
                    .setType(NO_MATCH_RESPONSE)
                    .setHttpRequest(request("request_one"))
                    .setExpectation(new Expectation(request("request_one")).thenRespond(response("response_two")))
                    .setMessageFormat("no expectation for:{}returning response:{}")
                    .setArguments(request("request_one"), notFoundResponse()),
                new LogEntry()
                    .setLogLevel(INFO)
                    .setType(RECEIVED_REQUEST)
                    .setHttpRequests(new RequestDefinition[]{request("request_two"), request("request_three")})
                    .setMessageFormat(RECEIVED_REQUEST_MESSAGE_FORMAT)
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
                    .setMessageFormat("some random{}message")
                    .setArguments("argument_one"),
                new LogEntry()
                    .setType(FORWARDED_REQUEST)
                    .setHttpRequest(request("request_five"))
                    .setHttpResponse(response("response_five"))
                    .setExpectation(request("request_five"), response("response_five"))
            ));
    }

    @Test
    public void shouldRetrieveLogEntriesWithRequestMatcher() {
        // given
        configuration.logLevel(Level.INFO);
        mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(INFO)
                    .setType(RECEIVED_REQUEST)
                    .setHttpRequest(request("request_one"))
                    .setMessageFormat(RECEIVED_REQUEST_MESSAGE_FORMAT)
                    .setArguments(request("request_one"))
            );
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(INFO)
                    .setType(NO_MATCH_RESPONSE)
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
                    .setMessageFormat(RECEIVED_REQUEST_MESSAGE_FORMAT)
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
                    .setMessageFormat("some random{}message")
                    .setArguments("argument_one")
            );
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(FORWARDED_REQUEST)
                    .setHttpRequest(request("request_five"))
                    .setHttpResponse(response("response_five"))
                    .setExpectation(request("request_five"), response("response_five"))
            );

            // then
            RequestDefinition requestMatcher = request("request_one");
            assertThat(retrieveRequests(requestMatcher), contains(
                request("request_one")
            ));
            assertThat(retrieveRequestResponseMessageLogEntries(requestMatcher), contains(
                new LogEntry()
                    .setEpochTime(EpochService.currentTimeMillis())
                    .setLogLevel(INFO)
                    .setType(NO_MATCH_RESPONSE)
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
                    .setMessageFormat(RECEIVED_REQUEST_MESSAGE_FORMAT)
                    .setArguments(request("request_one")),
                new LogEntry()
                    .setLogLevel(INFO)
                    .setType(NO_MATCH_RESPONSE)
                    .setHttpRequest(request("request_one"))
                    .setExpectation(new Expectation(request("request_one")).thenRespond(response("response_two")))
                    .setMessageFormat("no expectation for:{}returning response:{}")
                    .setArguments(request("request_one"), notFoundResponse())
            ));
    }

    @Test
    public void shouldClearWithNullRequestMatcher() {
        // given
        configuration.logLevel(Level.INFO);
        mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(INFO)
                    .setType(NO_MATCH_RESPONSE)
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
                    .setMessageFormat("some random{}message")
                    .setArguments("argument_one")
            );

            // when
            mockServerEventLog.clear(null);

            // then
            assertThat(retrieveRequests(null), empty());
            assertThat(retrieveRecordedExpectations(null), empty());
            assertThat(retrieveMessageLogEntries(null), contains(new LogEntry()
                .setType(CLEARED)
                .setHttpRequest(request())
                .setMessageFormat("cleared logs that match:{}")
                .setArguments("{}")));
            assertThat(retrieveRequestLogEntries(), empty());
            assertThat(retrieveRequestResponseMessageLogEntries(null), empty());
    }

    @Test
    public void shouldClearWithNullRequestMatcherButPreserveRequestLessEntries() {
        // pins the preserved behaviour: clear(null) auto-clears entries that carry >= 1 request,
        // but a request-less entry (getHttpRequests() is the length-0 array, e.g. SERVER_CONFIGURATION
        // logged without a request) SURVIVES the clear - exactly as before the efficiency change.
        // given
        configuration.logLevel(Level.INFO);
        mockServerLogger.logEvent(
            new LogEntry()
                .setType(SERVER_CONFIGURATION)
                .setLogLevel(INFO)
                .setMessageFormat("some random{}configuration message")
                .setArguments("argument_one")
        );
        mockServerLogger.logEvent(
            new LogEntry()
                .setLogLevel(INFO)
                .setType(EXPECTATION_RESPONSE)
                .setHttpRequest(request("request_one"))
                .setHttpResponse(response("response_one"))
                .setMessageFormat("returning response:{}for request:{}")
                .setArguments(response("response_one"), request("request_one"))
        );

        // when
        mockServerEventLog.clear(null);

        // then - the request-bearing entry is gone, the request-less entry survives
        assertThat(retrieveRequests(null), empty());
        List<LogEntry.LogMessageType> survivingTypes = retrieveMessageLogEntries(null).stream()
            .map(LogEntry::getType)
            .collect(Collectors.toList());
        assertThat("request-less SERVER_CONFIGURATION entry must survive clear(null)",
            survivingTypes.contains(SERVER_CONFIGURATION), is(true));
        assertThat("request-bearing EXPECTATION_RESPONSE entry must be cleared by clear(null)",
            survivingTypes.contains(EXPECTATION_RESPONSE), is(false));
    }

    @Test
    public void shouldClearWithNullRequestMatcherWhenWhenLogLevelDebug() {
        // given
        configuration.logLevel(Level.DEBUG);
        mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(INFO)
                    .setType(NO_MATCH_RESPONSE)
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
                    .setMessageFormat("some random{}message")
                    .setArguments("argument_one")
            );

            // when
            mockServerEventLog.clear(null);

            // then
            assertThat(retrieveRequests(null), empty());
            assertThat(retrieveRecordedExpectations(null), empty());
            List<LogEntry> actual = Objects.requireNonNull(retrieveMessageLogEntriesIncludingDeleted(null));
            assertThat(actual.get(0), is(new LogEntry()
                .setDeleted(true)
                .setLogLevel(INFO)
                .setType(NO_MATCH_RESPONSE)
                .setHttpRequest(request("request_one"))
                .setExpectation(new Expectation(request("request_one")).thenRespond(response("response_two")))
                .setMessageFormat("no expectation for:{}returning response:{}")
                .setArguments(request("request_one"), notFoundResponse())));
            assertThat(actual.get(1), is(new LogEntry()
                .setDeleted(true)
                .setLogLevel(INFO)
                .setType(EXPECTATION_RESPONSE)
                .setHttpRequest(request("request_two"))
                .setHttpResponse(response("response_two"))
                .setMessageFormat("returning error:{}for request:{}for action:{}")
                .setArguments(request("request_two"), response("response_two"), response("response_two"))));
            assertThat(actual.get(2), is(new LogEntry()
                .setDeleted(true)
                .setType(EXPECTATION_MATCHED)
                .setLogLevel(INFO)
                .setHttpRequest(request("request_one"))
                .setExpectation(new Expectation(request("request_one")).thenRespond(response("response_two")))
                .setMessageFormat("request:{}matched expectation:{}")
                .setArguments(request("request_one"), new Expectation(request("request_one")).thenRespond(response("response_two")))));
            assertThat(actual.get(3), is(new LogEntry()
                .setDeleted(true)
                .setType(EXPECTATION_MATCHED)
                .setLogLevel(INFO)
                .setHttpRequest(request("request_two"))
                .setExpectation(new Expectation(request("request_two")).thenRespond(response("response_two")))
                .setMessageFormat("request:{}matched expectation:{}")
                .setArguments(request("request_two"), new Expectation(request("request_two")).thenRespond(response("response_two")))));
            assertThat(actual.get(4), is(new LogEntry()
                .setDeleted(true)
                .setType(TRACE)
                .setHttpRequest(request("request_four"))
                .setExpectation(new Expectation(request("request_four")).thenRespond(response("response_four")))
                .setMessageFormat("some random{}message")
                .setArguments("argument_one")));
            assertThat(actual.get(5), is(new LogEntry()
                .setType(CLEARED)
                .setHttpRequest(request())
                .setMessageFormat("cleared logs that match:{}")
                .setArguments("{}")));
            assertThat(retrieveRequestLogEntries(), empty());
            assertThat(retrieveRequestResponseMessageLogEntries(null), empty());
    }

    @Test
    public void shouldClearWithRequestMatcher() {
        // given
        configuration.logLevel(Level.INFO);
        mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(INFO)
                    .setType(RECEIVED_REQUEST)
                    .setHttpRequest(request("request_one"))
                    .setMessageFormat(RECEIVED_REQUEST_MESSAGE_FORMAT)
                    .setArguments(request("request_one"))
            );
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(INFO)
                    .setType(NO_MATCH_RESPONSE)
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
                    .setMessageFormat(RECEIVED_REQUEST_MESSAGE_FORMAT)
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
                    .setMessageFormat("some random{}message")
                    .setArguments("argument_one")
            );
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(FORWARDED_REQUEST)
                    .setHttpRequest(request("request_five"))
                    .setHttpResponse(response("response_five"))
                    .setExpectation(request("request_five"), response("response_five"))
            );

            // when
            mockServerEventLog.clear(request("request_one"));

            // then
            assertThat(retrieveRequests(null), contains(
                request("request_two")
            ));
            assertThat(retrieveRequestResponseMessageLogEntries(null), contains(
                new LogEntry()
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
                    .setExpectation(request("request_five"), response("response_five"))
            ));
            assertThat(retrieveRecordedExpectations(null), contains(
                new Expectation(request("request_five"), Times.once(), TimeToLive.unlimited(), 0).thenRespond(response("response_five"))
            ));
            assertThat(retrieveMessageLogEntries(null), contains(
                new LogEntry()
                    .setLogLevel(INFO)
                    .setType(RECEIVED_REQUEST)
                    .setHttpRequest(request("request_two"))
                    .setMessageFormat(RECEIVED_REQUEST_MESSAGE_FORMAT)
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
                    .setMessageFormat("some random{}message")
                    .setArguments("argument_one"),
                new LogEntry()
                    .setType(FORWARDED_REQUEST)
                    .setHttpRequest(request("request_five"))
                    .setHttpResponse(response("response_five"))
                    .setExpectation(request("request_five"), response("response_five")),
                new LogEntry()
                    .setType(CLEARED)
                    .setHttpRequest(request("request_one"))
                    .setMessageFormat("cleared logs that match:{}")
                    .setArguments(request("request_one"))
            ));
    }

    @Test
    public void shouldClearWithRequestMatcherWhenLogLevelDebug() {
        // given
        configuration.logLevel(Level.DEBUG);
        mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(INFO)
                    .setType(RECEIVED_REQUEST)
                    .setHttpRequest(request("request_one"))
                    .setMessageFormat(RECEIVED_REQUEST_MESSAGE_FORMAT)
                    .setArguments(request("request_one"))
            );
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(INFO)
                    .setType(NO_MATCH_RESPONSE)
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
                    .setMessageFormat(RECEIVED_REQUEST_MESSAGE_FORMAT)
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
                    .setMessageFormat("some random{}message")
                    .setArguments("argument_one")
            );
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(FORWARDED_REQUEST)
                    .setHttpRequest(request("request_five"))
                    .setHttpResponse(response("response_five"))
                    .setExpectation(request("request_five"), response("response_five"))
            );

            // when
            mockServerEventLog.clear(request("request_one"));

            // then
            assertThat(retrieveRequests(null), contains(
                request("request_two")
            ));
            assertThat(retrieveRequestResponseMessageLogEntries(null), contains(
                new LogEntry()
                    .setEpochTime(EpochService.currentTimeMillis())
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
                    .setExpectation(request("request_five"), response("response_five"))
            ));
            assertThat(retrieveRecordedExpectations(null), contains(
                new Expectation(request("request_five"), Times.once(), TimeToLive.unlimited(), 0).thenRespond(response("response_five"))
            ));
            List<LogEntry> actual = Objects.requireNonNull(retrieveMessageLogEntriesIncludingDeleted(null));
            assertThat(actual.get(0), is(new LogEntry()
                .setDeleted(true)
                .setLogLevel(INFO)
                .setType(RECEIVED_REQUEST)
                .setHttpRequest(request("request_one"))
                .setMessageFormat(RECEIVED_REQUEST_MESSAGE_FORMAT)
                .setArguments(request("request_one"))));
            assertThat(actual.get(1), is(new LogEntry()
                .setDeleted(true)
                .setLogLevel(INFO)
                .setType(NO_MATCH_RESPONSE)
                .setHttpRequest(request("request_one"))
                .setExpectation(new Expectation(request("request_one")).thenRespond(response("response_two")))
                .setMessageFormat("no expectation for:{}returning response:{}")
                .setArguments(request("request_one"), notFoundResponse())));
            assertThat(actual.get(2), is(new LogEntry()
                .setLogLevel(INFO)
                .setType(RECEIVED_REQUEST)
                .setHttpRequest(request("request_two"))
                .setMessageFormat(RECEIVED_REQUEST_MESSAGE_FORMAT)
                .setArguments(request("request_two"))));
            assertThat(actual.get(3), is(new LogEntry()
                .setType(EXPECTATION_MATCHED)
                .setLogLevel(INFO)
                .setHttpRequest(request("request_two"))
                .setExpectation(new Expectation(request("request_two")).thenRespond(response("response_two")))
                .setMessageFormat("request:{}matched expectation:{}")
                .setArguments(request("request_two"), new Expectation(request("request_two")).thenRespond(response("response_two")))));
            assertThat(actual.get(4), is(new LogEntry()
                .setType(EXPECTATION_RESPONSE)
                .setLogLevel(INFO)
                .setHttpRequest(request("request_two"))
                .setHttpResponse(response("response_two"))
                .setMessageFormat("request:{}matched expectation:{}")
                .setMessageFormat("returning response:{}for request:{}for action:{}")
                .setArguments(request("request_two"), response("response_two"), response("response_two"))));
            assertThat(actual.get(5), is(new LogEntry()
                .setType(TRACE)
                .setHttpRequest(request("request_four"))
                .setExpectation(new Expectation(request("request_four")).thenRespond(response("response_four")))
                .setMessageFormat("some random{}message")
                .setArguments("argument_one")));
            assertThat(actual.get(6), is(new LogEntry()
                .setType(FORWARDED_REQUEST)
                .setHttpRequest(request("request_five"))
                .setHttpResponse(response("response_five"))
                .setExpectation(request("request_five"), response("response_five"))));
            assertThat(actual.get(7), is(new LogEntry()
                .setType(CLEARED)
                .setHttpRequest(request("request_one"))
                .setMessageFormat("cleared logs that match:{}")
                    .setArguments(request("request_one"))));
    }

    @Test
    public void shouldReset() {
        // given
        configuration.logLevel(Level.INFO);
        mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(INFO)
                    .setType(NO_MATCH_RESPONSE)
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
                    .setMessageFormat("some random{}message")
                    .setArguments("argument_one")
            );

            // when
            mockServerEventLog.reset();

            // then
            assertThat(retrieveRequests(null), empty());
            assertThat(retrieveRecordedExpectations(null), empty());
            assertThat(retrieveMessageLogEntries(null), empty());
            assertThat(retrieveRequestLogEntries(), empty());
            assertThat(retrieveRequestResponseMessageLogEntries(null), empty());
    }

    // ------------------------------------------------------------------------------------------
    // Regression guards for GitHub issue #2359 — "CPU usage (still) increases over time".
    //
    // Under sustained load the request/event log fills to maxLogEntries and stays full (clearing
    // expectations does not clear the log, and at INFO level clear() only tombstones entries). The
    // periodic /retrieve and per-cycle clear must therefore NOT pay the cost of the expensive
    // request matcher (which clones the request and runs full field-by-field matching) for entries
    // that the cheap type/not-deleted predicate would discard for free — otherwise per-request CPU
    // climbs with the log size and stays high. These tests count how many entries the request
    // matcher is actually evaluated against (via the per-entry cloneWithLogCorrelationId() probe)
    // and assert it is gated by the cheap predicate, not run over the whole log.
    // ------------------------------------------------------------------------------------------

    /**
     * An {@link HttpRequest} that counts how many times the request matcher evaluates it.
     * {@link RequestDefinition#cloneWithLogCorrelationId()} is invoked exactly once per entry that
     * {@link LogEntry#matches(org.mockserver.matchers.HttpRequestMatcher)} (during retrieve) or
     * {@code clear(...)} evaluates against the matcher, so it is a precise probe for how many
     * entries the expensive matcher actually touched.
     */
    private static final class CountingHttpRequest extends HttpRequest {
        @JsonIgnore
        private final AtomicInteger matcherEvaluations;

        private CountingHttpRequest(AtomicInteger matcherEvaluations) {
            this.matcherEvaluations = matcherEvaluations;
        }

        @Override
        public RequestDefinition cloneWithLogCorrelationId() {
            matcherEvaluations.incrementAndGet();
            return super.cloneWithLogCorrelationId();
        }
    }

    private static CountingHttpRequest countingRequest(AtomicInteger counter, String path) {
        CountingHttpRequest request = new CountingHttpRequest(counter);
        request.withPath(path);
        return request;
    }

    private List<LogEntry> retrieveRequestLogEntries(RequestDefinition filter) {
        CompletableFuture<List<LogEntry>> future = new CompletableFuture<>();
        mockServerEventLog.retrieveRequestLogEntries(filter, future::complete);
        try {
            return future.get(60, SECONDS);
        } catch (Exception e) {
            fail(e.getMessage());
            return null;
        }
    }

    @Test
    public void shouldNotEvaluateRequestMatcherForEntriesFilteredOutByType() {
        // given - a mix of request and non-request entries, all carrying a counting request
        configuration.logLevel(Level.INFO);
        AtomicInteger matcherEvaluations = new AtomicInteger(0);
        mockServerLogger.logEvent(new LogEntry().setLogLevel(INFO).setType(RECEIVED_REQUEST).setHttpRequest(countingRequest(matcherEvaluations, "/one")));
        mockServerLogger.logEvent(new LogEntry().setLogLevel(INFO).setType(EXPECTATION_RESPONSE).setHttpRequest(countingRequest(matcherEvaluations, "/two")));
        mockServerLogger.logEvent(new LogEntry().setLogLevel(INFO).setType(RECEIVED_REQUEST).setHttpRequest(countingRequest(matcherEvaluations, "/three")));
        mockServerLogger.logEvent(new LogEntry().setLogLevel(INFO).setType(EXPECTATION_RESPONSE).setHttpRequest(countingRequest(matcherEvaluations, "/four")));
        mockServerLogger.logEvent(new LogEntry().setLogLevel(INFO).setType(RECEIVED_REQUEST).setHttpRequest(countingRequest(matcherEvaluations, "/five")));

        // and - a blocking null-filter retrieve drains the disruptor so every add is processed;
        // the counter is then reset to isolate the measurement to the "when" step below
        retrieveRequestLogEntries();
        matcherEvaluations.set(0);

        // when - retrieving request log entries with a filter that matches every entry (an empty filter
        // skips the matcher altogether, so it would measure nothing)
        List<LogEntry> requestLogEntries = retrieveRequestLogEntries(request().withPath("/.*"));

        // then - only the 3 RECEIVED_REQUEST entries are returned ...
        assertThat(requestLogEntries.size(), is(3));
        // ... and the expensive matcher was evaluated only for those 3, NOT for the 2 entries the
        // cheap type predicate discards (before the #2359 fix this would have been 5)
        assertThat(matcherEvaluations.get(), is(3));
    }

    @Test
    public void shouldNotEvaluateAnyRequestMatcherForAnEmptyFilter() {
        // given
        configuration.logLevel(Level.INFO);
        AtomicInteger matcherEvaluations = new AtomicInteger(0);
        mockServerLogger.logEvent(new LogEntry().setLogLevel(INFO).setType(RECEIVED_REQUEST).setHttpRequest(countingRequest(matcherEvaluations, "/one")));
        mockServerLogger.logEvent(new LogEntry().setLogLevel(INFO).setType(RECEIVED_REQUEST).setHttpRequest(countingRequest(matcherEvaluations, "/two")));

        // when - no filter and the empty filter {} both mean every entry
        List<LogEntry> unfiltered = retrieveRequestLogEntries(null);
        List<LogEntry> emptyFilter = retrieveRequestLogEntries(request());

        // then - every entry is returned without running a matcher
        assertThat(unfiltered.size(), is(2));
        assertThat(emptyFilter.size(), is(2));
        assertThat(matcherEvaluations.get(), is(0));
    }

    @Test
    public void shouldNotEvaluateRequestMatcherForDeletedTombstonesWhenRetrieving() {
        // given - request entries, half of which are then tombstoned by a clear (INFO => mark only)
        configuration.logLevel(Level.INFO);
        AtomicInteger matcherEvaluations = new AtomicInteger(0);
        mockServerLogger.logEvent(new LogEntry().setLogLevel(INFO).setType(RECEIVED_REQUEST).setHttpRequest(countingRequest(matcherEvaluations, "/keep")));
        mockServerLogger.logEvent(new LogEntry().setLogLevel(INFO).setType(RECEIVED_REQUEST).setHttpRequest(countingRequest(matcherEvaluations, "/delete")));
        mockServerLogger.logEvent(new LogEntry().setLogLevel(INFO).setType(RECEIVED_REQUEST).setHttpRequest(countingRequest(matcherEvaluations, "/keep")));
        mockServerLogger.logEvent(new LogEntry().setLogLevel(INFO).setType(RECEIVED_REQUEST).setHttpRequest(countingRequest(matcherEvaluations, "/delete")));

        // when - clear tombstones the two "/delete" entries (they remain physically in the log)
        mockServerEventLog.clear(request("/delete"));
        matcherEvaluations.set(0);

        // and - retrieving with a filter that matches every entry
        List<LogEntry> requestLogEntries = retrieveRequestLogEntries(request().withPath("/.*"));

        // then - only the 2 live entries are returned ...
        assertThat(requestLogEntries.size(), is(2));
        // ... and the matcher was evaluated only for those 2, NOT for the 2 deleted tombstones
        // (before the #2359 fix this would have been 4)
        assertThat(matcherEvaluations.get(), is(2));
    }

    @Test
    public void shouldNotReEvaluateAlreadyDeletedTombstonesOnRepeatedClear() {
        // given - 2 entries that will be tombstoned and 2 that will not, all counting requests
        configuration.logLevel(Level.INFO);
        AtomicInteger matcherEvaluations = new AtomicInteger(0);
        mockServerLogger.logEvent(new LogEntry().setLogLevel(INFO).setType(RECEIVED_REQUEST).setHttpRequest(countingRequest(matcherEvaluations, "/keep")));
        mockServerLogger.logEvent(new LogEntry().setLogLevel(INFO).setType(RECEIVED_REQUEST).setHttpRequest(countingRequest(matcherEvaluations, "/delete")));
        mockServerLogger.logEvent(new LogEntry().setLogLevel(INFO).setType(RECEIVED_REQUEST).setHttpRequest(countingRequest(matcherEvaluations, "/keep")));
        mockServerLogger.logEvent(new LogEntry().setLogLevel(INFO).setType(RECEIVED_REQUEST).setHttpRequest(countingRequest(matcherEvaluations, "/delete")));

        // when - the first clear evaluates every entry (none deleted yet) and tombstones the 2 "/delete"
        mockServerEventLog.clear(request("/delete"));
        assertThat(matcherEvaluations.get(), is(4));

        // and - a second identical clear runs again
        matcherEvaluations.set(0);
        mockServerEventLog.clear(request("/delete"));

        // then - the already-deleted tombstones are skipped, so only the 2 live entries are
        // evaluated (before the #2359 fix this would have re-scanned all 4)
        assertThat(matcherEvaluations.get(), is(2));
        // and the live entries are untouched
        assertThat(retrieveRequestLogEntries(request()).size(), is(2));
    }

    @Test
    public void shouldNotEvaluateRequestMatcherForEntriesFilteredOutByTypeOnReverseForUiPath() {
        // given - the dashboard reverse-order retrieve path with a mix of request/non-request entries
        configuration.logLevel(Level.INFO);
        AtomicInteger matcherEvaluations = new AtomicInteger(0);
        mockServerLogger.logEvent(new LogEntry().setLogLevel(INFO).setType(RECEIVED_REQUEST).setHttpRequest(countingRequest(matcherEvaluations, "/one")));
        mockServerLogger.logEvent(new LogEntry().setLogLevel(INFO).setType(EXPECTATION_RESPONSE).setHttpRequest(countingRequest(matcherEvaluations, "/two")));
        mockServerLogger.logEvent(new LogEntry().setLogLevel(INFO).setType(RECEIVED_REQUEST).setHttpRequest(countingRequest(matcherEvaluations, "/three")));
        mockServerLogger.logEvent(new LogEntry().setLogLevel(INFO).setType(EXPECTATION_RESPONSE).setHttpRequest(countingRequest(matcherEvaluations, "/four")));
        mockServerLogger.logEvent(new LogEntry().setLogLevel(INFO).setType(RECEIVED_REQUEST).setHttpRequest(countingRequest(matcherEvaluations, "/five")));

        // The UI reverse path no longer takes a disruptor-ordered snapshot, so force the five async
        // publishes to be processed and visible before the direct traversal, then reset the counter so it
        // measures ONLY the matcher evaluations the UI retrieve itself performs.
        retrieveRequestLogEntries(request());
        matcherEvaluations.set(0);

        // when - retrieving in reverse for the UI with a match-everything filter and a RECEIVED_REQUEST predicate
        CompletableFuture<List<LogEntry>> future = new CompletableFuture<>();
        mockServerEventLog.retrieveLogEntriesInReverseForUI(
            request().withPath("/.*"),
            entry -> !entry.isDeleted() && entry.getType() == RECEIVED_REQUEST,
            entry -> entry,
            stream -> future.complete(stream.collect(Collectors.toList()))
        );
        List<LogEntry> reverseEntries;
        try {
            reverseEntries = future.get(60, SECONDS);
        } catch (Exception e) {
            fail(e.getMessage());
            return;
        }

        // then - only the 3 RECEIVED_REQUEST entries are returned ...
        assertThat(reverseEntries.size(), is(3));
        // ... and the expensive matcher was evaluated only for those 3, NOT for the 2 entries the
        // cheap type predicate discards (before the #2359 fix this would have been 5)
        assertThat(matcherEvaluations.get(), is(3));
    }

    @Test
    public void dashboardReverseRetrieveDoesNotWaitOnTheDisruptorConsumerThread() throws Exception {
        // The dashboard reverse-for-UI retrieve used to take its snapshot on the single disruptor consumer
        // thread (a RUNNABLE that copied the whole log), so it queued behind everything that thread was
        // doing. It now traverses the deque's weakly-consistent descendingIterator directly, off that
        // thread. This proves the change: with the consumer thread pinned, the UI retrieve still completes
        // and returns the already-visible entry. Reverting to the snapshot copy makes it block behind the
        // pinned consumer and the future never completes, so the notNullValue assertion fails.
        configuration.logLevel(Level.INFO);

        // an already-processed, visible entry
        mockServerLogger.logEvent(new LogEntry().setLogLevel(INFO).setType(RECEIVED_REQUEST).setHttpRequest(request("/before")));
        retrieveRequestLogEntries(request()); // drain so /before is in the deque

        // pin the single disruptor consumer thread: recordedRequestConsumer runs on it, inside
        // processLogEntry, BEFORE the entry is added — so blocking here holds the consumer thread.
        CountDownLatch consumerPinned = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        mockServerEventLog.setRecordedRequestConsumer(entry -> {
            consumerPinned.countDown();
            try {
                release.await(10, SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        mockServerLogger.logEvent(new LogEntry().setLogLevel(INFO).setType(FORWARDED_REQUEST).setHttpRequest(request("/blocker")).setHttpResponse(response()));
        assertThat("consumer thread is pinned", consumerPinned.await(10, SECONDS), is(true));

        try {
            CompletableFuture<List<LogEntry>> future = new CompletableFuture<>();
            mockServerEventLog.retrieveLogEntriesInReverseForUI(
                request(),
                entry -> !entry.isDeleted() && entry.getType() == RECEIVED_REQUEST,
                entry -> entry,
                stream -> future.complete(stream.collect(Collectors.toList()))
            );
            List<LogEntry> entries;
            try {
                entries = future.get(5, SECONDS);
            } catch (TimeoutException timedOut) {
                entries = null; // blocked behind the pinned consumer thread
            }
            assertThat("reverse-for-UI retrieve completed without waiting on the pinned consumer thread", entries, is(notNullValue()));
            // only the visible RECEIVED_REQUEST is returned (the FORWARDED_REQUEST blocker is filtered by
            // the predicate and, being pinned before add(), is not in the deque anyway)
            assertThat(entries.size(), is(1));
            assertThat(entries.get(0).getHttpRequest(), is(request("/before")));
        } finally {
            release.countDown();
        }
    }
}
