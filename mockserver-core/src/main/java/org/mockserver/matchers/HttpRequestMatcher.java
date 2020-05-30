package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.*;
import org.slf4j.event.Level;

import java.util.Comparator;

import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.configuration.ConfigurationProperties.matchersFailFast;
import static org.mockserver.log.model.LogEntry.LogMessageType.EXPECTATION_MATCHED;
import static org.mockserver.log.model.LogEntry.LogMessageType.EXPECTATION_NOT_MATCHED;
import static org.mockserver.model.MediaType.DEFAULT_HTTP_CHARACTER_SET;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("rawtypes")
public class HttpRequestMatcher extends NotMatcher<HttpRequest> {

    public static final Comparator<? super HttpRequestMatcher> EXPECTATION_PRIORITY_COMPARATOR = Comparator.comparing(HttpRequestMatcher::getExpectation, Expectation.EXPECTATION_PRIORITY_COMPARATOR);
    private static final String[] excludedFields = {"mockServerLogger", "methodMatcher", "pathMatcher", "queryStringParameterMatcher", "bodyMatcher", "headerMatcher", "cookieMatcher", "keepAliveMatcher", "bodyDTOMatcher", "sslMatcher", "controlPlaneMatcher", "responseInProgress", "objectMapper"};
    private static final String DID_NOT_MATCH = "didn't match";
    private static final String MATCHED = "matched";
    private static final String REQUEST_DID_NOT_MATCH = "request:{}didn't match request matcher:{}because:{}";
    private static final String EXPECTATION_DID_NOT_MATCH = "request:{}didn't match expectation:{}because:{}";
    private static final String EXPECTATION_DID_NOT_MATCH_WITHOUT_BECAUSE = "request:{}didn't match expectation:{}";
    private static final String REQUEST_DID_MATCH = "request:{}matched request:{}";
    private static final String EXPECTATION_DID_MATCH = "request:{}matched expectation:{}";
    private static final String SPACE = " ";
    private static final String SSL_MATCHES = "sslMatches";
    private static final String KEEP_ALIVE = "keep-alive";
    private static final String QUERY = "query";
    private static final String COOKIES = "cookies";
    private static final String HEADERS = "headers";
    private static final String BODY = "body";
    private static final String PATH = "path";
    private static final String METHOD = "method";
    private static final String COMMA = ",";
    private static final String EMPTY = "";
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
    private boolean controlPlaneMatcher;
    private boolean responseInProgress = false;
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

    public HttpRequestMatcher withControlPlaneMatcher(boolean controlPlaneMatcher) {
        this.controlPlaneMatcher = controlPlaneMatcher;
        return this;
    }

    public boolean isResponseInProgress() {
        return responseInProgress;
    }

