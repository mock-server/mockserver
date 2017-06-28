package org.mockserver.matchers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.client.serialization.model.*;
import org.mockserver.logging.LogFormatter;
import org.mockserver.model.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.mockserver.character.Character.NEW_LINE;
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
    private BooleanMatcher keepAliveMatcher = null;
    private BodyDTO bodyDTOMatcher = null;
    private BooleanMatcher sslMatcher = null;
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    public HttpRequestMatcher(HttpRequest httpRequest) {
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
        addFieldsExcludedFromEqualsAndHashCode("logFormatter", "objectMapper");
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
                    bodyDTOMatcher = new StringBodyDTO(stringBody);
                    this.bodyMatcher = new ExactStringMatcher(string(stringBody.getValue(), stringBody.getNot()));
                    break;
                case REGEX:
                    RegexBody regexBody = (RegexBody) body;
                    bodyDTOMatcher = new RegexBodyDTO(regexBody);
                    this.bodyMatcher = new RegexStringMatcher(string(regexBody.getValue(), regexBody.getNot()));
                    break;
                case PARAMETERS:
                    ParameterBody parameterBody = (ParameterBody) body;
                    bodyDTOMatcher = new ParameterBodyDTO(parameterBody);
                    this.bodyMatcher = new ParameterStringMatcher(parameterBody.getValue());
                    break;
                case XPATH:
                    XPathBody xPathBody = (XPathBody) body;
                    bodyDTOMatcher = new XPathBodyDTO(xPathBody);
                    this.bodyMatcher = new XPathStringMatcher(xPathBody.getValue());
                    break;
                case XML:
                    XmlBody xmlBody = (XmlBody) body;
                    bodyDTOMatcher = new XmlBodyDTO(xmlBody);
                    this.bodyMatcher = new XmlStringMatcher(xmlBody.getValue());
                    break;
                case JSON:
                    JsonBody jsonBody = (JsonBody) body;
                    bodyDTOMatcher = new JsonBodyDTO(jsonBody);
                    this.bodyMatcher = new JsonStringMatcher(jsonBody.getValue(), jsonBody.getMatchType());
                    break;
                case JSON_SCHEMA:
                    JsonSchemaBody jsonSchemaBody = (JsonSchemaBody) body;
                    bodyDTOMatcher = new JsonSchemaBodyDTO(jsonSchemaBody);
                    this.bodyMatcher = new JsonSchemaMatcher(jsonSchemaBody.getValue());
                    break;
                case XML_SCHEMA:
                    XmlSchemaBody xmlSchemaBody = (XmlSchemaBody) body;
                    bodyDTOMatcher = new XmlSchemaBodyDTO(xmlSchemaBody);
                    this.bodyMatcher = new XmlSchemaMatcher(xmlSchemaBody.getValue());
                    break;
                case BINARY:
                    BinaryBody binaryBody = (BinaryBody) body;
                    bodyDTOMatcher = new BinaryBodyDTO(binaryBody);
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

    private HttpRequestMatcher withKeepAlive(Boolean keepAlive) {
        this.keepAliveMatcher = new BooleanMatcher(keepAlive);
        return this;
    }

    private HttpRequestMatcher withSsl(Boolean isSsl) {
        this.sslMatcher = new BooleanMatcher(isSsl);
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
        } else {
            if (httpRequest != null) {
                boolean methodMatches = Strings.isNullOrEmpty(httpRequest.getMethod().getValue()) || matches(methodMatcher, httpRequest.getMethod());
                boolean pathMatches = Strings.isNullOrEmpty(httpRequest.getPath().getValue()) || matches(pathMatcher, httpRequest.getPath());
                boolean queryStringParametersMatches = matches(queryStringParameterMatcher, (httpRequest.getQueryStringParameters() != null ? new ArrayList<KeyToMultiValue>(httpRequest.getQueryStringParameters()) : null));
                boolean bodyMatches;
                String bodyAsString = httpRequest.getBody() != null ? new String(httpRequest.getBody().getRawBytes(), httpRequest.getBody().getCharset(Charsets.UTF_8)) : "";
                BodyDTO bodyDTO = null;
                try {
                    bodyDTO = objectMapper.readValue(bodyAsString, BodyDTO.class);
                } catch (IOException e) {
                    // ignore this exception as this exception will always get thrown for "normal" HTTP requests (i.e. not clear or retrieve)
                }
                if (bodyDTO == null || bodyDTOMatcher == null || bodyDTO.getType() == Body.Type.STRING) {
                    if (bodyMatcher instanceof BinaryMatcher) {
                        bodyMatches = matches(bodyMatcher, httpRequest.getBodyAsRawBytes());
                    } else {
                        if (bodyMatcher instanceof ExactStringMatcher || bodyMatcher instanceof RegexStringMatcher || bodyMatcher instanceof XmlStringMatcher) {
                            bodyMatches = matches(bodyMatcher, string(bodyAsString));
                        } else {
                            bodyMatches = matches(bodyMatcher, bodyAsString);
                        }
                    }
                } else {
                    bodyMatches = bodyDTOMatcher.equals(bodyDTO);
                }
                boolean headersMatch = matches(headerMatcher, (httpRequest.getHeaders() != null ? new ArrayList<KeyToMultiValue>(httpRequest.getHeaders()) : null));
                boolean cookiesMatch = matches(cookieMatcher, (httpRequest.getCookies() != null ? new ArrayList<KeyAndValue>(httpRequest.getCookies()) : null));
                boolean keepAliveMatches = matches(keepAliveMatcher, httpRequest.isKeepAlive());
                boolean sslMatches = matches(sslMatcher, httpRequest.isSecure());

                boolean totalResult = methodMatches && pathMatches && queryStringParametersMatches && bodyMatches && headersMatch && cookiesMatch && keepAliveMatches && sslMatches;
                boolean totalResultAfterNotOperatorApplied = httpRequest.isNot() == (this.httpRequest.isNot() == (not != totalResult));

                if (logMatchResults && logger.isInfoEnabled()) {
                    if (!totalResultAfterNotOperatorApplied) {
                        StringBuilder becauseBuilder = new StringBuilder();
                        becauseBuilder.append("method matches = ").append(methodMatches).append(NEW_LINE);
                        becauseBuilder.append("path matches = ").append(pathMatches).append(NEW_LINE);
                        becauseBuilder.append("query string parameters match = ").append(queryStringParametersMatches).append(NEW_LINE);
                        becauseBuilder.append("body matches = ").append(bodyMatches).append(NEW_LINE);
                        becauseBuilder.append("headers match = ").append(headersMatch).append(NEW_LINE);
                        becauseBuilder.append("cookies match = ").append(cookiesMatch).append(NEW_LINE);
                        becauseBuilder.append("keep-alive matches = ").append(keepAliveMatches).append(NEW_LINE);
                        becauseBuilder.append("ssl matches = ").append(sslMatches).append(NEW_LINE);
                        if (httpRequest.isNot()) {
                            becauseBuilder.append("request \'not\' operator is enabled").append(NEW_LINE);
                        }
                        if (this.httpRequest.isNot()) {
                            becauseBuilder.append("expectation's request \'not\' operator is enabled").append(NEW_LINE);
                        }
                        if (not) {
                            becauseBuilder.append("expectation's request matcher \'not\' operator is enabled").append(NEW_LINE);
                        }
                        logFormatter.infoLog("request:{}" + NEW_LINE + " did" + (totalResult ? "" : " not") + " match expectation:{}" + NEW_LINE + " because:{}", httpRequest, this, becauseBuilder.toString());
                    } else {
                        logFormatter.infoLog("request:{}" + NEW_LINE + " matched expectation:{}", httpRequest, this);
                    }
                }
                return totalResultAfterNotOperatorApplied;
            } else {
                return false;
            }
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
