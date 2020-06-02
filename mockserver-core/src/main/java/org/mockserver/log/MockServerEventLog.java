package org.mockserver.log;

import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.dsl.Disruptor;
import org.mockserver.collections.CircularConcurrentLinkedDeque;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.LogEventRequestAndResponse;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.HttpRequestSerializer;
import org.mockserver.ui.MockServerEventLogNotifier;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockserver.log.model.LogEntry.LogMessageType.*;
import static org.mockserver.logging.MockServerLogger.writeToSystemOut;

/**
 * @author jamesdbloom
 */
public class MockServerEventLog extends MockServerEventLogNotifier {

    private static final Logger logger = LoggerFactory.getLogger(MockServerEventLog.class);
    private static final Predicate<LogEntry> requestLogPredicate = input
        -> input.getType() == RECEIVED_REQUEST;
    private static final Predicate<LogEntry> requestResponseLogPredicate = input
        -> input.getType() == EXPECTATION_RESPONSE
        || input.getType() == EXPECTATION_NOT_MATCHED_RESPONSE
        || input.getType() == FORWARDED_REQUEST;
    private static final Predicate<LogEntry> recordedExpectationLogPredicate = input
        -> input.getType() == FORWARDED_REQUEST;
    private static final Function<LogEntry, HttpRequest[]> logEntryToRequest = LogEntry::getHttpRequests;
    private static final Function<LogEntry, Expectation> logEntryToExpectation = LogEntry::getExpectation;
    private static final Function<LogEntry, LogEventRequestAndResponse> logEntryToHttpRequestAndHttpResponse =
        logEntry -> new LogEventRequestAndResponse()
            .withHttpRequest(logEntry.getHttpRequest())
            .withHttpResponse(logEntry.getHttpResponse())
            .withTimestamp(logEntry.getTimestamp());
    private static final String[] EXCLUDED_FIELDS = {"id", "disruptor"};
    private MockServerLogger mockServerLogger;
    private CircularConcurrentLinkedDeque<LogEntry> eventLog = new CircularConcurrentLinkedDeque<>(ConfigurationProperties.maxLogEntries(), LogEntry::clear);
    private MatcherBuilder matcherBuilder;
    private HttpRequestSerializer httpRequestSerializer;
    private final boolean asynchronousEventProcessing;
    private Disruptor<LogEntry> disruptor;

    public MockServerEventLog(MockServerLogger mockServerLogger, Scheduler scheduler, boolean asynchronousEventProcessing) {
        super(scheduler);
        this.mockServerLogger = mockServerLogger;
        this.matcherBuilder = new MatcherBuilder(mockServerLogger);
        this.httpRequestSerializer = new HttpRequestSerializer(mockServerLogger);
        this.asynchronousEventProcessing = asynchronousEventProcessing;
        startRingBuffer();
    }

    public void add(LogEntry logEntry) {
        if (asynchronousEventProcessing) {
            if (!disruptor.getRingBuffer().tryPublishEvent(logEntry)) {
                // if ring buffer full only write WARN and ERROR to logger
                if (logEntry.getLogLevel().toInt() >= Level.WARN.toInt()) {
                    logger.warn("Too many log events failed to add log event to ring buffer: " + logEntry);
                }
            }
        } else {
            processLogEntry(logEntry);
        }
    }

    public int size() {
        return eventLog.size();
    }

    public void setMaxSize(int maxSize) {
        eventLog.setMaxSize(maxSize);
    }

    private void startRingBuffer() {
        disruptor = new Disruptor<>(LogEntry::new, ConfigurationProperties.ringBufferSize(), new Scheduler.SchedulerThreadFactory("EventLog"));

        final ExceptionHandler<LogEntry> errorHandler = new ExceptionHandler<LogEntry>() {
            @Override
            public void handleEventException(Throwable ex, long sequence, LogEntry logEntry) {
                logger.error("Exception handling log entry in log ring buffer, for log entry: " + logEntry, ex);
            }

            @Override
            public void handleOnStartException(Throwable ex) {
                logger.error("Exception starting log ring buffer", ex);
            }

            @Override
            public void handleOnShutdownException(Throwable ex) {
                logger.error("Exception during shutdown of log ring buffer", ex);
            }
        };
        disruptor.setDefaultExceptionHandler(errorHandler);

        disruptor.handleEventsWith((logEntry, sequence, endOfBatch) -> {
            if (logEntry.getType() != RUNNABLE) {
                processLogEntry(logEntry);
            } else {
                logEntry.getConsumer().run();
            }
        });

        disruptor.start();
    }

    private void processLogEntry(LogEntry logEntry) {
        eventLog.add(logEntry);
        notifyListeners(this);
        writeToSystemOut(logger, logEntry);
    }

    public void stop() {
        try {
            disruptor.shutdown(2, SECONDS);
        } catch (Throwable throwable) {
            if (!(throwable instanceof com.lmax.disruptor.TimeoutException)) {
                writeToSystemOut(logger, new LogEntry()
                    .setLogLevel(Level.WARN)
                    .setMessageFormat("exception while shutting down log ring buffer")
                    .setThrowable(throwable)
                );
            }
        }
    }

