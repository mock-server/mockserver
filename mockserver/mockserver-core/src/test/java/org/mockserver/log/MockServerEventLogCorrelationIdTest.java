package org.mockserver.log;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpState;
import org.mockserver.mock.RequestMatchers;
import org.mockserver.model.RequestDefinition;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;
import org.slf4j.event.Level;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static junit.framework.TestCase.fail;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.*;
import static org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause.API;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.verify.Verification.verification;
import static org.mockserver.verify.VerificationTimes.exactly;

public class MockServerEventLogCorrelationIdTest {

    private static final Scheduler scheduler = new Scheduler(configuration(), new MockServerLogger());
    private final Configuration configuration = configuration();
    private MockServerLogger mockServerLogger;
    private MockServerEventLog mockServerEventLog;

    @AfterClass
    public static void stopScheduler() {
        scheduler.shutdown();
    }

    @Before
    public void setupTestFixture() {
        HttpState httpState = new HttpState(configuration, new MockServerLogger(configuration, MockServerLogger.class), scheduler);
        mockServerLogger = httpState.getMockServerLogger();
        mockServerEventLog = httpState.getMockServerLog();
    }

    private String verify(Verification verification) {
        CompletableFuture<String> result = new CompletableFuture<>();
        mockServerEventLog.verify(verification, result::complete);
        try {
            return result.get(10, SECONDS);
        } catch (Exception e) {
            fail(e.getMessage());
            return null;
        }
    }

    private String verify(VerificationSequence verificationSequence) {
        CompletableFuture<String> result = new CompletableFuture<>();
        mockServerEventLog.verify(verificationSequence, result::complete);
        try {
            return result.get(10, SECONDS);
        } catch (Exception e) {
            fail(e.getMessage());
            return null;
        }
    }

    private List<LogEntry> retrieveMessageLogEntries(RequestDefinition requestDefinition) {
        CompletableFuture<List<LogEntry>> future = new CompletableFuture<>();
        mockServerEventLog.retrieveMessageLogEntries(requestDefinition, future::complete);
        try {
            return future.get(10, SECONDS);
        } catch (Exception e) {
            fail(e.getMessage());
            return null;
        }
    }

    @Test
    public void shouldAssignCorrelationIdToVerificationLogEntries() {
        configuration.logLevel(Level.INFO);
        mockServerLogger.logEvent(
            new LogEntry()
                .setHttpRequest(request("some_path"))
                .setType(RECEIVED_REQUEST)
        );

        assertThat(verify(
            verification()
                .withRequest(request("some_path"))
                .withTimes(exactly(1))
        ), is(""));

        List<LogEntry> logEntries = retrieveMessageLogEntries(null);
        List<LogEntry> verificationEntries = logEntries.stream()
            .filter(entry -> entry.getType() == VERIFICATION || entry.getType() == VERIFICATION_PASSED)
            .collect(Collectors.toList());
        assertThat(verificationEntries, hasSize(2));
        String correlationId = verificationEntries.get(0).getCorrelationId();
        assertThat(correlationId, is(notNullValue()));
        assertThat(verificationEntries.get(1).getCorrelationId(), is(correlationId));
    }

    @Test
    public void shouldAssignCorrelationIdToFailedVerificationLogEntries() {
        configuration.logLevel(Level.INFO);
        mockServerLogger.logEvent(
            new LogEntry()
                .setHttpRequest(request("some_path"))
                .setType(RECEIVED_REQUEST)
        );

        assertThat(verify(
            verification()
                .withRequest(request("some_other_path"))
                .withTimes(exactly(1))
        ), is(not("")));

        List<LogEntry> logEntries = retrieveMessageLogEntries(null);
        List<LogEntry> verificationEntries = logEntries.stream()
            .filter(entry -> entry.getType() == VERIFICATION || entry.getType() == VERIFICATION_FAILED)
            .collect(Collectors.toList());
        assertThat(verificationEntries, hasSize(2));
        String correlationId = verificationEntries.get(0).getCorrelationId();
        assertThat(correlationId, is(notNullValue()));
        assertThat(verificationEntries.get(1).getCorrelationId(), is(correlationId));
    }

    @Test
    public void shouldAssignUniqueCorrelationIdPerVerification() {
        configuration.logLevel(Level.INFO);
        mockServerLogger.logEvent(
            new LogEntry()
                .setHttpRequest(request("some_path"))
                .setType(RECEIVED_REQUEST)
        );

        verify(
            verification()
                .withRequest(request("some_path"))
                .withTimes(exactly(1))
        );
        verify(
            verification()
                .withRequest(request("some_path"))
                .withTimes(exactly(1))
        );

        List<LogEntry> logEntries = retrieveMessageLogEntries(null);
        List<LogEntry> verificationEntries = logEntries.stream()
            .filter(entry -> entry.getType() == VERIFICATION)
            .collect(Collectors.toList());
        assertThat(verificationEntries, hasSize(2));
        assertThat(
            verificationEntries.get(0).getCorrelationId(),
            is(not(verificationEntries.get(1).getCorrelationId()))
        );
    }

