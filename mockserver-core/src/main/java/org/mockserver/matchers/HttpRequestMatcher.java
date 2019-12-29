package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.*;
import org.slf4j.event.Level;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.log.model.LogEntry.LogMessageType.EXPECTATION_MATCHED;
import static org.mockserver.log.model.LogEntry.LogMessageType.EXPECTATION_NOT_MATCHED;
import static org.mockserver.model.MediaType.DEFAULT_HTTP_CHARACTER_SET;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("rawtypes")
public class HttpRequestMatcher extends NotMatcher<HttpRequest> {

    private static final String[] excludedFields = {"mockServerLogger", "objectMapper"};
    private static final String DID_NOT_MATCH = "didn't match";
    private static final String MATCHED = "matched";
    private static final String REQUEST_DID_NOT_MATCH = "request:{}didn't match request:{}because:{}";
    private static final String EXPECTATION_DID_NOT_MATCH = "request:{}didn't match expectation:{}because:{}";
    private static final String EXPECTATION_DID_NOT_MATCH_WITHOUT_BECAUSE = "request:{}didn't match expectation:{}";
    private static final String REQUEST_DID_MATCH = "request:{}matched request:{}";
    private static final String EXPECTATION_DID_MATCH = "request:{}matched expectation:{}";
    private MockServerLogger mockServerLogger;
    private Expectation expectation;
    private HttpRequest httpRequest;
    private RegexStringMatcher methodMatcher = null;
    private RegexStringMatcher pathMatcher = null;
    private MultiValueMapMatcher queryStringParameterMatcher = null;
    private BodyMatcher bodyMatcher = null;
    private MultiValueMapMatcher headerMatcher = null;
    private HashMapMatcher cookieMatcher = null;
    private BooleanMatcher keepAliveMatcher = null;
    private BodyDTO bodyDTOMatcher = null;
    private BooleanMatcher sslMatcher = null;
    private final boolean controlPlaneMatcher;
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    public HttpRequestMatcher(MockServerLogger mockServerLogger, HttpRequest httpRequest) {
        this.mockServerLogger = mockServerLogger;
        this.controlPlaneMatcher = true;
        update(httpRequest);
    }

    public boolean update(HttpRequest httpRequest) {
        if (this.httpRequest != null && this.httpRequest.equals(httpRequest)) {
            return false;
        } else {
            this.httpRequest = httpRequest;
            if (httpRequest != null) {
                withMethod(httpRequest.getMethod());
                withPath(httpRequest.getPath());
                withQueryStringParameters(httpRequest.getQueryStringParameters());
                withBody(httpRequest.getBody());
                withHeaders(httpRequest.getHeaders());
                withCookies(httpRequest.getCookies());
                withKeepAlive(httpRequest.isKeepAlive());
                withSsl(httpRequest.isSecure());
            }
            return true;
        }
    }

    HttpRequestMatcher(MockServerLogger mockServerLogger, Expectation expectation) {
        this.mockServerLogger = mockServerLogger;
        this.controlPlaneMatcher = false;
        update(expectation);
    }

    public boolean update(Expectation expectation) {
        if (this.expectation != null && this.expectation.equals(expectation)) {
            return false;
        } else {
            this.expectation = expectation;
            update(expectation.getHttpRequest());
            return true;
        }
    }

    public Expectation getExpectation() {
        return expectation;
    }

    private void withMethod(NottableString method) {
        this.methodMatcher = new RegexStringMatcher(mockServerLogger, method, controlPlaneMatcher);
    }

    private void withPath(NottableString path) {
        this.pathMatcher = new RegexStringMatcher(mockServerLogger, path, controlPlaneMatcher);
    }

    private void withQueryStringParameters(Parameters parameters) {
        this.queryStringParameterMatcher = new MultiValueMapMatcher(mockServerLogger, parameters, controlPlaneMatcher);
    }

