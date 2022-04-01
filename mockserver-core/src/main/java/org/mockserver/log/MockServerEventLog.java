package org.mockserver.log;

import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.dsl.Disruptor;
import org.mockserver.collections.CircularConcurrentLinkedDeque;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.log.model.RequestAndExpectationId;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.listeners.MockServerEventLogNotifier;
import org.mockserver.model.ExpectationId;
import org.mockserver.model.LogEventRequestAndResponse;
import org.mockserver.model.RequestDefinition;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.RequestDefinitionSerializer;
import org.mockserver.uuid.UUIDService;
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
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.log.model.LogEntry.LogMessageType.*;
import static org.mockserver.log.model.LogEntryMessages.VERIFICATION_REQUESTS_MESSAGE_FORMAT;
import static org.mockserver.log.model.LogEntryMessages.VERIFICATION_REQUEST_SEQUENCES_MESSAGE_FORMAT;
import static org.mockserver.logging.MockServerLogger.writeToSystemOut;
import static org.mockserver.mock.HttpState.getPort;
import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("FieldMayBeFinal")
public class MockServerEventLog extends MockServerEventLogNotifier {

    private static final Logger logger = LoggerFactory.getLogger(MockServerEventLog.class);
    private static final Predicate<LogEntry> allPredicate = input
        -> true;
    private static final Predicate<LogEntry> notDeletedPredicate = input
        -> !input.isDeleted();
    private static final Predicate<LogEntry> requestLogPredicate = input
        -> !input.isDeleted() && input.getType() == RECEIVED_REQUEST;
    private static final Predicate<LogEntry> expectationLogPredicate = input
        -> !input.isDeleted() && (
        input.getType() == EXPECTATION_RESPONSE
            || input.getType() == FORWARDED_REQUEST
    );
    private static final Predicate<LogEntry> requestResponseLogPredicate = input
        -> !input.isDeleted() && (
        input.getType() == EXPECTATION_RESPONSE
            || input.getType() == NO_MATCH_RESPONSE
            || input.getType() == FORWARDED_REQUEST
    );
    private static final Predicate<LogEntry> recordedExpectationLogPredicate = input
        -> !input.isDeleted() && input.getType() == FORWARDED_REQUEST;
    private static final Function<LogEntry, RequestDefinition[]> logEntryToRequest = LogEntry::getHttpRequests;
    private static final Function<LogEntry, Expectation> logEntryToExpectation = LogEntry::getExpectation;
    private static final Function<LogEntry, LogEventRequestAndResponse> logEntryToHttpRequestAndHttpResponse =
        logEntry -> new LogEventRequestAndResponse()
            .withHttpRequest(logEntry.getHttpRequest())
            .withHttpResponse(logEntry.getHttpResponse())
            .withTimestamp(logEntry.getTimestamp());
    private static final String[] EXCLUDED_FIELDS = {"id", "disruptor"};
    private final Configuration configuration;
    private MockServerLogger mockServerLogger;
    private CircularConcurrentLinkedDeque<LogEntry> eventLog;
    private MatcherBuilder matcherBuilder;
    private RequestDefinitionSerializer requestDefinitionSerializer;
    private final boolean asynchronousEventProcessing;
    private Disruptor<LogEntry> disruptor;

    public MockServerEventLog(Configuration configuration, MockServerLogger mockServerLogger, Scheduler scheduler, boolean asynchronousEventProcessing) {
        super(scheduler);
        this.configuration = configuration;
        this.mockServerLogger = mockServerLogger;
        this.matcherBuilder = new MatcherBuilder(configuration, mockServerLogger);
        this.requestDefinitionSerializer = new RequestDefinitionSerializer(mockServerLogger);
        this.asynchronousEventProcessing = asynchronousEventProcessing;
        this.eventLog = new CircularConcurrentLinkedDeque<>(configuration.maxLogEntries(), LogEntry::clear);
        startRingBuffer();
    }