    public void reset() {
        CompletableFuture<String> future = new CompletableFuture<>();
        disruptor.publishEvent(new LogEntry()
            .setType(RUNNABLE)
            .setConsumer(() -> {
                eventLog.clear();
                future.complete("done");
                notifyListeners(this);
            })
        );
        try {
            future.get(2, SECONDS);
        } catch (ExecutionException | InterruptedException | TimeoutException ignore) {
        }
    }

    public void clear(HttpRequest httpRequest) {
        CompletableFuture<String> future = new CompletableFuture<>();
        disruptor.publishEvent(new LogEntry()
            .setType(RUNNABLE)
            .setConsumer(() -> {
                if (httpRequest != null) {
                    HttpRequestMatcher requestMatcher = matcherBuilder.transformsToMatcher(httpRequest);
                    for (LogEntry logEntry : new LinkedList<>(eventLog)) {
                        HttpRequest[] requests = logEntry.getHttpRequests();
                        boolean matches = false;
                        if (requests != null) {
                            for (HttpRequest request : requests) {
                                if (requestMatcher.matches(request)) {
                                    matches = true;
                                }
                            }
                        } else {
                            matches = true;
                        }
                        if (matches) {
                            eventLog.removeItem(logEntry);
                        }
                    }
                } else {
                    eventLog.clear();
                }
                future.complete("done");
                notifyListeners(this);
            })
        );
        try {
            future.get(2, SECONDS);
        } catch (ExecutionException | InterruptedException | TimeoutException ignore) {
        }
    }

    public void retrieveMessageLogEntries(HttpRequest httpRequest, Consumer<List<LogEntry>> listConsumer) {
        retrieveLogEntries(
            httpRequest,
            logEntry -> true,
            (Stream<LogEntry> logEventStream) -> listConsumer.accept(logEventStream.filter(Objects::nonNull).collect(Collectors.toList()))
        );
    }

    public void retrieveRequestLogEntries(HttpRequest httpRequest, Consumer<List<LogEntry>> listConsumer) {
        retrieveLogEntries(
            httpRequest,
            requestLogPredicate,
            (Stream<LogEntry> logEventStream) -> listConsumer.accept(logEventStream.filter(Objects::nonNull).collect(Collectors.toList()))
        );
    }

    public void retrieveRequests(HttpRequest httpRequest, Consumer<List<HttpRequest>> listConsumer) {
        retrieveLogEntries(
            httpRequest,
            requestLogPredicate,
            logEntryToRequest,
            logEventStream -> listConsumer.accept(
                logEventStream
                    .filter(Objects::nonNull)
                    .flatMap(Arrays::stream)
                    .collect(Collectors.toList())
            )
        );
    }

    public void retrieveRequestResponseMessageLogEntries(HttpRequest httpRequest, Consumer<List<LogEntry>> listConsumer) {
        retrieveLogEntries(
            httpRequest,
            requestResponseLogPredicate,
            (Stream<LogEntry> logEventStream) -> listConsumer.accept(logEventStream.filter(Objects::nonNull).collect(Collectors.toList()))
        );
    }

    public void retrieveRequestResponses(HttpRequest httpRequest, Consumer<List<LogEventRequestAndResponse>> listConsumer) {
        retrieveLogEntries(
            httpRequest,
            requestResponseLogPredicate,
            logEntryToHttpRequestAndHttpResponse,
            logEventStream -> listConsumer.accept(logEventStream.filter(Objects::nonNull).collect(Collectors.toList()))
        );
    }

    public void retrieveRecordedExpectationLogEntries(HttpRequest httpRequest, Consumer<List<LogEntry>> listConsumer) {
        retrieveLogEntries(
            httpRequest,
            recordedExpectationLogPredicate,
            (Stream<LogEntry> logEventStream) -> listConsumer.accept(logEventStream.filter(Objects::nonNull).collect(Collectors.toList()))
        );
    }

    public void retrieveRecordedExpectations(HttpRequest httpRequest, Consumer<List<Expectation>> listConsumer) {
        retrieveLogEntries(
            httpRequest,
            recordedExpectationLogPredicate,
            logEntryToExpectation,
            logEventStream -> listConsumer.accept(logEventStream.filter(Objects::nonNull).collect(Collectors.toList()))
        );
    }

    private void retrieveLogEntries(HttpRequest httpRequest, Predicate<LogEntry> logEntryPredicate, Consumer<Stream<LogEntry>> consumer) {
        disruptor.publishEvent(new LogEntry()
            .setType(RUNNABLE)
            .setConsumer(() -> {
                HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(httpRequest);
                consumer.accept(this.eventLog
                    .stream()
                    .filter(logItem -> logItem.matches(httpRequestMatcher))
                    .filter(logEntryPredicate)
                );
            })
        );
    }