    private void withBody(Body body) {
        if (body != null) {
            switch (body.getType()) {
                case STRING:
                    StringBody stringBody = (StringBody) body;
                    bodyDTOMatcher = new StringBodyDTO(stringBody);
                    if (stringBody.isSubString()) {
                        this.bodyMatcher = new SubStringMatcher(mockServerLogger, string(stringBody.getValue()));
                    } else {
                        this.bodyMatcher = new ExactStringMatcher(mockServerLogger, string(stringBody.getValue()));
                    }
                    break;
                case REGEX:
                    RegexBody regexBody = (RegexBody) body;
                    bodyDTOMatcher = new RegexBodyDTO(regexBody);
                    this.bodyMatcher = new RegexStringMatcher(mockServerLogger, string(regexBody.getValue()), controlPlaneMatcher);
                    break;
                case PARAMETERS:
                    ParameterBody parameterBody = (ParameterBody) body;
                    bodyDTOMatcher = new ParameterBodyDTO(parameterBody);
                    this.bodyMatcher = new ParameterStringMatcher(mockServerLogger, parameterBody.getValue(), controlPlaneMatcher);
                    break;
                case XPATH:
                    XPathBody xPathBody = (XPathBody) body;
                    bodyDTOMatcher = new XPathBodyDTO(xPathBody);
                    this.bodyMatcher = new XPathMatcher(mockServerLogger, xPathBody.getValue());
                    break;
                case XML:
                    XmlBody xmlBody = (XmlBody) body;
                    bodyDTOMatcher = new XmlBodyDTO(xmlBody);
                    this.bodyMatcher = new XmlStringMatcher(mockServerLogger, xmlBody.getValue());
                    break;
                case JSON:
                    JsonBody jsonBody = (JsonBody) body;
                    bodyDTOMatcher = new JsonBodyDTO(jsonBody);
                    this.bodyMatcher = new JsonStringMatcher(mockServerLogger, jsonBody.getValue(), jsonBody.getMatchType());
                    break;
                case JSON_SCHEMA:
                    JsonSchemaBody jsonSchemaBody = (JsonSchemaBody) body;
                    bodyDTOMatcher = new JsonSchemaBodyDTO(jsonSchemaBody);
                    this.bodyMatcher = new JsonSchemaMatcher(mockServerLogger, jsonSchemaBody.getValue());
                    break;
                case JSON_PATH:
                    JsonPathBody jsonPathBody = (JsonPathBody) body;
                    bodyDTOMatcher = new JsonPathBodyDTO(jsonPathBody);
                    this.bodyMatcher = new JsonPathMatcher(mockServerLogger, jsonPathBody.getValue());
                    break;
                case XML_SCHEMA:
                    XmlSchemaBody xmlSchemaBody = (XmlSchemaBody) body;
                    bodyDTOMatcher = new XmlSchemaBodyDTO(xmlSchemaBody);
                    this.bodyMatcher = new XmlSchemaMatcher(mockServerLogger, xmlSchemaBody.getValue());
                    break;
                case BINARY:
                    BinaryBody binaryBody = (BinaryBody) body;
                    bodyDTOMatcher = new BinaryBodyDTO(binaryBody);
                    this.bodyMatcher = new BinaryMatcher(mockServerLogger, binaryBody.getValue());
                    break;
            }
            if (body.isNot()) {
                //noinspection ConstantConditions
                this.bodyMatcher = not(this.bodyMatcher);
            }
        }
    }

    private void withHeaders(Headers headers) {
        this.headerMatcher = new MultiValueMapMatcher(mockServerLogger, headers, controlPlaneMatcher);
    }

    private void withCookies(Cookies cookies) {
        this.cookieMatcher = new HashMapMatcher(mockServerLogger, cookies, controlPlaneMatcher);
    }

    private void withKeepAlive(Boolean keepAlive) {
        this.keepAliveMatcher = new BooleanMatcher(mockServerLogger, keepAlive);
    }

    private void withSsl(Boolean isSsl) {
        this.sslMatcher = new BooleanMatcher(mockServerLogger, isSsl);
    }

    public boolean matches(final HttpRequest request) {
        return matches(null, request);
    }

