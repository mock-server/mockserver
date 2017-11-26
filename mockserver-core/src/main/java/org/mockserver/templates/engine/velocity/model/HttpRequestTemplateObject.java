package org.mockserver.templates.engine.velocity.model;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.mockserver.client.serialization.model.*;
import org.mockserver.model.*;

import java.util.ArrayList;
import java.util.List;

import static org.mockserver.model.StringBody.exact;

/**
 * @author jamesdbloom
 */
public class HttpRequestTemplateObject extends ObjectWithJsonToString {
    private String method = "";
    private String path = "";
    private List<KeyToMultiValueTemplateObject> queryStringParameters = new ArrayList<KeyToMultiValueTemplateObject>();
    private BodyDTO body = null;
    private List<KeyAndValueTemplateObject> cookies = new ArrayList<KeyAndValueTemplateObject>();
    private List<KeyToMultiValueTemplateObject> headers = new ArrayList<KeyToMultiValueTemplateObject>();
    private Boolean keepAlive = null;
    private Boolean secure = null;

    public HttpRequestTemplateObject(HttpRequest httpRequest) {
        if (httpRequest != null) {
            method = httpRequest.getMethod().getValue();
            path = httpRequest.getPath().getValue();
            headers = Lists.transform(httpRequest.getHeaders(), new Function<Header, KeyToMultiValueTemplateObject>() {
                public KeyToMultiValueTemplateObject apply(Header header) {
                    return new KeyToMultiValueTemplateObject(header);
                }
            });
            cookies = Lists.transform(httpRequest.getCookies(), new Function<Cookie, KeyAndValueTemplateObject>() {
                public KeyAndValueTemplateObject apply(Cookie cookie) {
                    return new KeyAndValueTemplateObject(cookie);
                }
            });
            queryStringParameters = Lists.transform(httpRequest.getQueryStringParameters(), new Function<Parameter, KeyToMultiValueTemplateObject>() {
                public KeyToMultiValueTemplateObject apply(Parameter parameter) {
                    return new KeyToMultiValueTemplateObject(parameter);
                }
            });
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

    public List<KeyToMultiValueTemplateObject> getQueryStringParameters() {
        return queryStringParameters;
    }

    public BodyDTO getBody() {
        return body;
    }

    public List<KeyToMultiValueTemplateObject> getHeaders() {
        return headers;
    }

    public List<KeyAndValueTemplateObject> getCookies() {
        return cookies;
    }

    public Boolean getKeepAlive() {
        return keepAlive;
    }

    public Boolean getSecure() {
        return secure;
    }
}
