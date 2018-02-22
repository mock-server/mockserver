package org.mockserver.filters;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.collections.BoundedConcurrentLinkedQueue;
import org.mockserver.log.model.*;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.ui.MockServerEventLogNotifier;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;

import java.util.*;

import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.configuration.ConfigurationProperties.maxExpectations;
import static org.mockserver.log.model.MessageLogEntry.LogMessageType.VERIFICATION_FAILED;

/**
 * @author jamesdbloom
 */
public class MockServerEventLog extends MockServerEventLogNotifier {

    private final static List<Class<? extends LogEntry>> MESSAGE_LOG_TYPES = Collections.<Class<? extends LogEntry>>singletonList(
        MessageLogEntry.class
    );
    private final static List<Class<? extends LogEntry>> REQUEST_LOG_TYPES = Arrays.asList(
        RequestLogEntry.class,
        RequestResponseLogEntry.class,
        ExpectationMatchLogEntry.class
    );
    private final static List<Class<? extends LogEntry>> EXPECTATION_LOG_TYPES = Collections.<Class<? extends LogEntry>>singletonList(
        RequestResponseLogEntry.class
    );
    static Predicate<LogEntry> messageLogPredicate = new Predicate<LogEntry>() {
        public boolean apply(LogEntry input) {
            return MESSAGE_LOG_TYPES.contains(input.getClass());
        }
    };
    static Predicate<LogEntry> requestLogPredicate = new Predicate<LogEntry>() {
        public boolean apply(LogEntry input) {
            return REQUEST_LOG_TYPES.contains(input.getClass());
        }
    };
    static Predicate<LogEntry> expectationLogPredicate = new Predicate<LogEntry>() {
        public boolean apply(LogEntry input) {
            return EXPECTATION_LOG_TYPES.contains(input.getClass());
        }
    };
    private static Function<LogEntry, List<HttpRequest>> logEntryToRequest = new Function<LogEntry, List<HttpRequest>>() {
        public List<HttpRequest> apply(LogEntry logEntry) {
            return logEntry.getHttpRequests();
        }
    };
    private static Function<LogEntry, Expectation> logEntryToExpectation = new Function<LogEntry, Expectation>() {
        public Expectation apply(LogEntry logEntry) {
            return ((ExpectationLogEntry) logEntry).getExpectation();
        }
    };
    private MockServerLogger logFormatter;
    private Queue<LogEntry> requestLog = new BoundedConcurrentLinkedQueue<>(maxExpectations());
    private MatcherBuilder matcherBuilder;
    private HttpRequestSerializer httpRequestSerializer;


    public MockServerEventLog(MockServerLogger logFormatter, Scheduler scheduler) {
        super(scheduler);
        this.logFormatter = logFormatter;
        this.matcherBuilder = new MatcherBuilder(logFormatter);
        httpRequestSerializer = new HttpRequestSerializer(logFormatter);
    }

