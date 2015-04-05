package org.mockserver.client.serialization.model;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.mockserver.model.*;

import java.util.ArrayList;
import java.util.List;

import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class HttpRequestDTO extends NotDTO {
    private NottableString method = string("");
    private NottableString path = string("");
    private List<ParameterDTO> queryStringParameters = new ArrayList<ParameterDTO>();
    private BodyDTO body;
    private List<CookieDTO> cookies = new ArrayList<CookieDTO>();
    private List<HeaderDTO> headers = new ArrayList<HeaderDTO>();

    public HttpRequestDTO(HttpRequest httpRequest) {
        this(httpRequest, false);
    }

    public HttpRequestDTO(HttpRequest httpRequest, boolean not) {
        super(not);
        if (httpRequest != null) {
            method = httpRequest.getMethod();
            path = httpRequest.getPath();
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
            queryStringParameters = Lists.transform(httpRequest.getQueryStringParameters(), new Function<Parameter, ParameterDTO>() {
                public ParameterDTO apply(Parameter parameter) {
                    return new ParameterDTO(parameter);
                }
            });
            body = BodyDTO.createDTO(httpRequest.getBody());
        }
    }

    public HttpRequestDTO() {
    }

    public HttpRequest buildObject() {
        return new HttpRequest()
                .withMethod(method)
                .withPath(path)
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
                .withQueryStringParameters(Lists.transform(queryStringParameters, new Function<ParameterDTO, Parameter>() {
                    public Parameter apply(ParameterDTO parameter) {
                        return parameter.buildObject();
                    }
                }))
                .withBody((body != null ? body.buildObject() : null));

    }

    public NottableString getMethod() {
        return method;
    }

    public HttpRequestDTO setMethod(NottableString method) {
        this.method = method;
        return this;
    }

    public NottableString getPath() {
        return path;
    }

    public HttpRequestDTO setPath(NottableString path) {
        this.path = path;
        return this;
    }

    public List<ParameterDTO> getQueryStringParameters() {
        return queryStringParameters;
    }

    public HttpRequestDTO setQueryStringParameters(List<ParameterDTO> queryStringParameters) {
        this.queryStringParameters = queryStringParameters;
        return this;
    }

    public BodyDTO getBody() {
        return body;
    }

    public HttpRequestDTO setBody(BodyDTO body) {
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

    public List<CookieDTO> getCookies() {
        return cookies;
    }

    public HttpRequestDTO setCookies(List<CookieDTO> cookies) {
        this.cookies = cookies;
        return this;
    }
}
