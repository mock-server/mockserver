package org.mockserver.matchers;

import com.google.common.base.Charsets;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.model.*;

import java.util.ArrayList;
import java.util.List;

import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class HttpRequestMatcher extends NotMatcher<HttpRequest> {

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
                    this.bodyMatcher = new ExactStringMatcher(string(stringBody.getValue(), stringBody.isNot()));
                    break;
                case REGEX:
                    RegexBody regexBody = (RegexBody) body;
                    this.bodyMatcher = new RegexStringMatcher(string(regexBody.getValue(), regexBody.isNot()));
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
            } else if (bodyMatcher instanceof ExactStringMatcher) {
                bodyMatches = matches(bodyMatcher, string(httpRequest.getBody() != null ? new String(httpRequest.getBody().getRawBytes(), Charsets.UTF_8) : ""));
            } else if (bodyMatcher instanceof RegexStringMatcher) {
                bodyMatches = matches(bodyMatcher, string(httpRequest.getBody() != null ? new String(httpRequest.getBody().getRawBytes(), Charsets.UTF_8) : ""));
            } else {
                bodyMatches = matches(bodyMatcher, (httpRequest.getBody() != null ? new String(httpRequest.getBody().getRawBytes(), Charsets.UTF_8) : ""));
            }
            boolean headersMatch = matches(headerMatcher, (httpRequest.getHeaders() != null ? new ArrayList<KeyToMultiValue>(httpRequest.getHeaders()) : null));
            boolean cookiesMatch = matches(cookieMatcher, (httpRequest.getCookies() != null ? new ArrayList<KeyAndValue>(httpRequest.getCookies()) : null));
            boolean result = methodMatches && pathMatches && queryStringParametersMatches && bodyMatches && headersMatch && cookiesMatch;
            if (!result && logger.isInfoEnabled()) {
                logger.info("request:" + System.getProperty("line.separator") + System.getProperty("line.separator") +
                        String.valueOf(httpRequest).replaceAll("(?m)^", "\t") + System.getProperty("line.separator") + System.getProperty("line.separator") +
                        "DID" + (not ? " NOT" : "") + " match expectation:" + System.getProperty("line.separator") + System.getProperty("line.separator") +
                        String.valueOf(this).replaceAll("(?m)^", "\t") + System.getProperty("line.separator") + System.getProperty("line.separator") +
                        "because:" + System.getProperty("line.separator") + System.getProperty("line.separator") +
                        "methodMatches = " + methodMatches + System.getProperty("line.separator") +
                        "pathMatches = " + pathMatches + System.getProperty("line.separator") +
                        "queryStringParametersMatch = " + queryStringParametersMatches + System.getProperty("line.separator") +
                        "bodyMatches = " + bodyMatches + System.getProperty("line.separator") +
                        "headersMatch = " + headersMatch + System.getProperty("line.separator") +
                        "cookiesMatch = " + cookiesMatch + System.getProperty("line.separator"));
            }
            return httpRequest.isNot() == (this.httpRequest.isNot() == (not != result));
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
