package org.mockserver.log;

import com.google.common.util.concurrent.SettableFuture;
import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.dsl.Disruptor;
import org.mockserver.collections.BoundedConcurrentLinkedQueue;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpRequestAndHttpResponse;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.HttpRequestSerializer;
import org.mockserver.ui.MockServerEventLogNotifier;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockserver.configuration.ConfigurationProperties.requestLogSize;
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
    private static final Function<LogEntry, Expectation> logEntryToExpectation = logEntry -> {
        if (logEntry.getExpectation() != null) {
            return logEntry.getExpectation();
        } else {
            return new Expectation(logEntry.getHttpRequest(), Times.once(), TimeToLive.unlimited()).thenRespond(logEntry.getHttpResponse());
        }
    };
    private static final Function<LogEntry, HttpRequestAndHttpResponse> logEntryToHttpRequestAndHttpResponse =
        logEntry -> new HttpRequestAndHttpResponse()
            .setHttpRequest(logEntry.getHttpRequest())
            .setHttpResponse(logEntry.getHttpResponse())
            .setTimestamp(logEntry.getTimestamp());
    private static final String[] EXCLUDED_FIELDS = {"key", "disruptor"};
    private MockServerLogger mockServerLogger;
    private Queue<LogEntry> requestLog = new BoundedConcurrentLinkedQueue<>(requestLogSize());
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
            // only try to publish so if it fails log is lost
            if (!disruptor.getRingBuffer().tryPublishEvent(logEntry)) {
                logger.error("Failed to add event too log ring buffer, for event: " + logEntry);
            }
        } else {
            processLogEntry(logEntry);
        }
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

    @SuppressWarnings("DuplicatedCode")
    private void processLogEntry(LogEntry logEntry) {
        requestLog.add(logEntry);
        notifyListeners(this);
        writeToSystemOut(logger, logEntry);
    }

    public void stop() {
        try {
            disruptor.shutdown(500, MILLISECONDS);
        } catch (Throwable throwable) {
            writeToSystemOut(logger, new LogEntry()
                .setLogLevel(Level.WARN)
                .setMessageFormat("Exception while shutting down log ring buffer")
                .setThrowable(throwable)
            );
        }
    }

    public void reset() {
        SettableFuture<String> future = SettableFuture.create();
        disruptor.publishEvent(new LogEntry()
            .setType(RUNNABLE)
            .setConsumer(() -> {
                requestLog.clear();
                future.set("done");
                notifyListeners(this);
            })
        );
        try {
            future.get(2, SECONDS);
        } catch (ExecutionException | InterruptedException | TimeoutException ignore) {
        }
    }

    public void clear(HttpRequest httpRequest) {
        SettableFuture<String> future = SettableFuture.create();
        disruptor.publishEvent(new LogEntry()
            .setType(RUNNABLE)
            .setConsumer(() -> {
                if (httpRequest != null) {
                    HttpRequestMatcher requestMatcher = matcherBuilder.transformsToMatcher(httpRequest);
                    for (LogEntry logEntry : new LinkedList<>(requestLog)) {
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
                            requestLog.remove(logEntry);
                        }
                    }
                } else {
                    requestLog.clear();
                }
                future.set("done");
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
            (Stream<LogEntry> logEventStream) -> listConsumer.accept(logEventStream.collect(Collectors.toList()))
        );
    }

    public void retrieveRequestLogEntries(HttpRequest httpRequest, Consumer<List<LogEntry>> listConsumer) {
        retrieveLogEntries(
            httpRequest,
            requestLogPredicate,
            (Stream<LogEntry> logEventStream) -> listConsumer.accept(logEventStream.collect(Collectors.toList()))
        );
    }

    public void retrieveRequests(HttpRequest httpRequest, Consumer<List<HttpRequest>> listConsumer) {
        retrieveLogEntries(
            httpRequest,
            requestLogPredicate,
            logEntryToRequest,
            logEventStream -> listConsumer.accept(logEventStream.flatMap(Arrays::stream).collect(Collectors.toList()))
        );
    }

    public void retrieveRequestResponseMessageLogEntries(HttpRequest httpRequest, Consumer<List<LogEntry>> listConsumer) {
        retrieveLogEntries(
            httpRequest,
            requestResponseLogPredicate,
            (Stream<LogEntry> logEventStream) -> listConsumer.accept(logEventStream.collect(Collectors.toList()))
        );
    }

    public void retrieveRequestResponses(HttpRequest httpRequest, Consumer<List<HttpRequestAndHttpResponse>> listConsumer) {
        retrieveLogEntries(
            httpRequest,
            requestResponseLogPredicate,
            logEntryToHttpRequestAndHttpResponse,
            logEventStream -> listConsumer.accept(logEventStream.collect(Collectors.toList()))
        );
    }

    public void retrieveRecordedExpectationLogEntries(HttpRequest httpRequest, Consumer<List<LogEntry>> listConsumer) {
        retrieveLogEntries(
            httpRequest,
            recordedExpectationLogPredicate,
            (Stream<LogEntry> logEventStream) -> listConsumer.accept(logEventStream.collect(Collectors.toList()))
        );
    }

    public void retrieveRecordedExpectations(HttpRequest httpRequest, Consumer<List<Expectation>> listConsumer) {
        retrieveLogEntries(
            httpRequest,
            recordedExpectationLogPredicate,
            logEntryToExpectation,
            logEventStream -> listConsumer.accept(logEventStream.collect(Collectors.toList()))
        );
    }

    private void retrieveLogEntries(HttpRequest httpRequest, Predicate<LogEntry> logEntryPredicate, Consumer<Stream<LogEntry>> consumer) {
        disruptor.publishEvent(new LogEntry()
            .setType(RUNNABLE)
            .setConsumer(() -> {
                HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(httpRequest);
                consumer.accept(this.requestLog
                    .stream()
                    .filter(logItem -> Arrays
                        .stream(logItem.getHttpRequests())
                        .anyMatch(httpRequestMatcher::matches)
                    )
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
                consumer.accept(this.requestLog
                    .stream()
                    .filter(logItem -> Arrays
                        .stream(logItem.getHttpRequests())
                        .anyMatch(httpRequestMatcher::matches)
                    )
                    .filter(logEntryPredicate)
                    .map(logEntryMapper)
                );
            })
        );
    }

    public <T> void retrieveLogEntriesInReverse(HttpRequest httpRequest, Predicate<LogEntry> logEntryPredicate, Function<LogEntry, T> logEntryMapper, Consumer<Deque<T>> consumer) {
        disruptor.publishEvent(new LogEntry()
            .setType(RUNNABLE)
            .setConsumer(() -> {
                HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(httpRequest);
                consumer.accept(
                    this.requestLog
                        .stream()
                        .filter(logItem -> Arrays
                            .stream(logItem.getHttpRequests())
                            .anyMatch(httpRequestMatcher::matches)
                        )
                        .filter(logEntryPredicate)
                        .map(logEntryMapper)
                        .collect(Collector.of(
                            ArrayDeque::new,
                            ArrayDeque::addFirst,
                            (d1, d2) -> {
                                d2.addAll(d1);
                                return d2;
                            }
                        ))
                );
            })
        );
    }

    public Future<String> verify(Verification verification) {
        SettableFuture<String> result = SettableFuture.create();
        verify(verification, result::set);
        return result;
    }

    public void verify(Verification verification, Consumer<String> resultConsumer) {
        if (verification != null) {
            retrieveRequests(verification.getHttpRequest(), httpRequests -> {
                if (!verification.getTimes().matches(httpRequests.size())) {
                    retrieveRequests(null, allRequestsArray -> {
                        String failureMessage = "";
                        String serializedRequestToBeVerified = httpRequestSerializer.serialize(true, verification.getHttpRequest());
                        String serializedAllRequestInLog = allRequestsArray.size() == 1 ? httpRequestSerializer.serialize(true, allRequestsArray.get(0)) : httpRequestSerializer.serialize(true, allRequestsArray);
                        failureMessage = "Request not found " + verification.getTimes() + ", expected:<" + serializedRequestToBeVerified + "> but was:<" + serializedAllRequestInLog + ">";
                        final Object[] arguments = new Object[]{verification.getHttpRequest(), allRequestsArray.size() == 1 ? allRequestsArray.get(0) : allRequestsArray};
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
        SettableFuture<String> result = SettableFuture.create();
        verify(verification, result::set);
        return result;
    }

    public void verify(VerificationSequence verificationSequence, Consumer<String> resultConsumer) {
        retrieveRequests(null, requestLog -> {
            String failureMessage = "";
            if (verificationSequence != null) {
                int requestLogCounter = 0;
                for (HttpRequest verificationHttpRequest : verificationSequence.getHttpRequests()) {
                    if (verificationHttpRequest != null) {
                        HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(verificationHttpRequest);
                        boolean foundRequest = false;
                        for (; !foundRequest && requestLogCounter < requestLog.size(); requestLogCounter++) {
                            if (httpRequestMatcher.matches(requestLog.get(requestLogCounter))) {
                                // move on to next request
                                foundRequest = true;
                            }
                        }
                        if (!foundRequest) {
                            String serializedRequestToBeVerified = httpRequestSerializer.serialize(true, verificationSequence.getHttpRequests());
                            String serializedAllRequestInLog = requestLog.size() == 1 ? httpRequestSerializer.serialize(true, requestLog.get(0)) : httpRequestSerializer.serialize(true, requestLog);
                            failureMessage = "Request sequence not found, expected:<" + serializedRequestToBeVerified + "> but was:<" + serializedAllRequestInLog + ">";
                            final Object[] arguments = new Object[]{verificationSequence.getHttpRequests(), requestLog.size() == 1 ? requestLog.get(0) : requestLog};
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
