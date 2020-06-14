package org.mockserver.templates.engine.model;

import org.mockserver.model.*;
import org.mockserver.serialization.model.BodyDTO;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author jamesdbloom
 */
public class HttpRequestTemplateObject extends RequestDefinition {
    private String method = "";
    private String path = "";
    private Map<String, List<String>> queryStringParameters = new HashMap<>();
    private BodyDTO body = null;
    private Map<String, String> cookies = new HashMap<>();
    private Map<String, List<String>> headers = new HashMap<>();
    private Boolean keepAlive = null;
    private Boolean secure = null;

    public HttpRequestTemplateObject(HttpRequest httpRequest) {
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
}
