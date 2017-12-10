package org.mockserver.filters;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.EvictingQueue;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.log.model.*;
import org.mockserver.logging.LoggingFormatter;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;

import java.util.*;

import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class LogFilter {

    public final static List<Class<? extends LogEntry>> REQUEST_LOG_TYPES = Arrays.asList(
        RequestLogEntry.class,
        RequestResponseLogEntry.class,
        ExpectationMatchLogEntry.class
    );
    public final static List<Class<? extends LogEntry>> EXPECTATION_LOG_TYPES = Arrays.<Class<? extends LogEntry>>asList(
        RequestResponseLogEntry.class,
        ExpectationMatchLogEntry.class
    );
    private final LoggingFormatter logFormatter;
    private Queue<LogEntry> requestLog = Queues.synchronizedQueue(EvictingQueue.<LogEntry>create(100));
    private MatcherBuilder matcherBuilder;
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();

    private Function<LogEntry, HttpRequest> logEntryToHttpRequestFunction = new Function<LogEntry, HttpRequest>() {
        public HttpRequest apply(LogEntry logEntry) {
            return logEntry.getHttpRequest();
        }
    };

    private Predicate<LogEntry> notMessageLogEntryPredicate = new Predicate<LogEntry>() {
        public boolean apply(LogEntry logEntry) {
            return !(logEntry instanceof MessageLogEntry);
        }
    };

    public LogFilter(LoggingFormatter logFormatter) {
        this.logFormatter = logFormatter;
        this.matcherBuilder = new MatcherBuilder(logFormatter);
    }

    public void onRequest(LogEntry logEntry) {
        requestLog.add(logEntry);
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
    }

    public List<HttpRequest> retrieveRequests(HttpRequest httpRequest) {
        return Lists.transform(
            retrieveLogEntries(httpRequest, REQUEST_LOG_TYPES),
            logEntryToHttpRequestFunction
        );
    }

    public List<Expectation> retrieveExpectations(HttpRequest httpRequest) {
        List<Expectation> matchingExpectations = new ArrayList<>();
        List<LogEntry> logEntries = retrieveLogEntries(httpRequest, EXPECTATION_LOG_TYPES);
        for (LogEntry logEntry : logEntries) {
            matchingExpectations.add(((ExpectationLogEntry) logEntry).getExpectation());
        }
        return matchingExpectations;
    }

    public List<LogEntry> retrieveLogEntries(HttpRequest httpRequest) {
        return retrieveLogEntries(httpRequest, Collections.<Class<? extends LogEntry>>emptyList());
    }

    public List<LogEntry> retrieveLogEntries(HttpRequest httpRequest, List<Class<? extends LogEntry>> types) {
        List<LogEntry> requestLog = new LinkedList<>(this.requestLog);

        List<LogEntry> matchingLogEntries = new ArrayList<>();
        HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(httpRequest);
        for (LogEntry logEntry : requestLog) {
            if ((types.isEmpty() || types.contains(logEntry.getClass())) && httpRequestMatcher.matches(logEntry.getHttpRequest(), true)) {
                matchingLogEntries.add(logEntry);
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
                        if (!(logEntry instanceof MessageLogEntry) && httpRequestMatcher.matches(logEntry.getHttpRequest(), true)) {
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