    @SuppressWarnings("UnusedReturnValue")
    public HttpRequestMatcher setResponseInProgress(boolean responseInProgress) {
        this.responseInProgress = responseInProgress;
        return this;
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

    public boolean matches(final MatchDifference matchDifference, final HttpRequest request) {
        StringBuilder becauseBuilder = new StringBuilder();
        boolean overallMatch = matches(matchDifference, request, becauseBuilder);
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
                becauseBuilder.replace(0, 1, "");
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

    public boolean isBlank() {
        return httpRequest == null;
    }

    private boolean matches(MatchDifference matchDifference, HttpRequest request, StringBuilder becauseBuilder) {
        if (isActive()) {
            if (request == this.httpRequest) {
                return true;
            } else if (this.httpRequest == null) {
                return true;
            } else {
                if (matchDifference == null) {
                    matchDifference = new MatchDifference(request);
                }
                if (request != null) {
                    boolean methodMatches = StringUtils.isBlank(request.getMethod().getValue()) || matches(METHOD, matchDifference, methodMatcher, request.getMethod());
                    if (failFast(methodMatcher, matchDifference, becauseBuilder, methodMatches, METHOD)) {
                        return false;
                    }

                    boolean pathMatches = StringUtils.isBlank(request.getPath().getValue()) || matches(PATH, matchDifference, pathMatcher, request.getPath());
                    if (failFast(pathMatcher, matchDifference, becauseBuilder, pathMatches, PATH)) {
                        return false;
                    }

                    boolean bodyMatches = bodyMatches(matchDifference, request);
                    if (failFast(bodyMatcher, matchDifference, becauseBuilder, bodyMatches, BODY)) {
                        return false;
                    }

                    boolean headersMatch = matches(HEADERS, matchDifference, headerMatcher, request.getHeaders());
                    if (failFast(headerMatcher, matchDifference, becauseBuilder, headersMatch, HEADERS)) {
                        return false;
                    }

                    boolean cookiesMatch = matches(COOKIES, matchDifference, cookieMatcher, request.getCookies());
                    if (failFast(cookieMatcher, matchDifference, becauseBuilder, cookiesMatch, COOKIES)) {
                        return false;
                    }

                    boolean queryStringParametersMatches = matches(QUERY, matchDifference, queryStringParameterMatcher, request.getQueryStringParameters());
                    if (failFast(queryStringParameterMatcher, matchDifference, becauseBuilder, queryStringParametersMatches, QUERY)) {
                        return false;
                    }

                    boolean keepAliveMatches = matches(KEEP_ALIVE, matchDifference, keepAliveMatcher, request.isKeepAlive());
                    if (failFast(keepAliveMatcher, matchDifference, becauseBuilder, keepAliveMatches, KEEP_ALIVE)) {
                        return false;
                    }

                    boolean sslMatches = matches(SSL_MATCHES, matchDifference, sslMatcher, request.isSecure());
                    if (failFast(sslMatcher, matchDifference, becauseBuilder, sslMatches, SSL_MATCHES)) {
                        return false;
                    }

                    return combinedResultAreTrue(matchDifference.getFailures() == 0, request.isNot(), this.httpRequest.isNot(), not);
                } else {
                    return combinedResultAreTrue(true, this.httpRequest.isNot(), not);
                }
            }
        }
        return false;
    }

    private boolean failFast(Matcher<?> matcher, MatchDifference matchDifference, StringBuilder becauseBuilder, boolean fieldMatches, String fieldName) {
        // update because builder
        if (!controlPlaneMatcher) {
            becauseBuilder
                .append(HttpRequestMatcher.EMPTY).append(NEW_LINE)
                .append(fieldName).append(SPACE).append((fieldMatches ? MATCHED : DID_NOT_MATCH));
            if (matchDifference.getDifferences(fieldName) != null && !matchDifference.getDifferences(fieldName).isEmpty()) {
                becauseBuilder
                    .append(": ").append(NEW_LINE).append(NEW_LINE)
                    .append(Joiner.on(NEW_LINE).join(matchDifference.getDifferences(fieldName)));
            }
        }
        if (!fieldMatches) {
            if (!controlPlaneMatcher) {
                if (matchDifference.getHttpRequest().isNot()) {
                    becauseBuilder
                        .append(COMMA)
                        .append(NEW_LINE)
                        .append("request 'not' operator is enabled");
                }
                if (this.httpRequest.isNot()) {
                    becauseBuilder
                        .append(COMMA)
                        .append(NEW_LINE)
                        .append("expectation's request 'not' operator is enabled");
                }
                if (not) {
                    becauseBuilder
                        .append(COMMA)
                        .append(NEW_LINE)
                        .append("expectation's request matcher 'not' operator is enabled");
                }
            }
        }
        // update match difference and potentially fail fast
        if (!fieldMatches) {
            matchDifference.incrementFailures();
        }
        if (matcher != null && !matcher.isBlank() && matchersFailFast()) {
            return combinedResultAreTrue(matchDifference.getFailures() != 0, matchDifference.getHttpRequest().isNot(), this.httpRequest.isNot(), not);
        }
        return false;
    }

    /**
     * true for odd number of false inputs
     */
    private static boolean combinedResultAreTrue(boolean... inputs) {
        int count = 0;
        for (boolean input : inputs) {
            count += (input ? 1 : 0);
        }
        return count % 2 != 0;
    }

    @SuppressWarnings("unchecked")
    private boolean bodyMatches(MatchDifference context, HttpRequest request) {
        boolean bodyMatches;
        String bodyAsString = request.getBody() != null ? new String(request.getBody().getRawBytes(), request.getBody().getCharset(DEFAULT_HTTP_CHARACTER_SET)) : "";
        if (bodyMatcher instanceof BinaryMatcher) {
            bodyMatches = matches(BODY, context, bodyMatcher, request.getBodyAsRawBytes());
        } else {
            if (bodyMatcher instanceof ExactStringMatcher ||
                bodyMatcher instanceof SubStringMatcher ||
                bodyMatcher instanceof RegexStringMatcher ||
                bodyMatcher instanceof XmlStringMatcher) {
                bodyMatches = matches(BODY, context, bodyMatcher, string(bodyAsString));
            } else {
                bodyMatches = matches(BODY, context, bodyMatcher, bodyAsString);
            }
        }
        if (!bodyMatches) {
            try {
                BodyDTO bodyDTO = objectMapper.readValue(bodyAsString, BodyDTO.class);
                bodyMatches = bodyDTOMatcher.equals(bodyDTO);
            } catch (Throwable e) {
                // ignore this exception as this exception would typically get thrown for "normal" HTTP requests (i.e. not clear or retrieve)
            }
        }
        return bodyMatches;
    }

    private <T> boolean matches(String fieldName, MatchDifference context, Matcher<T> matcher, T t) {
        if (context != null) {
            context.currentField(fieldName);
        }
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
