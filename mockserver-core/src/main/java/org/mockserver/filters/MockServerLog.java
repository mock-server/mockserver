package org.mockserver.filters;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.EvictingQueue;
import com.google.common.collect.Queues;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.log.model.*;
import org.mockserver.logging.LoggingFormatter;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.mock.Expectation;
import org.mockserver.ui.MockServerLogNotifier;
import org.mockserver.model.HttpRequest;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;

import java.util.*;

import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class MockServerLog extends MockServerLogNotifier {

    private final static List<Class<? extends LogEntry>> MESSAGE_LOG_TYPES = Collections.<Class<? extends LogEntry>>singletonList(
        MessageLogEntry.class
    );
    private final static List<Class<? extends LogEntry>> REQUEST_LOG_TYPES = Arrays.asList(
        RequestLogEntry.class,
        RequestResponseLogEntry.class,
        ExpectationMatchLogEntry.class
    );
    private final static List<Class<? extends LogEntry>> EXPECTATION_LOG_TYPES = Arrays.<Class<? extends LogEntry>>asList(
        RequestResponseLogEntry.class
    );
    public static Predicate<LogEntry> notMessageLogEntryPredicate = new Predicate<LogEntry>() {
        public boolean apply(LogEntry logEntry) {
            return !(logEntry instanceof MessageLogEntry);
        }
    };
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
    private static Function<LogEntry, HttpRequest> logEntryToRequest = new Function<LogEntry, HttpRequest>() {
        public HttpRequest apply(LogEntry logEntry) {
            return logEntry.getHttpRequest();
        }
    };
    private static Function<LogEntry, Expectation> logEntryToExpectation = new Function<LogEntry, Expectation>() {
        public Expectation apply(LogEntry logEntry) {
            return ((ExpectationLogEntry) logEntry).getExpectation();
        }
    };
    private final LoggingFormatter logFormatter;

    private Queue<LogEntry> requestLog = Queues.synchronizedQueue(EvictingQueue.<LogEntry>create(100));
    private MatcherBuilder matcherBuilder;
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();
    private Function<LogEntry, String> logEntryToMessage = new Function<LogEntry, String>() {
        public String apply(LogEntry logEntry) {
            MessageLogEntry messageLogEntry = (MessageLogEntry) logEntry;
            return messageLogEntry.getTimeStamp() + " - " + messageLogEntry.getMessage();
        }
    };

    public MockServerLog(LoggingFormatter logFormatter) {
        this.logFormatter = logFormatter;
        this.matcherBuilder = new MatcherBuilder(logFormatter);
    }

    public void add(LogEntry logEntry) {
        requestLog.add(logEntry);
        notifyListeners(this);
    }

    public void reset() {
        requestLog.clear();
    }

    public void clear(HttpRequest request) {
        if (request != null) {
            HttpRequestMatcher requestMatcher = matcherBuilder.transformsToMatcher(request);
            for (LogEntry logEntry : new LinkedList<>(requestLog)) {
                if (requestMatcher.matches(logEntry.getHttpRequest(), false)) {
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

    public List<String> retrieveMessages(HttpRequest httpRequest) {
        return retrieveLogEntries(httpRequest, messageLogPredicate, logEntryToMessage);
    }

    public List<HttpRequest> retrieveRequests(HttpRequest httpRequest) {
        return retrieveLogEntries(httpRequest, requestLogPredicate, logEntryToRequest);
    }

    public List<Expectation> retrieveExpectations(HttpRequest httpRequest) {
        return retrieveLogEntries(httpRequest, expectationLogPredicate, logEntryToExpectation);
    }

    <T> List<T> retrieveLogEntries(HttpRequest httpRequest, Predicate<LogEntry> logEntryPredicate, Function<LogEntry, T> logEntryToTypeFunction) {
        List<T> matchingLogEntries = new ArrayList<>();
        HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(httpRequest);
        for (LogEntry logEntry : new LinkedList<>(this.requestLog)) {
            if (logEntryPredicate.apply(logEntry) && httpRequestMatcher.matches(logEntry.getHttpRequest(), false)) {
                matchingLogEntries.add(logEntryToTypeFunction.apply(logEntry));
            }
        }
        return matchingLogEntries;
    }

    public String verify(Verification verification) {
        LinkedList<LogEntry> requestLog = new LinkedList<>(this.requestLog);

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
                logFormatter.infoLog(verification.getHttpRequest(), "request not found " + verification.getTimes() + ", expected:{}" + NEW_LINE + " but was:{}", serializedRequestToBeVerified, serializedAllRequestInLog);
                failureMessage = "Request not found " + verification.getTimes() + ", expected:<" + serializedRequestToBeVerified + "> but was:<" + serializedAllRequestInLog + ">";
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
                        if (!(logEntry instanceof MessageLogEntry) && httpRequestMatcher.matches(logEntry.getHttpRequest(), false)) {
                            // move on to next request
                            foundRequest = true;
                        }
                    }
                    if (!foundRequest) {
                        List<HttpRequest> allRequestsArray = retrieveRequests(null);
                        String serializedRequestToBeVerified = httpRequestSerializer.serialize(true, verificationSequence.getHttpRequests());
                        String serializedAllRequestInLog = allRequestsArray.size() == 1 ? httpRequestSerializer.serialize(true, allRequestsArray.get(0)) : httpRequestSerializer.serialize(true, allRequestsArray);
                        failureMessage = "Request sequence not found, expected:<" + serializedRequestToBeVerified + "> but was:<" + serializedAllRequestInLog + ">";
                        logFormatter.infoLog(verificationSequence.getHttpRequests(), "request sequence not found, expected:{}" + NEW_LINE + " but was:{}", serializedRequestToBeVerified, serializedAllRequestInLog);
                        break;
                    }
                }
            }
        }

        return failureMessage;
    }

}