    public void add(LogEntry logEntry) {
        logEntry.setPort(getPort());
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

    private void startRingBuffer() {
        disruptor = new Disruptor<>(LogEntry::new, configuration.ringBufferSize(), new Scheduler.SchedulerThreadFactory("EventLog"));

        final ExceptionHandler<LogEntry> errorHandler = new ExceptionHandler<LogEntry>() {
            @Override
            public void handleEventException(Throwable ex, long sequence, LogEntry logEntry) {
                logger.error("exception handling log entry in log ring buffer, for log entry: " + logEntry, ex);
            }

            @Override
            public void handleOnStartException(Throwable ex) {
                logger.error("exception starting log ring buffer", ex);
            }

            @Override
            public void handleOnShutdownException(Throwable ex) {
                logger.error("exception during shutdown of log ring buffer", ex);
            }
        };
        disruptor.setDefaultExceptionHandler(errorHandler);

        disruptor.handleEventsWith((logEntry, sequence, endOfBatch) -> {
            if (logEntry.getType() != RUNNABLE) {
                processLogEntry(logEntry);
            } else {
                logEntry.getConsumer().run();
                logEntry.clear();
            }
        });

        disruptor.start();
    }

    private void processLogEntry(LogEntry logEntry) {
        logEntry = logEntry.cloneAndClear();
        eventLog.add(logEntry);
        notifyListeners(this, false);
        writeToSystemOut(logger, logEntry);
    }

    public void stop() {
        try {
            notifyListeners(this, true);
            eventLog.clear();
            disruptor.shutdown(2, SECONDS);
        } catch (Throwable throwable) {
            if (!(throwable instanceof com.lmax.disruptor.TimeoutException)) {
                if (MockServerLogger.isEnabled(Level.WARN)) {
                    writeToSystemOut(logger, new LogEntry()
                        .setLogLevel(Level.WARN)
                        .setMessageFormat("exception while shutting down log ring buffer")
                        .setThrowable(throwable)
                    );
                }
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
                notifyListeners(this, false);
            })
        );
        try {
            future.get(2, SECONDS);
        } catch (ExecutionException | InterruptedException | TimeoutException ignore) {
        }
    }

