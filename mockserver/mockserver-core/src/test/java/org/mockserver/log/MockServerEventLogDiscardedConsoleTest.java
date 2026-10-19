package org.mockserver.log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpState;
import org.mockserver.model.Format;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.RetrieveType;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.verify.VerificationTimes;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.EXPECTATION_NOT_MATCHED;
import static org.mockserver.log.model.LogEntry.LogMessageType.EXPECTATION_RESPONSE;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.mock.HttpState.LOG_SEPARATOR;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.verify.Verification.verification;

/**
 * When nothing prints the event log's console output, the consumer does not render each entry's message; every
 * reader that needs the text then renders it from the entry, and reads exactly what it reads when the console
 * output is printed. Reconfigures the java.util.logging logger of {@link MockServerEventLog}, a JVM-wide
 * setting, so it runs in the sequential test phase.
 */
public class MockServerEventLogDiscardedConsoleTest {

    private static final long EPOCH = 1_700_000_000_123L;
    private static final String TIMESTAMP = "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}";
    private static final HttpRequest REQUEST = request("/orders").withMethod("POST").withHeader("Authorization", "Bearer abc").withBody("{\"id\":1,\n\"password\":\"secret\"}");
    private static final HttpResponse RESPONSE = response().withStatusCode(201).withBody("{\"created\":true}");
    private static final Expectation EXPECTATION = new Expectation(request("/orders")).withId("orders").thenRespond(RESPONSE);

    private final java.util.logging.Logger eventLogJulLogger = java.util.logging.Logger.getLogger(MockServerEventLog.class.getName());
    private Handler[] savedHandlers;
    private boolean savedUseParentHandlers;
    private java.util.logging.Level savedLevel;
    private final List<HttpState> httpStates = new ArrayList<>();
    private final List<ScheduledExecutorService> executors = new ArrayList<>();

    @Before
    public void saveLogging() {
        savedHandlers = eventLogJulLogger.getHandlers();
        savedUseParentHandlers = eventLogJulLogger.getUseParentHandlers();
        savedLevel = eventLogJulLogger.getLevel();
    }

    @After
    public void restoreLogging() {
        for (HttpState httpState : httpStates) {
            httpState.stop();
        }
        for (ScheduledExecutorService executor : executors) {
            executor.shutdownNow();
        }
        for (Handler handler : eventLogJulLogger.getHandlers()) {
            eventLogJulLogger.removeHandler(handler);
        }
        for (Handler handler : savedHandlers) {
            eventLogJulLogger.addHandler(handler);
        }
        eventLogJulLogger.setUseParentHandlers(savedUseParentHandlers);
        eventLogJulLogger.setLevel(savedLevel);
    }

    @Test
    public void shouldNotRenderOnTheConsumerWhenNothingPrintsAndRenderForEachReader() throws Exception {
        // given
        printTo(null);
        Configuration configuration = configuration().logLevel(Level.INFO).compactLogFormat(false);
        HttpState httpState = httpState(configuration);
        CountingArgument counted = new CountingArgument();

        // when
        List<LogEntry> logged = logScenario(httpState, counted);

        // then
        assertThat("rendered on the consumer", counted.renders.get(), is(0));
        String logs = retrieveLogs(httpState);
        assertThat("rendered by the LOGS retrieve", counted.renders.get(), is(1));
        assertThat(logs, is(expectedLogs(logged, configuration)));
        assertThat(verificationFailure(httpState), containsString("not found"));
    }

    @Test
    public void shouldGiveEachReaderTheSameTextWhetherOrNotTheConsoleIsPrinted() throws Exception {
        Configuration configuration = configuration().logLevel(Level.INFO).compactLogFormat(false).redactSecretsInLog(true);
        String[] discarded = readEverything(configuration, false);
        String[] printed = readEverything(configuration, true);

        for (int i = 0; i < discarded.length; i++) {
            assertThat("reader " + i, discarded[i], is(printed[i]));
        }
    }

    @Test
    public void shouldPrintTheRenderedMessageWhenAHandlerIsAttached() {
        // given
        CapturingHandler console = printTo(new CapturingHandler());
        Configuration configuration = configuration().logLevel(Level.INFO).compactLogFormat(false);
        HttpState httpState = httpState(configuration);
        CountingArgument counted = new CountingArgument();

        // when
        List<LogEntry> logged = logScenario(httpState, counted);

        // then
        assertThat("rendered on the consumer", counted.renders.get(), is(1));
        assertThat(console.messages(), is(messages(logged, configuration)));
    }

    private String[] readEverything(Configuration configuration, boolean printed) throws Exception {
        CapturingHandler console = printTo(printed ? new CapturingHandler() : null);
        HttpState httpState = httpState(configuration);
        CompletableFuture<List<String>> listenerMessages = listenerMessages(httpState, configuration, 4);
        List<LogEntry> logged = logScenario(httpState, new CountingArgument());
        String verificationFailure = verificationFailure(httpState);
        String logs = retrieveLogs(httpState);
        if (printed) {
            // the verification logs entries of its own after the scenario's
            assertThat(console.messages().subList(0, logged.size()), is(messages(logged, configuration)));
        }
        // the verification's own entries carry the time they were logged
        return new String[]{logs.replaceAll(TIMESTAMP, "<time>"), String.join(NEW_LINE, listenerMessages.get(10, TimeUnit.SECONDS)), verificationFailure};
    }