    public boolean matches(final HttpRequest context, final HttpRequest request) {
        StringBuilder becauseBuilder = new StringBuilder();
        boolean overallMatch = matches(context, request, becauseBuilder);
        if (!controlPlaneMatcher) {
            if (overallMatch) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(EXPECTATION_MATCHED)
                        .setLogLevel(Level.INFO)
                        .setHttpRequest(request)
                        .setExpectation(this.expectation)
                        .setMessageFormat(this.expectation == null ? REQUEST_DID_MATCH : EXPECTATION_DID_MATCH)
                        .setArguments(request, (this.expectation == null ? this : this.expectation.clone()))
                );
            } else {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(EXPECTATION_NOT_MATCHED)
                        .setLogLevel(Level.INFO)
                        .setHttpRequest(request)
                        .setExpectation(this.expectation)
                        .setMessageFormat(this.expectation == null ? REQUEST_DID_NOT_MATCH : becauseBuilder.length() > 0 ? EXPECTATION_DID_NOT_MATCH : EXPECTATION_DID_NOT_MATCH_WITHOUT_BECAUSE)
                        .setArguments(request, (this.expectation == null ? this : this.expectation.clone()), becauseBuilder.toString())
                );
            }
        }
        return overallMatch;
    }

    private boolean matches(HttpRequest context, HttpRequest request, StringBuilder becauseBuilder) {
        if (isActive()) {
            if (request == this.httpRequest) {
                return true;
            } else if (this.httpRequest == null) {
                return true;
            } else {
                if (request != null) {
                    boolean methodMatches = isBlank(request.getMethod().getValue()) || matches(context, methodMatcher, request.getMethod());
                    if (matchFailed(request, becauseBuilder, methodMatches, "", "method ")) {
                        return combineResults(false, request.isNot(), this.httpRequest.isNot(), not);
                    }

                    boolean pathMatches = isBlank(request.getPath().getValue()) || matches(context, pathMatcher, request.getPath());
                    if (matchFailed(request, becauseBuilder, pathMatches, ",", "path ")) {
                        return combineResults(false, request.isNot(), this.httpRequest.isNot(), not);
                    }

                    boolean bodyMatches = bodyMatches(context, request);
                    if (matchFailed(request, becauseBuilder, bodyMatches, ",", "body ")) {
                        return combineResults(false, request.isNot(), this.httpRequest.isNot(), not);
                    }

                    boolean headersMatch = matches(context, headerMatcher, request.getHeaders());
                    if (matchFailed(request, becauseBuilder, headersMatch, ",", "headers ")) {
                        return combineResults(false, request.isNot(), this.httpRequest.isNot(), not);
                    }

                    boolean cookiesMatch = matches(context, cookieMatcher, request.getCookies());
                    if (matchFailed(request, becauseBuilder, cookiesMatch, ",", "cookies ")) {
                        return combineResults(false, request.isNot(), this.httpRequest.isNot(), not);
                    }

                    boolean queryStringParametersMatches = matches(context, queryStringParameterMatcher, request.getQueryStringParameters());
                    if (matchFailed(request, becauseBuilder, queryStringParametersMatches, ",", "query ")) {
                        return combineResults(false, request.isNot(), this.httpRequest.isNot(), not);
                    }

                    boolean keepAliveMatches = matches(context, keepAliveMatcher, request.isKeepAlive());
                    if (matchFailed(request, becauseBuilder, keepAliveMatches, ",", "keep-alive ")) {
                        return combineResults(false, request.isNot(), this.httpRequest.isNot(), not);
                    }

                    boolean sslMatches = matches(context, sslMatcher, request.isSecure());
                    if (matchFailed(request, becauseBuilder, sslMatches, ",", "sslMatches ")) {
                        return combineResults(false, request.isNot(), this.httpRequest.isNot(), not);
                    }

                    return combineResults(true, request.isNot(), this.httpRequest.isNot(), not);
                } else {
                    return combineResults(true, this.httpRequest.isNot(), not);
                }
            }
        }
        return false;
    }

    private static boolean combineResults(boolean... inputs) {
        int count = 0;
        for (boolean input : inputs) {
            count += (input ? 1 : 0);
        }
        return count % 2 != 0;
    }

    private boolean matchFailed(HttpRequest request, StringBuilder becauseBuilder, boolean testResult, String separator, String fieldName) {
        boolean matchFailed = false;
        if (!controlPlaneMatcher) {
            becauseBuilder.append(separator).append(separator.length() > 0 ? NEW_LINE : "").append(fieldName).append((testResult ? MATCHED : DID_NOT_MATCH));
        }
        if (!testResult) {
            if (!controlPlaneMatcher) {
                if (request.isNot()) {
                    becauseBuilder.append(",").append(NEW_LINE).append("request 'not' operator is enabled");
                }
                if (this.httpRequest.isNot()) {
                    becauseBuilder.append(",").append(NEW_LINE).append("expectation's request 'not' operator is enabled");
                }
                if (not) {
                    becauseBuilder.append(",").append(NEW_LINE).append("expectation's request matcher 'not' operator is enabled");
                }
            }
            matchFailed = true;
        }
        return matchFailed;
    }

    @SuppressWarnings("unchecked")
    private boolean bodyMatches(HttpRequest context, HttpRequest request) {
        boolean bodyMatches = true;
        String bodyAsString = request.getBody() != null ? new String(request.getBody().getRawBytes(), request.getBody().getCharset(DEFAULT_HTTP_CHARACTER_SET)) : "";
        if (isNotBlank(bodyAsString) || !controlPlaneMatcher) {
            if (bodyMatcher instanceof BinaryMatcher) {
                bodyMatches = matches(context, bodyMatcher, request.getBodyAsRawBytes());
            } else {
                if (bodyMatcher instanceof ExactStringMatcher ||
                    bodyMatcher instanceof SubStringMatcher ||
                    bodyMatcher instanceof RegexStringMatcher ||
                    bodyMatcher instanceof XmlStringMatcher) {
                    bodyMatches = matches(context, bodyMatcher, string(bodyAsString));
                } else {
                    bodyMatches = matches(context, bodyMatcher, bodyAsString);
                }
            }
            if (!bodyMatches) {
                try {
                    bodyMatches = bodyDTOMatcher.equals(objectMapper.readValue(bodyAsString, BodyDTO.class));
                } catch (Throwable e) {
                    // ignore this exception as this exception would typically get thrown for "normal" HTTP requests (i.e. not clear or retrieve)
                }
            }
        }
        return bodyMatches;
    }

    private <T> boolean matches(HttpRequest context, Matcher<T> matcher, T t) {
        boolean result = false;

        if (matcher == null) {
            result = true;
        } else if (matcher.matches(context, t)) {
            result = true;
        }

        return result;
    }

    public boolean isActive() {
        return expectation == null || expectation.isActive();

    }

    public boolean decrementRemainingMatches() {
        return expectation.decrementRemainingMatches();
    }

    @Override
    public String toString() {
        try {
            return ObjectMapperFactory
                .createObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(httpRequest);
        } catch (Exception e) {
            return super.toString();
        }
    }

    @Override
    @JsonIgnore
    public String[] fieldsExcludedFromEqualsAndHashCode() {
        return excludedFields;
    }
}
