package org.jamesdbloom.mockserver.client.serialization.model;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.jamesdbloom.mockserver.model.*;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class HttpRequestDTO extends ModelObject {
    private String method;
    private String path;
    private String body;
    private List<HeaderDTO> headers;
    private List<CookieDTO> cookies;
    private List<ParameterDTO> parameters;

    public HttpRequestDTO(HttpRequest httpRequest) {
        method = httpRequest.getMethod();
        path = httpRequest.getPath();
        body = httpRequest.getBody();
        headers = Lists.transform(httpRequest.getHeaders(), new Function<Header, HeaderDTO>() {
            public HeaderDTO apply(Header header) {
                return new HeaderDTO(header);
            }
        });
        cookies = Lists.transform(httpRequest.getCookies(), new Function<Cookie, CookieDTO>() {
            public CookieDTO apply(Cookie cookie) {
                return new CookieDTO(cookie);
            }
        });
        parameters = Lists.transform(httpRequest.getParameters(), new Function<Parameter, ParameterDTO>() {
            public ParameterDTO apply(Parameter parameter) {
                return new ParameterDTO(parameter);
            }
        });
    }

    public HttpRequestDTO() {
    }

    public HttpRequest buildObject() {
        return new HttpRequest()
                .withMethod(method)
                .withPath(path)
                .withBody(body)
                .withHeaders(Lists.transform(headers, new Function<HeaderDTO, Header>() {
                    public Header apply(HeaderDTO header) {
                        return header.buildObject();
                    }
                }))
                .withCookies(Lists.transform(cookies, new Function<CookieDTO, Cookie>() {
                    public Cookie apply(CookieDTO cookie) {
                        return cookie.buildObject();
                    }
                }))
                .withParameters(Lists.transform(parameters, new Function<ParameterDTO, Parameter>() {
                    public Parameter apply(ParameterDTO parameter) {
                        return parameter.buildObject();
                    }
                }));
    }

    public String getMethod() {
        return method;
    }

    public HttpRequestDTO setMethod(String method) {
        this.method = method;
        return this;
    }

    public String getPath() {
        return path;
    }

    public HttpRequestDTO setPath(String path) {
        this.path = path;
        return this;
    }

    public String getBody() {
        return body;
    }

    public HttpRequestDTO setBody(String body) {
        this.body = body;
        return this;
    }

    public List<HeaderDTO> getHeaders() {
        return headers;
    }

    public HttpRequestDTO setHeaders(List<HeaderDTO> headers) {
        this.headers = headers;
        return this;
    }

    public List<ParameterDTO> getParameters() {
        return parameters;
    }

    public HttpRequestDTO setParameters(List<ParameterDTO> parameters) {
        this.parameters = parameters;
        return this;
    }

    public List<CookieDTO> getCookies() {
        return cookies;
    }

    public HttpRequestDTO setCookies(List<CookieDTO> cookies) {
        this.cookies = cookies;
        return this;
    }
}