    private CapturingHandler printTo(CapturingHandler handler) {
        for (Handler existing : eventLogJulLogger.getHandlers()) {
            eventLogJulLogger.removeHandler(existing);
        }
        eventLogJulLogger.setUseParentHandlers(false);
        eventLogJulLogger.setLevel(java.util.logging.Level.INFO);
        if (handler != null) {
            eventLogJulLogger.addHandler(handler);
        }
        return handler;
    }

    private HttpState httpState(Configuration configuration) {
        Scheduler scheduler = mock(Scheduler.class);
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
        executors.add(executor);
        when(scheduler.getExecutorService()).thenReturn(executor);
        HttpState httpState = new HttpState(configuration, new MockServerLogger(configuration, MockServerLogger.class), scheduler);
        httpStates.add(httpState);
        return httpState;
    }

    private static List<LogEntry> logScenario(HttpState httpState, CountingArgument counted) {
        List<LogEntry> logged = Arrays.asList(
            new LogEntry().setType(RECEIVED_REQUEST).setLogLevel(Level.INFO).setEpochTime(EPOCH).setHttpRequest(REQUEST)
                .setMessageFormat("received request:{}").setArguments(REQUEST),
            new LogEntry().setType(EXPECTATION_NOT_MATCHED).setLogLevel(Level.INFO).setEpochTime(EPOCH).setHttpRequest(REQUEST).setExpectation(EXPECTATION)
                .setMessageFormat("request:{}didn't match expectation:{}because:{}").setArguments(REQUEST, EXPECTATION, "method didn't match"),
            new LogEntry().setType(EXPECTATION_RESPONSE).setLogLevel(Level.INFO).setEpochTime(EPOCH).setHttpRequest(REQUEST).setHttpResponse(RESPONSE)
                .setMessageFormat("returning response:{}for request:{}for action:{}from expectation:{}").setArguments(RESPONSE, REQUEST, RESPONSE, EXPECTATION.getId()),
            new LogEntry().setLogLevel(Level.INFO).setEpochTime(EPOCH).setMessageFormat("value:{}").setArguments(counted)
        );
        for (LogEntry entry : logged) {
            httpState.log(entry.clone());
        }
        // a retrieve is answered once the consumer has processed every entry published before it
        CompletableFuture<Integer> processed = new CompletableFuture<>();
        httpState.getMockServerLog().retrieveRequests((HttpRequest) null, requests -> processed.complete(requests.size()));
        try {
            processed.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return logged;
    }

    private static String verificationFailure(HttpState httpState) throws Exception {
        return httpState.getMockServerLog().verify(verification().withRequest(request("/absent")).withTimes(VerificationTimes.exactly(1))).get(10, TimeUnit.SECONDS);
    }

    private static CompletableFuture<List<String>> listenerMessages(HttpState httpState, Configuration configuration, int count) {
        CompletableFuture<List<String>> messages = new CompletableFuture<>();
        httpState.getMockServerLog().registerListener(log -> log.retrieveMessageLogEntries(null, entries -> {
            if (entries.size() >= count) {
                messages.complete(entries.stream().map(entry -> entry.getMessage(configuration)).collect(Collectors.toList()));
            }
        }));
        return messages;
    }

    private static List<String> messages(List<LogEntry> logged, Configuration configuration) {
        return logged.stream().map(entry -> entry.getMessage(configuration)).collect(Collectors.toList());
    }

    private static String expectedLogs(List<LogEntry> logged, Configuration configuration) {
        return logged.stream().map(entry -> entry.getTimestamp() + " - " + entry.getMessage(configuration)).collect(Collectors.joining(LOG_SEPARATOR)) + NEW_LINE;
    }

    private static String retrieveLogs(HttpState httpState) {
        return retrieve(httpState, RetrieveType.LOGS);
    }

    private static String retrieve(HttpState httpState, RetrieveType type) {
        HttpResponse response = httpState.retrieve(request("/mockserver/retrieve")
            .withMethod("PUT")
            .withQueryStringParameter("type", type.name())
            .withQueryStringParameter("format", Format.JSON.name()));
        assertThat(response.getStatusCode(), is(200));
        return response.getBodyAsString();
    }

    private static class CountingArgument {
        private final AtomicInteger renders = new AtomicInteger();

        @Override
        public String toString() {
            renders.incrementAndGet();
            return "counted";
        }
    }

    private static class CapturingHandler extends Handler {
        private final List<String> messages = new ArrayList<>();

        @Override
        public synchronized void publish(LogRecord record) {
            messages.add(record.getMessage());
        }

        synchronized List<String> messages() {
            return new ArrayList<>(messages);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