    public void clear(RequestDefinition requestDefinition) {
        CompletableFuture<String> future = new CompletableFuture<>();
        final boolean markAsDeletedOnly = MockServerLogger.isEnabled(Level.INFO);
        disruptor.publishEvent(new LogEntry()
            .setType(RUNNABLE)
            .setConsumer(() -> {
                String logCorrelationId = UUIDService.getUUID();
                RequestDefinition matcher = requestDefinition != null ? requestDefinition : request().withLogCorrelationId(logCorrelationId);
                HttpRequestMatcher requestMatcher = matcherBuilder.transformsToMatcher(matcher);
                for (LogEntry logEntry : new LinkedList<>(eventLog)) {
                    RequestDefinition[] requests = logEntry.getHttpRequests();
                    boolean matches = false;
                    if (requests != null) {
                        for (RequestDefinition request : requests) {
                            if (requestMatcher.matches(request.cloneWithLogCorrelationId())) {
                                matches = true;
                            }
                        }
                    } else {
                        matches = true;
                    }
                    if (matches) {
                        if (markAsDeletedOnly) {
                            logEntry.setDeleted(true);
                        } else {
                            eventLog.removeItem(logEntry);
                        }
                    }
                }
                if (MockServerLogger.isEnabled(Level.INFO)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setType(CLEARED)
                            .setLogLevel(Level.INFO)
                            .setCorrelationId(logCorrelationId)
                            .setHttpRequest(requestDefinition)
                            .setMessageFormat("cleared logs that match:{}")
                            .setArguments((requestDefinition == null ? "{}" : requestDefinition))
                    );
                }
                future.complete("done");
                notifyListeners(this, false);
            })
        );
        try {
            future.get(2, SECONDS);
        } catch (ExecutionException | InterruptedException | TimeoutException ignore) {
        }
    }

    public void retrieveMessageLogEntries(RequestDefinition requestDefinition, Consumer<List<LogEntry>> listConsumer) {
        retrieveLogEntries(
            requestDefinition,
            notDeletedPredicate,
            (Stream<LogEntry> logEventStream) -> listConsumer.accept(logEventStream.filter(Objects::nonNull).collect(Collectors.toList()))
        );
    }

    public void retrieveMessageLogEntriesIncludingDeleted(RequestDefinition requestDefinition, Consumer<List<LogEntry>> listConsumer) {
        retrieveLogEntries(
            requestDefinition,
            allPredicate,
            (Stream<LogEntry> logEventStream) -> listConsumer.accept(logEventStream.filter(Objects::nonNull).collect(Collectors.toList()))
        );
    }

    public void retrieveRequestLogEntries(RequestDefinition requestDefinition, Consumer<List<LogEntry>> listConsumer) {
        retrieveLogEntries(
            requestDefinition,
            requestLogPredicate,
            (Stream<LogEntry> logEventStream) -> listConsumer.accept(logEventStream.filter(Objects::nonNull).collect(Collectors.toList()))
        );
    }

    public void retrieveRequests(Verification verification, String logCorrelationId, Consumer<List<RequestDefinition>> listConsumer) {
        if (verification.getExpectationId() != null) {
            retrieveLogEntries(
                Collections.singletonList(verification.getExpectationId().getId()),
                expectationLogPredicate,
                logEntryToRequest,
                logEventStream -> listConsumer.accept(
                    logEventStream
                        .filter(Objects::nonNull)
                        .flatMap(Arrays::stream)
                        .collect(Collectors.toList())
                )
            );
        } else {
            retrieveLogEntries(
                verification.getHttpRequest().withLogCorrelationId(logCorrelationId),
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
    }

    public void retrieveAllRequests(boolean matchingExpectationsOnly, Consumer<List<RequestDefinition>> listConsumer) {
        if (matchingExpectationsOnly) {
            retrieveLogEntries(
                (List<String>) null,
                expectationLogPredicate,
                logEntryToRequest,
                logEventStream -> listConsumer.accept(
                    logEventStream
                        .filter(Objects::nonNull)
                        .flatMap(Arrays::stream)
                        .collect(Collectors.toList())
                )
            );
        } else {
            retrieveLogEntries(
                (RequestDefinition) null,
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
    }

    public void retrieveAllRequests(List<String> expectationIds, Consumer<List<RequestAndExpectationId>> listConsumer) {
        retrieveLogEntries(
            expectationIds,
            expectationLogPredicate,
            logEntry -> new RequestAndExpectationId(logEntry.getHttpRequest(), logEntry.getExpectationId()),
            logEventStream -> listConsumer.accept(
                logEventStream
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList())
            )
        );
    }

    public void retrieveRequests(RequestDefinition requestDefinition, Consumer<List<RequestDefinition>> listConsumer) {
        retrieveLogEntries(
            requestDefinition,
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

    public void retrieveRequests(ExpectationId expectationId, Consumer<List<RequestDefinition>> listConsumer) {
        retrieveLogEntries(
            expectationId != null ? Collections.singletonList(expectationId.getId()) : Collections.emptyList(),
            expectationLogPredicate,
            logEntryToRequest,
            logEventStream -> listConsumer.accept(
                logEventStream
                    .filter(Objects::nonNull)
                    .flatMap(Arrays::stream)
                    .collect(Collectors.toList())
            )
        );
    }

    public void retrieveRequests(List<String> expectationIds, Consumer<List<RequestDefinition>> listConsumer) {
        retrieveLogEntries(
            expectationIds,
            expectationLogPredicate,
            logEntryToRequest,
            logEventStream -> listConsumer.accept(
                logEventStream
                    .filter(Objects::nonNull)
                    .flatMap(Arrays::stream)
                    .collect(Collectors.toList())
            )
        );
    }

    public void retrieveRequestResponseMessageLogEntries(RequestDefinition requestDefinition, Consumer<List<LogEntry>> listConsumer) {
        retrieveLogEntries(
            requestDefinition,
            requestResponseLogPredicate,
            (Stream<LogEntry> logEventStream) -> listConsumer.accept(logEventStream.filter(Objects::nonNull).collect(Collectors.toList()))
        );
    }

    public void retrieveRequestResponses(RequestDefinition requestDefinition, Consumer<List<LogEventRequestAndResponse>> listConsumer) {
        retrieveLogEntries(
            requestDefinition,
            requestResponseLogPredicate,
            logEntryToHttpRequestAndHttpResponse,
            logEventStream -> listConsumer.accept(logEventStream.filter(Objects::nonNull).collect(Collectors.toList()))
        );
    }

    public void retrieveRecordedExpectationLogEntries(RequestDefinition requestDefinition, Consumer<List<LogEntry>> listConsumer) {
        retrieveLogEntries(
            requestDefinition,
            recordedExpectationLogPredicate,
            (Stream<LogEntry> logEventStream) -> listConsumer.accept(logEventStream.filter(Objects::nonNull).collect(Collectors.toList()))
        );
    }

    public void retrieveRecordedExpectations(RequestDefinition requestDefinition, Consumer<List<Expectation>> listConsumer) {
        retrieveLogEntries(
            requestDefinition,
            recordedExpectationLogPredicate,
            logEntryToExpectation,
            logEventStream -> listConsumer.accept(logEventStream.filter(Objects::nonNull).collect(Collectors.toList()))
        );
    }

    private void retrieveLogEntries(RequestDefinition requestDefinition, Predicate<LogEntry> logEntryPredicate, Consumer<Stream<LogEntry>> consumer) {
        disruptor.publishEvent(new LogEntry()
            .setType(RUNNABLE)
            .setConsumer(() -> {
                HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(requestDefinition);
                consumer.accept(this.eventLog
                    .stream()
                    .filter(logItem -> logItem.matches(httpRequestMatcher))
                    .filter(logEntryPredicate)
                );
            })
        );
    }

    private <T> void retrieveLogEntries(RequestDefinition requestDefinition, Predicate<LogEntry> logEntryPredicate, Function<LogEntry, T> logEntryMapper, Consumer<Stream<T>> consumer) {
        disruptor.publishEvent(new LogEntry()
            .setType(RUNNABLE)
            .setConsumer(() -> {
                RequestDefinition requestDefinitionMatcher = requestDefinition != null ? requestDefinition : request().withLogCorrelationId(UUIDService.getUUID());
                HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(requestDefinitionMatcher);
                consumer.accept(this.eventLog
                    .stream()
                    .filter(logItem -> logItem.matches(httpRequestMatcher))
                    .filter(logEntryPredicate)
                    .map(logEntryMapper)
                );
            })
        );
    }

    @SuppressWarnings("SameParameterValue")
    private <T> void retrieveLogEntries(List<String> expectationIds, Predicate<LogEntry> logEntryPredicate, Function<LogEntry, T> logEntryMapper, Consumer<Stream<T>> consumer) {
        disruptor.publishEvent(new LogEntry()
            .setType(RUNNABLE)
            .setConsumer(() -> consumer.accept(this.eventLog
                .stream()
                .filter(logEntryPredicate)
                .filter(logItem -> expectationIds == null || logItem.matchesAnyExpectationId(expectationIds))
                .map(logEntryMapper)
            ))
        );
    }

    public <T> void retrieveLogEntriesInReverseForUI(RequestDefinition requestDefinition, Predicate<LogEntry> logEntryPredicate, Function<LogEntry, T> logEntryMapper, Consumer<Stream<T>> consumer) {
        disruptor.publishEvent(new LogEntry()
            .setType(RUNNABLE)
            .setConsumer(() -> {
                HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(requestDefinition);
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
        final String logCorrelationId = UUIDService.getUUID();
        if (verification != null) {
            if (MockServerLogger.isEnabled(Level.INFO)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(VERIFICATION)
                        .setLogLevel(Level.INFO)
                        .setCorrelationId(logCorrelationId)
                        .setHttpRequest(verification.getHttpRequest())
                        .setMessageFormat(VERIFICATION_REQUESTS_MESSAGE_FORMAT)
                        .setArguments(verification)
                );
            }
            retrieveRequests(verification, logCorrelationId, httpRequests -> {
                try {
                    if (!verification.getTimes().matches(httpRequests.size())) {
                        boolean matchByExpectationId = verification.getExpectationId() != null;
                        retrieveAllRequests(matchByExpectationId, allRequests -> {
                            String failureMessage;
                            String serializedRequestToBeVerified = requestDefinitionSerializer.serialize(true, verification.getHttpRequest());
                            Integer maximumNumberOfRequestToReturnInVerificationFailure = verification.getMaximumNumberOfRequestToReturnInVerificationFailure() != null ? verification.getMaximumNumberOfRequestToReturnInVerificationFailure() : configuration.maximumNumberOfRequestToReturnInVerificationFailure();
                            if (allRequests.size() < maximumNumberOfRequestToReturnInVerificationFailure) {
                                String serializedAllRequestInLog = allRequests.size() == 1 ? requestDefinitionSerializer.serialize(true, allRequests.get(0)) : requestDefinitionSerializer.serialize(true, allRequests);
                                failureMessage = "Request not found " + verification.getTimes() + ", expected:<" + serializedRequestToBeVerified + "> but was:<" + serializedAllRequestInLog + ">";
                            } else {
                                failureMessage = "Request not found " + verification.getTimes() + ", expected:<" + serializedRequestToBeVerified + "> but was not found, found " + allRequests.size() + " other requests";
                            }
                            final Object[] arguments = new Object[]{verification.getHttpRequest(), allRequests.size() == 1 ? allRequests.get(0) : allRequests};
                            if (MockServerLogger.isEnabled(Level.INFO)) {
                                mockServerLogger.logEvent(
                                    new LogEntry()
                                        .setType(VERIFICATION_FAILED)
                                        .setLogLevel(Level.INFO)
                                        .setCorrelationId(logCorrelationId)
                                        .setHttpRequest(verification.getHttpRequest())
                                        .setMessageFormat("request not found " + verification.getTimes() + ", expected:{}but was:{}")
                                        .setArguments(arguments)
                                );
                            }
                            resultConsumer.accept(failureMessage);
                        });
                    } else {
                        if (MockServerLogger.isEnabled(Level.INFO)) {
                            mockServerLogger.logEvent(
                                new LogEntry()
                                    .setType(VERIFICATION_PASSED)
                                    .setLogLevel(Level.INFO)
                                    .setCorrelationId(logCorrelationId)
                                    .setHttpRequest(verification.getHttpRequest())
                                    .setMessageFormat("request:{}found " + verification.getTimes())
                                    .setArguments(verification.getHttpRequest())
                            );
                        }
                        resultConsumer.accept("");
                    }
                } catch (Throwable throwable) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setType(EXCEPTION)
                            .setCorrelationId(logCorrelationId)
                            .setMessageFormat("exception:{} while processing verification:{}")
                            .setArguments(throwable.getMessage(), verification)
                            .setThrowable(throwable)
                    );
                    resultConsumer.accept("exception while processing verification" + (isNotBlank(throwable.getMessage()) ? " " + throwable.getMessage() : ""));
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
        if (verificationSequence != null) {
            final String logCorrelationId = UUIDService.getUUID();
            if (MockServerLogger.isEnabled(Level.INFO)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(VERIFICATION)
                        .setLogLevel(Level.INFO)
                        .setCorrelationId(logCorrelationId)
                        .setHttpRequests(verificationSequence.getHttpRequests().toArray(new RequestDefinition[0]))
                        .setMessageFormat(VERIFICATION_REQUEST_SEQUENCES_MESSAGE_FORMAT)
                        .setArguments(verificationSequence)
                );
            }
            if (verificationSequence.getExpectationIds() != null && !verificationSequence.getExpectationIds().isEmpty()) {
                retrieveAllRequests(verificationSequence.getExpectationIds().stream().map(ExpectationId::getId).collect(Collectors.toList()), allRequests -> {
                    List<RequestDefinition> requestDefinitions = allRequests.stream().map(RequestAndExpectationId::getRequestDefinition).collect(Collectors.toList());
                    try {
                        String failureMessage = "";
                        int requestLogCounter = 0;
                        for (ExpectationId expectationId : verificationSequence.getExpectationIds()) {
                            if (expectationId != null) {
                                boolean foundRequest = false;
                                for (; !foundRequest && requestLogCounter < allRequests.size(); requestLogCounter++) {
                                    if (allRequests.get(requestLogCounter).matches(expectationId)) {
                                        // move on to next request
                                        foundRequest = true;
                                    }
                                }
                                if (!foundRequest) {
                                    failureMessage = verificationSequenceFailureMessage(verificationSequence, logCorrelationId, requestDefinitions);
                                    break;
                                }
                            }
                        }
                        verificationSequenceSuccessMessage(verificationSequence, resultConsumer, logCorrelationId, failureMessage);

                    } catch (Throwable throwable) {
                        verificationSequenceExceptionHandler(verificationSequence, resultConsumer, logCorrelationId, throwable, "exception:{} while processing verification sequence:{}", "exception while processing verification sequence");
                    }
                });
            } else {
                retrieveAllRequests(false, allRequests -> {
                    try {
                        String failureMessage = "";
                        int requestLogCounter = 0;
                        for (RequestDefinition verificationHttpRequest : verificationSequence.getHttpRequests()) {
                            if (verificationHttpRequest != null) {
                                verificationHttpRequest.withLogCorrelationId(logCorrelationId);
                                HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(verificationHttpRequest);
                                boolean foundRequest = false;
                                for (; !foundRequest && requestLogCounter < allRequests.size(); requestLogCounter++) {
                                    if (httpRequestMatcher.matches(allRequests.get(requestLogCounter).cloneWithLogCorrelationId())) {
                                        // move on to next request
                                        foundRequest = true;
                                    }
                                }
                                if (!foundRequest) {
                                    failureMessage = verificationSequenceFailureMessage(verificationSequence, logCorrelationId, allRequests);
                                    break;
                                }
                            }
                        }
                        verificationSequenceSuccessMessage(verificationSequence, resultConsumer, logCorrelationId, failureMessage);

                    } catch (Throwable throwable) {
                        verificationSequenceExceptionHandler(verificationSequence, resultConsumer, logCorrelationId, throwable, "exception:{} while processing verification sequence:{}", "exception while processing verification sequence");
                    }
                });
            }
        } else {
            resultConsumer.accept("");
        }
    }

    private void verificationSequenceSuccessMessage(VerificationSequence verificationSequence, Consumer<String> resultConsumer, String logCorrelationId, String failureMessage) {
        if (isBlank(failureMessage) && MockServerLogger.isEnabled(Level.INFO)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(VERIFICATION_PASSED)
                    .setLogLevel(Level.INFO)
                    .setCorrelationId(logCorrelationId)
                    .setMessageFormat("request sequence found:{}")
                    .setArguments(verificationSequence.getHttpRequests())
            );
        }
        resultConsumer.accept(failureMessage);
    }

    private String verificationSequenceFailureMessage(VerificationSequence verificationSequence, String logCorrelationId, List<RequestDefinition> allRequests) {
        String failureMessage;
        String serializedRequestToBeVerified = requestDefinitionSerializer.serialize(true, verificationSequence.getHttpRequests());
        Integer maximumNumberOfRequestToReturnInVerificationFailure = verificationSequence.getMaximumNumberOfRequestToReturnInVerificationFailure() != null ? verificationSequence.getMaximumNumberOfRequestToReturnInVerificationFailure() : configuration.maximumNumberOfRequestToReturnInVerificationFailure();
        if (allRequests.size() < maximumNumberOfRequestToReturnInVerificationFailure) {
            String serializedAllRequestInLog = allRequests.size() == 1 ? requestDefinitionSerializer.serialize(true, allRequests.get(0)) : requestDefinitionSerializer.serialize(true, allRequests);
            failureMessage = "Request sequence not found, expected:<" + serializedRequestToBeVerified + "> but was:<" + serializedAllRequestInLog + ">";
        } else {
            failureMessage = "Request sequence not found, expected:<" + serializedRequestToBeVerified + "> but was not found, found " + allRequests.size() + " other requests";
        }
        final Object[] arguments = new Object[]{verificationSequence.getHttpRequests(), allRequests.size() == 1 ? allRequests.get(0) : allRequests};
        if (MockServerLogger.isEnabled(Level.INFO)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(VERIFICATION_FAILED)
                    .setLogLevel(Level.INFO)
                    .setCorrelationId(logCorrelationId)
                    .setHttpRequests(verificationSequence.getHttpRequests().toArray(new RequestDefinition[0]))
                    .setMessageFormat("request sequence not found, expected:{}but was:{}")
                    .setArguments(arguments)
            );
        }
        return failureMessage;
    }

    private void verificationSequenceExceptionHandler(VerificationSequence verificationSequence, Consumer<String> resultConsumer, String logCorrelationId, Throwable throwable, String s, String s2) {
        mockServerLogger.logEvent(
            new LogEntry()
                .setType(EXCEPTION)
                .setCorrelationId(logCorrelationId)
                .setMessageFormat(s)
                .setArguments(throwable.getMessage(), verificationSequence)
                .setThrowable(throwable)
        );
        resultConsumer.accept(s2 + (isNotBlank(throwable.getMessage()) ? " " + throwable.getMessage() : ""));
    }

    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return EXCLUDED_FIELDS;
    }

}