    @Test
    public void shouldAssignCorrelationIdToVerificationSequenceLogEntries() {
        configuration.logLevel(Level.INFO);
        mockServerLogger.logEvent(
            new LogEntry()
                .setHttpRequest(request("one"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerLogger.logEvent(
            new LogEntry()
                .setHttpRequest(request("two"))
                .setType(RECEIVED_REQUEST)
        );

        assertThat(verify(
            new VerificationSequence()
                .withRequests(request("one"), request("two"))
        ), is(""));

        List<LogEntry> logEntries = retrieveMessageLogEntries(null);
        List<LogEntry> verificationEntries = logEntries.stream()
            .filter(entry -> entry.getType() == VERIFICATION || entry.getType() == VERIFICATION_PASSED)
            .collect(Collectors.toList());
        assertThat(verificationEntries, hasSize(2));
        String correlationId = verificationEntries.get(0).getCorrelationId();
        assertThat(correlationId, is(notNullValue()));
        assertThat(verificationEntries.get(1).getCorrelationId(), is(correlationId));
    }

    @Test
    public void shouldAssignCorrelationIdToFailedVerificationSequenceLogEntries() {
        configuration.logLevel(Level.INFO);
        mockServerLogger.logEvent(
            new LogEntry()
                .setHttpRequest(request("one"))
                .setType(RECEIVED_REQUEST)
        );

        assertThat(verify(
            new VerificationSequence()
                .withRequests(request("one"), request("missing"))
        ), is(not("")));

        List<LogEntry> logEntries = retrieveMessageLogEntries(null);
        List<LogEntry> verificationEntries = logEntries.stream()
            .filter(entry -> entry.getType() == VERIFICATION || entry.getType() == VERIFICATION_FAILED)
            .collect(Collectors.toList());
        assertThat(verificationEntries, hasSize(2));
        String correlationId = verificationEntries.get(0).getCorrelationId();
        assertThat(correlationId, is(notNullValue()));
        assertThat(verificationEntries.get(1).getCorrelationId(), is(correlationId));
    }

    @Test
    public void shouldPropagateLogCorrelationIdFromRequestToLogEntry() {
        String correlationId = "test-correlation-id";
        LogEntry logEntry = new LogEntry()
            .setHttpRequest(request("some_path").withLogCorrelationId(correlationId))
            .setType(RECEIVED_REQUEST);
        assertThat(logEntry.getCorrelationId(), is(correlationId));
    }

    @Test
    public void shouldNotOverwriteCorrelationIdWhenRequestHasNoLogCorrelationId() {
        String existingCorrelationId = "existing-id";
        LogEntry logEntry = new LogEntry()
            .setCorrelationId(existingCorrelationId)
            .setHttpRequest(request("some_path"))
            .setType(RECEIVED_REQUEST);
        assertThat(logEntry.getCorrelationId(), is(existingCorrelationId));
    }

    @Test
    public void shouldAssignCorrelationIdToClearLogEntries() {
        configuration.logLevel(Level.INFO);
        mockServerLogger.logEvent(
            new LogEntry()
                .setHttpRequest(request("some_path"))
                .setType(RECEIVED_REQUEST)
        );

        mockServerEventLog.clear(request("some_path"));

        List<LogEntry> logEntries = retrieveMessageLogEntries(null);
        List<LogEntry> clearEntries = logEntries.stream()
            .filter(entry -> entry.getType() == CLEARED)
            .collect(Collectors.toList());
        assertThat(clearEntries, hasSize(1));
        assertThat(clearEntries.get(0).getCorrelationId(), is(notNullValue()));
    }

    @Test
    public void shouldAssignCorrelationIdToClearAllLogEntries() {
        configuration.logLevel(Level.INFO);
        mockServerLogger.logEvent(
            new LogEntry()
                .setHttpRequest(request("path_one"))
                .setType(RECEIVED_REQUEST)
        );

        mockServerEventLog.clear(null);

        List<LogEntry> logEntries = retrieveMessageLogEntries(null);
        List<LogEntry> clearEntries = logEntries.stream()
            .filter(entry -> entry.getType() == CLEARED)
            .collect(Collectors.toList());
        assertThat(clearEntries, hasSize(1));
        assertThat(clearEntries.get(0).getCorrelationId(), is(notNullValue()));
    }

    @Test
    public void shouldPropagateLogCorrelationIdThroughExpectationClear() {
        try {
            Configuration localConfiguration = configuration().logLevel(Level.INFO);
            HttpState httpState = new HttpState(localConfiguration, new MockServerLogger(localConfiguration, MockServerLogger.class), scheduler);
            RequestMatchers requestMatchers = httpState.getRequestMatchers();
            MockServerEventLog eventLog = httpState.getMockServerLog();

            requestMatchers.add(
                new Expectation(request().withPath("some_path"))
                    .thenRespond(response().withBody("some_body")),
                API
            );

            String testCorrelationId = "test-clear-correlation-id";
            requestMatchers.clear(request().withPath("some_path").withLogCorrelationId(testCorrelationId));

            CompletableFuture<List<LogEntry>> future = new CompletableFuture<>();
            eventLog.retrieveMessageLogEntries(null, future::complete);
            List<LogEntry> logEntries = future.get(10, SECONDS);
            List<LogEntry> removedEntries = logEntries.stream()
                .filter(entry -> entry.getType() == REMOVED_EXPECTATION)
                .collect(Collectors.toList());
            assertThat(removedEntries, hasSize(1));
            assertThat(removedEntries.get(0).getCorrelationId(), is(testCorrelationId));
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
}
