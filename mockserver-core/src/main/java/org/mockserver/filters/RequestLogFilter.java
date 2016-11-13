package org.mockserver.filters;

import com.google.common.collect.EvictingQueue;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.logging.LogFormatter;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class RequestLogFilter implements ResponseFilter, RequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RequestLogFilter.class);
    // request persistence
    private final EvictingQueue<HttpRequest> requestLog = EvictingQueue.create(5000);

    // matcher
    private final MatcherBuilder matcherBuilder = new MatcherBuilder();
    private LogFormatter logFormatter = new LogFormatter(logger);
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();

    @Override
    public /* synchronized */ HttpResponse onResponse(HttpRequest httpRequest, HttpResponse httpResponse) {
        return httpResponse;
    }

    @Override
    public /* synchronized */ HttpRequest onRequest(HttpRequest httpRequest) {
        requestLog.add(httpRequest);
        return httpRequest;
    }

    public /* synchronized */ List<HttpRequest> httpRequests(HttpRequest httpRequest) {
        LinkedList<HttpRequest> requestLog = new LinkedList<HttpRequest>(this.requestLog);

        List<HttpRequest> httpRequests = new ArrayList<HttpRequest>();
        HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(httpRequest);
        for (HttpRequest loggedHttpRequest : requestLog) {
            if (httpRequestMatcher.matches(loggedHttpRequest)) {
                httpRequests.add(loggedHttpRequest);
            }
        }
        return httpRequests;
    }

    public /* synchronized */ void reset() {
        requestLog.clear();
    }

    public /* synchronized */ void clear(HttpRequest httpRequest) {
        if (httpRequest != null) {
            HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(httpRequest);
            for (HttpRequest value : new LinkedList<HttpRequest>(requestLog)) {
                if (httpRequestMatcher.matches(value, true)) {
                    requestLog.remove(value);
                }
            }
        } else {
            reset();
        }
    }

    public HttpRequest[] retrieve(HttpRequest httpRequestToMatch) {
        LinkedList<HttpRequest> requestLog = new LinkedList<HttpRequest>(this.requestLog);

        List<HttpRequest> matchingRequests = new ArrayList<HttpRequest>();
        if (httpRequestToMatch != null) {
            HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(httpRequestToMatch);
            for (HttpRequest httpRequest : requestLog) {
                if (httpRequestMatcher.matches(httpRequest, true)) {
                    matchingRequests.add(httpRequest);
                }
            }
        } else {
            for (HttpRequest httpRequest : requestLog) {
                matchingRequests.add(httpRequest);
            }
        }
        return matchingRequests.toArray(new HttpRequest[matchingRequests.size()]);
    }

    public String verify(Verification verification) {
        LinkedList<HttpRequest> requestLog = new LinkedList<HttpRequest>(this.requestLog);

        String failureMessage = "";

        if (verification != null) {
            List<HttpRequest> matchingRequests = new ArrayList<HttpRequest>();
            if (verification.getHttpRequest() != null) {
                HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(verification.getHttpRequest());
                for (HttpRequest httpRequest : requestLog) {
                    if (httpRequestMatcher.matches(httpRequest, true)) {
                        matchingRequests.add(httpRequest);
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
                HttpRequest[] allRequestsArray = requestLog.toArray(new HttpRequest[requestLog.size()]);
                String serializedRequestToBeVerified = httpRequestSerializer.serialize(verification.getHttpRequest());
                String serializedAllRequestInLog = allRequestsArray.length == 1 ? httpRequestSerializer.serialize(allRequestsArray[0]) : httpRequestSerializer.serialize(allRequestsArray);
                logFormatter.infoLog("request not found " + verification.getTimes() + ", expected:{}" + System.getProperty("line.separator") + " but was:{}", serializedRequestToBeVerified, serializedAllRequestInLog);
                failureMessage = "Request not found " + verification.getTimes() + ", expected:<" + serializedRequestToBeVerified + "> but was:<" + serializedAllRequestInLog + ">";
            }
        }

        return failureMessage;
    }

    public String verify(VerificationSequence verificationSequence) {
        LinkedList<HttpRequest> requestLog = new LinkedList<HttpRequest>(this.requestLog);

        String failureMessage = "";

        if (verificationSequence != null) {

            int requestLogCounter = 0;

            for (HttpRequest verificationHttpRequest : verificationSequence.getHttpRequests()) {
                if (verificationHttpRequest != null) {
                    HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(verificationHttpRequest);
                    boolean foundRequest = false;
                    for (; !foundRequest && requestLogCounter < requestLog.size(); requestLogCounter++) {
                        if (httpRequestMatcher.matches(requestLog.get(requestLogCounter), true)) {
                            // move on to next request
                            foundRequest = true;
                        }
                    }
                    if (!foundRequest) {
                        String serializedRequestToBeVerified = httpRequestSerializer.serialize(verificationSequence.getHttpRequests());
                        String serializedAllRequestInLog = httpRequestSerializer.serialize(requestLog);
                        failureMessage = "Request sequence not found, expected:<" + serializedRequestToBeVerified + "> but was:<" + serializedAllRequestInLog + ">";
                        logFormatter.infoLog("request sequence not found, expected:{}" + System.getProperty("line.separator") + " but was:{}", serializedRequestToBeVerified, serializedAllRequestInLog);
                        break;
                    }
                }
            }
        }

        return failureMessage;
    }
}
