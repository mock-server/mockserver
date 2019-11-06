package org.mockserver.log;

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;
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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ThreadFactory;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
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
    private static final Function<LogEntry, List<HttpRequest>> logEntryToRequest = LogEntry::getHttpRequests;
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
        initialiseRingBuffer();
    }

    @SuppressWarnings("DuplicatedCode")
    private void processLogEntry(LogEntry logEntry) {
        requestLog.add(logEntry);
        notifyListeners(this);
        writeToSystemOut(logger, logEntry);
    }

    private void initialiseRingBuffer() {
        EventFactory<LogEntry> eventFactory = LogEntry::new;
        ThreadFactory threadFactory = runnable -> new Thread();

        disruptor = new Disruptor<>(eventFactory, ConfigurationProperties.ringBufferSize(), DaemonThreadFactory.INSTANCE);

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
            processLogEntry(logEntry);
        });

        disruptor.start();
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

    public void reset() {
        requestLog.clear();
        notifyListeners(this);
    }

    public void clear(HttpRequest httpRequest) {
        if (httpRequest != null) {
            HttpRequestMatcher requestMatcher = matcherBuilder.transformsToMatcher(httpRequest);
            for (LogEntry logEntry : new LinkedList<>(requestLog)) {
                List<HttpRequest> requests = logEntry.getHttpRequests();
                boolean matches = false;
                for (HttpRequest request : requests) {
                    if (requestMatcher.matches(request)) {
                        matches = true;
                    }
                }
                if (matches) {
                    requestLog.remove(logEntry);
                }
            }
        } else {
            reset();
        }
        notifyListeners(this);
    }

    public List<LogEntry> retrieveMessageLogEntries(HttpRequest httpRequest) {
        return retrieveLogEntries(httpRequest, logEntry -> true);
    }

    public List<LogEntry> retrieveRequestLogEntries(HttpRequest httpRequest) {
        return retrieveLogEntries(httpRequest, requestLogPredicate);
    }

    public List<HttpRequest> retrieveRequests(HttpRequest httpRequest) {
        List<HttpRequest> httpRequests = new ArrayList<>();
        retrieveLogEntries(httpRequest, requestLogPredicate, logEntryToRequest).forEach(httpRequests::addAll);
        return httpRequests;
    }

    public List<LogEntry> retrieveRequestResponseMessageLogEntries(HttpRequest httpRequest) {
        return retrieveLogEntries(httpRequest, requestResponseLogPredicate);
    }

    public List<HttpRequestAndHttpResponse> retrieveRequestResponses(HttpRequest httpRequest) {
        return retrieveLogEntries(httpRequest, requestResponseLogPredicate, logEntryToHttpRequestAndHttpResponse);
    }

    public List<LogEntry> retrieveRecordedExpectationLogEntries(HttpRequest httpRequest) {
        return retrieveLogEntries(httpRequest, recordedExpectationLogPredicate);
    }

    public List<Expectation> retrieveRecordedExpectations(HttpRequest httpRequest) {
        return retrieveLogEntries(httpRequest, recordedExpectationLogPredicate, logEntryToExpectation);
    }

    private List<LogEntry> retrieveLogEntries(HttpRequest httpRequest, Predicate<LogEntry> logEntryPredicate) {
        HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(httpRequest);
        return this.requestLog
            .stream()
            .filter(logEntry ->
                logEntry
                    .getHttpRequests()
                    .stream()
                    .anyMatch(request -> logEntryPredicate.test(logEntry) && httpRequestMatcher.matches(request))
            )
            .collect(Collectors.toList());
    }

    private <T> List<T> retrieveLogEntries(HttpRequest httpRequest, Predicate<LogEntry> logEntryPredicate, Function<LogEntry, T> logEntryMapper) {
        HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(httpRequest);
        try {
            //TODO(jamesdbloom) fix wait for disruptor to update here
            MILLISECONDS.sleep(10);
        } catch (InterruptedException ignore) {
            // ignore
        }
        return this.requestLog
            .stream()
            .filter(logEntry ->
                logEntry
                    .getHttpRequests()
                    .stream()
                    .anyMatch(request -> logEntryPredicate.test(logEntry) && httpRequestMatcher.matches(request))
            )
            .map(logEntryMapper)
            .collect(Collectors.toList());
    }

    public String verify(Verification verification) {
        String failureMessage = "";

        if (verification != null) {
            if (!verification.getTimes().matches(retrieveRequests(verification.getHttpRequest()).size())) {
                List<HttpRequest> allRequestsArray = retrieveRequests(null);
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
            }
        }

        return failureMessage;
    }

    public String verify(VerificationSequence verificationSequence) {
        List<HttpRequest> requestLog = retrieveRequests(null);

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
                                .setHttpRequests(verificationSequence.getHttpRequests())
                                .setMessageFormat("request sequence not found, expected:{}but was:{}")
                                .setArguments(arguments)
                        );
                        break;
                    }
                }
            }
        }

        return failureMessage;
    }

    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return new String[]{"disruptor"};
    }

}
