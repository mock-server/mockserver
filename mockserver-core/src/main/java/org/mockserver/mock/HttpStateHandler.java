package org.mockserver.mock;

import com.google.common.base.Strings;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.client.serialization.java.ExpectationToJavaSerializer;
import org.mockserver.client.serialization.java.HttpRequestToJavaSerializer;
import org.mockserver.filters.RequestLogFilter;
import org.mockserver.filters.RequestResponseLogFilter;
import org.mockserver.logging.LogFormatter;
import org.mockserver.model.HttpRequest;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class HttpStateHandler {

    private LogFormatter logFormatter = new LogFormatter(LoggerFactory.getLogger(this.getClass()));
    // mockserver
    private final RequestLogFilter requestLogFilter;
    private final RequestResponseLogFilter requestResponseLogFilter;
    private final MockServerMatcher mockServerMatcher;
    // serializers
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer();
    private HttpRequestToJavaSerializer httpRequestToJavaSerializer = new HttpRequestToJavaSerializer();
    private ExpectationToJavaSerializer expectationToJavaSerializer = new ExpectationToJavaSerializer();

    public HttpStateHandler(RequestLogFilter requestLogFilter, RequestResponseLogFilter requestResponseLogFilter, MockServerMatcher mockServerMatcher) {
        this.requestLogFilter = requestLogFilter;
        this.requestResponseLogFilter = requestResponseLogFilter;
        this.mockServerMatcher = mockServerMatcher;
    }

    public void clear(final HttpRequest request) {
        HttpRequest httpRequest = null;
        if (!Strings.isNullOrEmpty(request.getBodyAsString())) {
            httpRequest = httpRequestSerializer.deserialize(request.getBodyAsString());
        }
        if (request.hasQueryStringParameter("type", "expectation")) {
            if (mockServerMatcher != null) {
                mockServerMatcher.clear(httpRequest);
            }
            if (requestResponseLogFilter != null) {
                requestResponseLogFilter.clear(httpRequest);
            }
            logFormatter.infoLog("clearing expectations that match:{}", httpRequest);
        } else if (request.hasQueryStringParameter("type", "log")) {
            requestLogFilter.clear(httpRequest);
            logFormatter.infoLog("clearing request logs that match:{}", httpRequest);
        } else {
            if (mockServerMatcher != null) {
                mockServerMatcher.clear(httpRequest);
            }
            if (requestResponseLogFilter != null) {
                requestResponseLogFilter.clear(httpRequest);
            }
            requestLogFilter.clear(httpRequest);
            logFormatter.infoLog("clearing expectations and request logs that match:{}", httpRequest);
        }
    }

    public String retrieve(final HttpRequest request) {
        HttpRequest httpRequest = null;
        if (!Strings.isNullOrEmpty(request.getBodyAsString())) {
            httpRequest = httpRequestSerializer.deserialize(request.getBodyAsString());
        }
        StringBuilder responseBody = new StringBuilder();
        boolean asJava = request.hasQueryStringParameter("format", "java");
        boolean asExpectations = request.hasQueryStringParameter("type", "expectation");
        if (asExpectations) {
            List<Expectation> expectations = mockServerMatcher.retrieveExpectations(httpRequest);
            if (asJava) {
                responseBody.append(expectationToJavaSerializer.serializeAsJava(0, expectations));
            } else {
                responseBody.append(expectationSerializer.serialize(expectations));
            }
        } else {
            HttpRequest[] httpRequests = requestLogFilter.retrieve(httpRequest);
            if (asJava) {
                responseBody.append(httpRequestToJavaSerializer.serializeAsJava(0, httpRequests));
            } else {
                responseBody.append(httpRequestSerializer.serialize(httpRequests));
            }
        }
        logFormatter.infoLog("retrieving " + (asExpectations ? "expectations" : "requests") + " that match:{}", httpRequest);

        return responseBody.toString();
    }

    public void dumpRecordedRequestResponsesToLog(final HttpRequest request) {
        HttpRequest httpRequest = null;
        if (!Strings.isNullOrEmpty(request.getBodyAsString())) {
            httpRequest = httpRequestSerializer.deserialize(request.getBodyAsString());
        }
        boolean asJava = request.hasQueryStringParameter("type", "java") || request.hasQueryStringParameter("format", "java");
        requestResponseLogFilter.dumpToLog(httpRequest, asJava);
        logFormatter.infoLog("dumped all requests and responses to the log in " + (asJava ? "java" : "json") + " that match:{}", httpRequest);
    }

    public void dumpExpectationsToLog(final HttpRequest request) {
        HttpRequest httpRequest = null;
        if (!Strings.isNullOrEmpty(request.getBodyAsString())) {
            httpRequest = httpRequestSerializer.deserialize(request.getBodyAsString());
        }
        boolean asJava = request.hasQueryStringParameter("type", "java") || request.hasQueryStringParameter("format", "java");
        mockServerMatcher.dumpToLog(httpRequest, asJava);
        logFormatter.infoLog("dumped all active expectations to the log in " + (asJava ? "java" : "json") + " that match:{}", httpRequest);
    }

    public void reset() {
        if (mockServerMatcher != null) {
            mockServerMatcher.reset();
        }
        if (requestResponseLogFilter != null) {
            requestResponseLogFilter.reset();
        }
        requestLogFilter.reset();
        logFormatter.infoLog("resetting all expectations and request logs");
    }
}