    public void add(LogEntry logEntry) {
        requestLog.add(logEntry);
        notifyListeners(this);
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

    public List<LogEntry> retrieveRequestLogEntries(HttpRequest httpRequest) {
        return retrieveLogEntries(httpRequest, requestLogPredicate, new Function<LogEntry, LogEntry>() {
            public LogEntry apply(LogEntry input) {
                return input;
            }
        });
    }

    public List<MessageLogEntry> retrieveMessageLogEntries(HttpRequest httpRequest) {
        return retrieveLogEntries(httpRequest, messageLogPredicate, new Function<LogEntry, MessageLogEntry>() {
            public MessageLogEntry apply(LogEntry input) {
                return (MessageLogEntry) input;
            }
        });
    }

    public List<MessageLogEntry> retrieveMessages(HttpRequest httpRequest) {
        return retrieveLogEntries(httpRequest, messageLogPredicate, new Function<LogEntry, MessageLogEntry>() {
            public MessageLogEntry apply(LogEntry input) {
                return (MessageLogEntry) input;
            }
        });
    }

    public List<HttpRequest> retrieveRequests(HttpRequest httpRequest) {
        List<HttpRequest> result = new ArrayList<>();
        for (List<HttpRequest> httpRequests : retrieveLogEntries(httpRequest, requestLogPredicate, logEntryToRequest)) {
            result.addAll(httpRequests);
        }
        return result;
    }

    public List<Expectation> retrieveExpectations(HttpRequest httpRequest) {
        return retrieveLogEntries(httpRequest, expectationLogPredicate, logEntryToExpectation);
    }

    <T> List<T> retrieveLogEntries(HttpRequest httpRequest, Predicate<LogEntry> logEntryPredicate, Function<LogEntry, T> logEntryToTypeFunction) {
        List<T> matchingLogEntries = new ArrayList<>();
        HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(httpRequest);
        for (LogEntry logEntry : new LinkedList<>(this.requestLog)) {
            List<HttpRequest> requests = logEntry.getHttpRequests();
            boolean matched = false;
            for (HttpRequest request : requests) {
                if (logEntryPredicate.apply(logEntry) && httpRequestMatcher.matches(request)) {
                    matched = true;
                }
            }
            if (matched) {
                matchingLogEntries.add(logEntryToTypeFunction.apply(logEntry));
            }
        }
        return matchingLogEntries;
    }

    public String verify(Verification verification) {
        String failureMessage = "";

        if (verification != null) {
            List<HttpRequest> matchingRequests = retrieveRequests(verification.getHttpRequest());

            boolean verified = true;

            if (verification.getTimes().getCount() != 0 && matchingRequests.isEmpty()) {
                verified = false;
            } else if (verification.getTimes().isExact() && matchingRequests.size() != verification.getTimes().getCount()) {
                verified = false;
            } else if (matchingRequests.size() < verification.getTimes().getCount()) {
                verified = false;
            }

            if (!verified) {
                List<HttpRequest> allRequestsArray = retrieveRequests(null);
                String serializedRequestToBeVerified = httpRequestSerializer.serialize(true, verification.getHttpRequest());
                String serializedAllRequestInLog = allRequestsArray.size() == 1 ? httpRequestSerializer.serialize(true, allRequestsArray.get(0)) : httpRequestSerializer.serialize(true, allRequestsArray);
                failureMessage = "Request not found " + verification.getTimes() + ", expected:<" + serializedRequestToBeVerified + "> but was:<" + serializedAllRequestInLog + ">";
                logFormatter.info(VERIFICATION_FAILED, verification.getHttpRequest(), "request not found " + verification.getTimes() + ", expected:{}" + NEW_LINE + " but was:{}", verification.getHttpRequest(), allRequestsArray.size() == 1 ? allRequestsArray.get(0) : allRequestsArray);
            }
        }

        return failureMessage;
    }

    public String verify(VerificationSequence verificationSequence) {
        LinkedList<LogEntry> requestLog = new LinkedList<>(this.requestLog);

        String failureMessage = "";

        if (verificationSequence != null) {

            int requestLogCounter = 0;

            for (HttpRequest verificationHttpRequest : verificationSequence.getHttpRequests()) {
                if (verificationHttpRequest != null) {
                    HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(verificationHttpRequest);
                    boolean foundRequest = false;
                    for (; !foundRequest && requestLogCounter < requestLog.size(); requestLogCounter++) {
                        LogEntry logEntry = requestLog.get(requestLogCounter);
                        List<HttpRequest> requests = logEntry.getHttpRequests();
                        for (HttpRequest request : requests) {
                            if (!(logEntry instanceof MessageLogEntry) && httpRequestMatcher.matches(request)) {
                                // move on to next request
                                foundRequest = true;
                            }
                        }
                    }
                    if (!foundRequest) {
                        List<HttpRequest> allRequestsArray = retrieveRequests(null);
                        String serializedRequestToBeVerified = httpRequestSerializer.serialize(true, verificationSequence.getHttpRequests());
                        String serializedAllRequestInLog = allRequestsArray.size() == 1 ? httpRequestSerializer.serialize(true, allRequestsArray.get(0)) : httpRequestSerializer.serialize(true, allRequestsArray);
                        failureMessage = "Request sequence not found, expected:<" + serializedRequestToBeVerified + "> but was:<" + serializedAllRequestInLog + ">";
                        logFormatter.info(VERIFICATION_FAILED, verificationSequence.getHttpRequests(), "request sequence not found, expected:{}" + NEW_LINE + " but was:{}", verificationSequence.getHttpRequests(), allRequestsArray.size() == 1 ? allRequestsArray.get(0) : allRequestsArray);
                        break;
                    }
                }
            }
        }

        return failureMessage;
    }

}
