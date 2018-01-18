package org.mockserver.templates.engine.model;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.mockserver.client.serialization.model.BodyDTO;
import org.mockserver.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jamesdbloom
 */
public class HttpRequestTemplateObject extends ObjectWithJsonToString {
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
                headers.put(header.getName().getValue(), Lists.transform(header.getValues(), new Function<NottableString, String>() {
                    public String apply(NottableString input) {
                        return input.getValue();
                    }
                }));
            }
            for (Cookie cookie : httpRequest.getCookieList()) {
                cookies.put(cookie.getName().getValue(), cookie.getValue().getValue());
            }
            for (Parameter parameter : httpRequest.getQueryStringParameterList()) {
                queryStringParameters.put(parameter.getName().getValue(), Lists.transform(parameter.getValues(), new Function<NottableString, String>() {
                    public String apply(NottableString input) {
                        return input.getValue();
                    }
                }));
            }
            body = BodyDTO.createDTO(httpRequest.getBody());
            keepAlive = httpRequest.isKeepAlive();
            secure = httpRequest.isSecure();
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