    private <T> void retrieveLogEntries(HttpRequest httpRequest, Predicate<LogEntry> logEntryPredicate, Function<LogEntry, T> logEntryMapper, Consumer<Stream<T>> consumer) {
        disruptor.publishEvent(new LogEntry()
            .setType(RUNNABLE)
            .setConsumer(() -> {
                HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(httpRequest);
                consumer.accept(this.eventLog
                    .stream()
                    .filter(logItem -> logItem.matches(httpRequestMatcher))
                    .filter(logEntryPredicate)
                    .map(logEntryMapper)
                );
            })
        );
    }

    public <T> void retrieveLogEntriesInReverse(HttpRequest httpRequest, Predicate<LogEntry> logEntryPredicate, Function<LogEntry, T> logEntryMapper, Consumer<Stream<T>> consumer) {
        disruptor.publishEvent(new LogEntry()
            .setType(RUNNABLE)
            .setConsumer(() -> {
                HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(httpRequest);
                consumer.accept(
                    StreamSupport
                        .stream(Spliterators.spliteratorUnknownSize(this.eventLog.descendingIterator(), 0), false)
                        .filter(logItem -> logItem.matches(httpRequestMatcher))
                        .filter(logEntryPredicate)
                        .map(logEntryMapper)
                );
            })
        );
    }

    public Future<String> verify(Verification verification) {
        CompletableFuture<String> result = new CompletableFuture<>();
        verify(verification, result::complete);
        return result;
    }

    public void verify(Verification verification, Consumer<String> resultConsumer) {
        if (verification != null) {
            retrieveRequests(verification.getHttpRequest(), httpRequests -> {
                if (!verification.getTimes().matches(httpRequests.size())) {
                    retrieveRequests(null, allRequests -> {
                        String failureMessage;
                        String serializedRequestToBeVerified = httpRequestSerializer.serialize(true, verification.getHttpRequest());
                        String serializedAllRequestInLog = allRequests.size() == 1 ? httpRequestSerializer.serialize(true, allRequests.get(0)) : httpRequestSerializer.serialize(true, allRequests);
                        failureMessage = "Request not found " + verification.getTimes() + ", expected:<" + serializedRequestToBeVerified + "> but was:<" + serializedAllRequestInLog + ">";
                        final Object[] arguments = new Object[]{verification.getHttpRequest(), allRequests.size() == 1 ? allRequests.get(0) : allRequests};
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setType(VERIFICATION_FAILED)
                                .setLogLevel(Level.INFO)
                                .setHttpRequest(verification.getHttpRequest())
                                .setMessageFormat("request not found " + verification.getTimes() + ", expected:{}but was:{}")
                                .setArguments(arguments)
                        );
                        resultConsumer.accept(failureMessage);
                    });
                } else {
                    resultConsumer.accept("");
                }
            });
        } else {
            resultConsumer.accept("");
        }
    }

    public Future<String> verify(VerificationSequence verification) {
        CompletableFuture<String> result = new CompletableFuture<>();
        verify(verification, result::complete);
        return result;
    }

    public void verify(VerificationSequence verificationSequence, Consumer<String> resultConsumer) {
        retrieveRequests(null, allRequests -> {
            String failureMessage = "";
            if (verificationSequence != null) {
                int requestLogCounter = 0;
                for (HttpRequest verificationHttpRequest : verificationSequence.getHttpRequests()) {
                    if (verificationHttpRequest != null) {
                        HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(verificationHttpRequest);
                        boolean foundRequest = false;
                        for (; !foundRequest && requestLogCounter < allRequests.size(); requestLogCounter++) {
                            if (httpRequestMatcher.matches(allRequests.get(requestLogCounter))) {
                                // move on to next request
                                foundRequest = true;
                            }
                        }
                        if (!foundRequest) {
                            String serializedRequestToBeVerified = httpRequestSerializer.serialize(true, verificationSequence.getHttpRequests());
                            String serializedAllRequestInLog = allRequests.size() == 1 ? httpRequestSerializer.serialize(true, allRequests.get(0)) : httpRequestSerializer.serialize(true, allRequests);
                            failureMessage = "Request sequence not found, expected:<" + serializedRequestToBeVerified + "> but was:<" + serializedAllRequestInLog + ">";
                            final Object[] arguments = new Object[]{verificationSequence.getHttpRequests(), allRequests.size() == 1 ? allRequests.get(0) : allRequests};
                            mockServerLogger.logEvent(
                                new LogEntry()
                                    .setType(VERIFICATION_FAILED)
                                    .setLogLevel(Level.INFO)
                                    .setHttpRequests(verificationSequence.getHttpRequests().toArray(new HttpRequest[0]))
                                    .setMessageFormat("request sequence not found, expected:{}but was:{}")
                                    .setArguments(arguments)
                            );
                            break;
                        }
                    }
                }
            }
            resultConsumer.accept(failureMessage);
        });
    }

    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return EXCLUDED_FIELDS;
    }

}
