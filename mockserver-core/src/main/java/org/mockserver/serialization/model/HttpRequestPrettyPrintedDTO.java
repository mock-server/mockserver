package org.mockserver.serialization.model;

import org.mockserver.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author jamesdbloom
 */
public class HttpRequestPrettyPrintedDTO extends RequestDefinition {
    private int hashCode;
    private String method = "";
    private String path = "";
    private final Map<String, List<String>> queryStringParameters = new HashMap<>();
    private BodyDTO body = null;
    private final Map<String, String> cookies = new HashMap<>();
    private final Map<String, List<String>> headers = new HashMap<>();
    private Boolean keepAlive = null;
    private Boolean secure = null;

    public HttpRequestPrettyPrintedDTO(HttpRequest httpRequest) {
        if (httpRequest != null) {
            method = httpRequest.getMethod().getValue();
            path = httpRequest.getPath().getValue();
            for (Header header : httpRequest.getHeaderList()) {
                headers.put(header.getName().getValue(), header.getValues().stream().map(NottableString::getValue).collect(Collectors.toList()));
            }
            for (Cookie cookie : httpRequest.getCookieList()) {
                cookies.put(cookie.getName().getValue(), cookie.getValue().getValue());
            }
            for (Parameter parameter : httpRequest.getQueryStringParameterList()) {
                queryStringParameters.put(parameter.getName().getValue(), parameter.getValues().stream().map(NottableString::getValue).collect(Collectors.toList()));
            }
            body = BodyDTO.createDTO(httpRequest.getBody());
            keepAlive = httpRequest.isKeepAlive();
            secure = httpRequest.isSecure();
            setNot(httpRequest.getNot());
        }
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public Map<String, List<String>> getQueryStringParameters() {
        return queryStringParameters;
    }

    public BodyDTO getBody() {
        return body;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public Map<String, String> getCookies() {
        return cookies;
    }

    public Boolean getKeepAlive() {
        return keepAlive;
    }

    public Boolean getSecure() {
        return secure;
    }

    public HttpRequestPrettyPrintedDTO shallowClone() {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (hashCode() != o.hashCode()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        HttpRequestPrettyPrintedDTO that = (HttpRequestPrettyPrintedDTO) o;
        return Objects.equals(method, that.method) &&
            Objects.equals(path, that.path) &&
            Objects.equals(queryStringParameters, that.queryStringParameters) &&
            Objects.equals(body, that.body) &&
            Objects.equals(cookies, that.cookies) &&
            Objects.equals(headers, that.headers) &&
            Objects.equals(keepAlive, that.keepAlive) &&
            Objects.equals(secure, that.secure);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(super.hashCode(), method, path, queryStringParameters, body, cookies, headers, keepAlive, secure);
        }
        return hashCode;
    }
}
