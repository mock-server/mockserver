package org.mockserver.matchers;

import com.google.common.base.Charsets;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.logging.LogFormatter;
import org.mockserver.model.*;

import java.util.ArrayList;
import java.util.List;

import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class HttpRequestMatcher extends NotMatcher<HttpRequest> {

    private LogFormatter logFormatter = new LogFormatter(logger);
    private HttpRequest httpRequest;
    private RegexStringMatcher methodMatcher = null;
    private RegexStringMatcher pathMatcher = null;
    private MultiValueMapMatcher queryStringParameterMatcher = null;
    private BodyMatcher bodyMatcher = null;
    private MultiValueMapMatcher headerMatcher = null;
    private HashMapMatcher cookieMatcher = null;

    public HttpRequestMatcher(HttpRequest httpRequest) {
        this.httpRequest = httpRequest;
        if (httpRequest != null) {
            withMethod(httpRequest.getMethod());
            withPath(httpRequest.getPath());
            withQueryStringParameters(httpRequest.getQueryStringParameters());
            withBody(httpRequest.getBody());
            withHeaders(httpRequest.getHeaders());
            withCookies(httpRequest.getCookies());
        }
        addFieldsExcludedFromEqualsAndHashCode("logFormatter");
    }

    private HttpRequestMatcher withMethod(NottableString method) {
        this.methodMatcher = new RegexStringMatcher(method);
        return this;
    }

    private HttpRequestMatcher withPath(NottableString path) {
        this.pathMatcher = new RegexStringMatcher(path);
        return this;
    }

    private HttpRequestMatcher withQueryStringParameters(List<Parameter> parameters) {
        this.queryStringParameterMatcher = new MultiValueMapMatcher(KeyToMultiValue.toMultiMap(parameters));
        return this;
    }

    private HttpRequestMatcher withBody(Body body) {
        if (body != null) {
            switch (body.getType()) {
                case STRING:
                    StringBody stringBody = (StringBody) body;
                    this.bodyMatcher = new ExactStringMatcher(string(stringBody.getValue(), stringBody.getNot()));
                    break;
                case REGEX:
                    RegexBody regexBody = (RegexBody) body;
                    this.bodyMatcher = new RegexStringMatcher(string(regexBody.getValue(), regexBody.getNot()));
                    break;
                case PARAMETERS:
                    ParameterBody parameterBody = (ParameterBody) body;
                    this.bodyMatcher = new ParameterStringMatcher(parameterBody.getValue());
                    break;
                case XPATH:
                    XPathBody xPathBody = (XPathBody) body;
                    this.bodyMatcher = new XPathStringMatcher(xPathBody.getValue());
                    break;
                case JSON:
                    JsonBody jsonBody = (JsonBody) body;
                    this.bodyMatcher = new JsonStringMatcher(jsonBody.getValue(), jsonBody.getMatchType());
                    break;
                case JSON_SCHEMA:
                    JsonSchemaBody jsonSchemaBody = (JsonSchemaBody) body;
                    this.bodyMatcher = new JsonSchemaMatcher(jsonSchemaBody.getValue());
                    break;
                case BINARY:
                    BinaryBody binaryBody = (BinaryBody) body;
                    this.bodyMatcher = new BinaryMatcher(binaryBody.getValue());
                    break;
            }
            if (body.isNot()) {
                this.bodyMatcher = not(this.bodyMatcher);
            }
        }
        return this;
    }

    private HttpRequestMatcher withHeaders(Header... headers) {
        this.headerMatcher = new MultiValueMapMatcher(KeyToMultiValue.toMultiMap(headers));
        return this;
    }

    private HttpRequestMatcher withHeaders(List<Header> headers) {
        this.headerMatcher = new MultiValueMapMatcher(KeyToMultiValue.toMultiMap(headers));
        return this;
    }

    private HttpRequestMatcher withCookies(Cookie... cookies) {
        this.cookieMatcher = new HashMapMatcher(KeyAndValue.toHashMap(cookies));
        return this;
    }

    private HttpRequestMatcher withCookies(List<Cookie> cookies) {
        this.cookieMatcher = new HashMapMatcher(KeyAndValue.toHashMap(cookies));
        return this;
    }

    public boolean matches(HttpRequest httpRequest) {
        return matches(httpRequest, false);
    }

    public boolean matches(HttpRequest httpRequest, boolean logMatchResults) {
        if (httpRequest == this.httpRequest) {
            return true;
        } else if (this.httpRequest == null) {
            return true;
        } else if (httpRequest != null) {
            boolean methodMatches = matches(methodMatcher, httpRequest.getMethod());
            boolean pathMatches = matches(pathMatcher, httpRequest.getPath());
            boolean queryStringParametersMatches = matches(queryStringParameterMatcher, (httpRequest.getQueryStringParameters() != null ? new ArrayList<KeyToMultiValue>(httpRequest.getQueryStringParameters()) : null));
            boolean bodyMatches;
            if (bodyMatcher instanceof BinaryMatcher) {
                bodyMatches = matches(bodyMatcher, httpRequest.getBodyAsRawBytes());
            } else if (bodyMatcher instanceof ExactStringMatcher || bodyMatcher instanceof RegexStringMatcher) {
                bodyMatches = matches(bodyMatcher, string(httpRequest.getBody() != null ? new String(httpRequest.getBody().getRawBytes(), httpRequest.getBody().getCharset(Charsets.UTF_8)) : ""));
            } else {
                bodyMatches = matches(bodyMatcher, (httpRequest.getBody() != null ? new String(httpRequest.getBody().getRawBytes(), httpRequest.getBody().getCharset(Charsets.UTF_8)) : ""));
            }
            boolean headersMatch = matches(headerMatcher, (httpRequest.getHeaders() != null ? new ArrayList<KeyToMultiValue>(httpRequest.getHeaders()) : null));
            boolean cookiesMatch = matches(cookieMatcher, (httpRequest.getCookies() != null ? new ArrayList<KeyAndValue>(httpRequest.getCookies()) : null));
            boolean result = methodMatches && pathMatches && queryStringParametersMatches && bodyMatches && headersMatch && cookiesMatch;
            boolean resultAfterNotOperatorsApplied = httpRequest.isNot() == (this.httpRequest.isNot() == (not != result));
            if (logMatchResults && logger.isInfoEnabled()) {
                if (!resultAfterNotOperatorsApplied) {
                    String because = "" +
                            "methodMatches = " + methodMatches + System.getProperty("line.separator") +
                            "pathMatches = " + pathMatches + System.getProperty("line.separator") +
                            "queryStringParametersMatch = " + queryStringParametersMatches + System.getProperty("line.separator") +
                            "bodyMatches = " + bodyMatches + System.getProperty("line.separator") +
                            "headersMatch = " + headersMatch + System.getProperty("line.separator") +
                            "cookiesMatch = " + cookiesMatch +
                            (httpRequest.isNot() ? System.getProperty("line.separator") + "request not operator is enabled" : "") +
                            (this.httpRequest.isNot() ? System.getProperty("line.separator") + "expectation's request not operator is enabled" : "") +
                            (not ? System.getProperty("line.separator") + "expectation's request matcher not operator is enabled" : "");
                    logFormatter.infoLog("request:{}" + System.getProperty("line.separator") + " did" + (result ? "" : " not") + " match expectation:{}" + System.getProperty("line.separator") + " because:{}", httpRequest, this, because);
                } else {
                    logFormatter.infoLog("request:{}" + System.getProperty("line.separator") + " matched expectation:{}", httpRequest, this);
                }
            }
            return resultAfterNotOperatorsApplied;
        } else {
            return false;
        }
    }

    private <T> boolean matches(Matcher<T> matcher, T t) {
        boolean result = false;

        if (matcher == null) {
            result = true;
        } else if (matcher.matches(t)) {
            result = true;
        }

        return result;
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
}
