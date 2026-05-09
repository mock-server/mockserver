package org.mockserver.templates.engine.model;

import org.mockserver.model.*;
import org.mockserver.serialization.model.BodyDTO;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class HttpResponseTemplateObject extends ObjectWithJsonToString {
    private Integer statusCode;
    private String reasonPhrase;
    private final Map<String, List<String>> headers = new LinkedHashMap<>();
    private final Map<String, String> cookies = new HashMap<>();
    private BodyDTO body = null;

    public HttpResponseTemplateObject(HttpResponse httpResponse) {
        if (httpResponse != null) {
            statusCode = httpResponse.getStatusCode();
            reasonPhrase = httpResponse.getReasonPhrase();
            for (Header header : httpResponse.getHeaderList()) {
                headers.put(header.getName().getValue(), header.getValues().stream().map(NottableString::getValue).collect(Collectors.toList()));
            }
            for (Cookie cookie : httpResponse.getCookieList()) {
                cookies.put(cookie.getName().getValue(), cookie.getValue().getValue());
            }
            body = BodyDTO.createDTO(httpResponse.getBody());
        }
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public String getReasonPhrase() {
        return reasonPhrase;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public Map<String, String> getCookies() {
        return cookies;
    }

    public String getBody() {
        return BodyDTO.toString(body);
    }

}
