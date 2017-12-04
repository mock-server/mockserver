package org.mockserver.filters;

import com.google.common.base.Function;
import com.google.common.collect.EvictingQueue;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.log.model.ExpectationLogEntry;
import org.mockserver.log.model.ExpectationMatchLogEntry;
import org.mockserver.log.model.LogEntry;
import org.mockserver.log.model.RequestResponseLogEntry;
import org.mockserver.logging.LoggingFormatter;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class LogFilter {

    private static LoggingFormatter logFormatter = new LoggingFormatter(LoggerFactory.getLogger(LogFilter.class));
    private Queue<LogEntry> requestLog = Queues.synchronizedQueue(EvictingQueue.<LogEntry>create(100));
    private MatcherBuilder matcherBuilder = new MatcherBuilder();
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();

    private Function<LogEntry, HttpRequest> logEntryToHttpRequestFunction = new Function<LogEntry, HttpRequest>() {
        public HttpRequest apply(LogEntry logEntry) {
            return logEntry.getHttpRequest();
        }
    };

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
        return Lists.transform(retrieveLogEntries(httpRequest), logEntryToHttpRequestFunction);
    }

    public List<Expectation> retrieveExpectations(HttpRequest httpRequest) {
        List<Expectation> matchingExpectations = new ArrayList<>();
        List<LogEntry> logEntries = retrieveLogEntries(httpRequest);
        for (LogEntry logEntry : logEntries) {
            if (logEntry instanceof ExpectationLogEntry) {
                matchingExpectations.add(((ExpectationLogEntry) logEntry).getExpectation());
            }
        }
        return matchingExpectations;
    }

    public List<LogEntry> retrieveLogEntries(HttpRequest httpRequest) {
        List<LogEntry> requestLog = new LinkedList<>(this.requestLog);

        List<LogEntry> matchingRequests = new ArrayList<>();
        if (httpRequest != null) {
            HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(httpRequest);
            for (LogEntry logEntry : requestLog) {
                if (httpRequestMatcher.matches(logEntry.getHttpRequest(), true)) {
                    matchingRequests.add(logEntry);
                }
            }
        } else {
            matchingRequests.addAll(requestLog);
        }
        return matchingRequests;
    }

    public String verify(Verification verification) {
        LinkedList<LogEntry> requestLog = new LinkedList<>(this.requestLog);

        String failureMessage = "";

        if (verification != null) {
            List<HttpRequest> matchingRequests = new ArrayList<>();
            if (verification.getHttpRequest() != null) {
                HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(verification.getHttpRequest());
                for (LogEntry logEntry : requestLog) {
                    if (httpRequestMatcher.matches(logEntry.getHttpRequest(), true)) {
                        matchingRequests.add(logEntry.getHttpRequest());
                    }
                }
            }

            boolean verified = true;

            if (verification.getTimes().getCount() != 0 && matchingRequests.isEmpty()) {
                verified = false;
            } else if (verification.getTimes().isExact() && matchingRequests.size() != verification.getTimes().getCount()) {
                verified = false;
            } else if (matchingRequests.size() < verification.getTimes().getCount()) {
                verified = false;
            }

            if (!verified) {
                List<HttpRequest> allRequestsArray = Lists.transform(requestLog, logEntryToHttpRequestFunction);
                String serializedRequestToBeVerified = httpRequestSerializer.serialize(true, verification.getHttpRequest());
                String serializedAllRequestInLog = allRequestsArray.size() == 1 ? httpRequestSerializer.serialize(true, allRequestsArray.get(0)) : httpRequestSerializer.serialize(true, allRequestsArray);
                logFormatter.infoLog("request not found " + verification.getTimes() + ", expected:{}" + NEW_LINE + " but was:{}", serializedRequestToBeVerified, serializedAllRequestInLog);
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
                        if (httpRequestMatcher.matches(requestLog.get(requestLogCounter).getHttpRequest(), true)) {
                            // move on to next request
                            foundRequest = true;
                        }
                    }
                    if (!foundRequest) {
                        List<HttpRequest> allRequestsArray = Lists.transform(requestLog, logEntryToHttpRequestFunction);
                        String serializedRequestToBeVerified = httpRequestSerializer.serialize(true, verificationSequence.getHttpRequests());
                        String serializedAllRequestInLog = allRequestsArray.size() == 1 ? httpRequestSerializer.serialize(true, allRequestsArray.get(0)) : httpRequestSerializer.serialize(true, allRequestsArray);
                        failureMessage = "Request sequence not found, expected:<" + serializedRequestToBeVerified + "> but was:<" + serializedAllRequestInLog + ">";
                        logFormatter.infoLog("request sequence not found, expected:{}" + NEW_LINE + " but was:{}", serializedRequestToBeVerified, serializedAllRequestInLog);
                        break;
                    }
                }
            }
        }

        return failureMessage;
    }
}
