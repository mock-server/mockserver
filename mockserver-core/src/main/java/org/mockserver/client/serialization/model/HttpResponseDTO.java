package org.mockserver.client.serialization.model;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.ModelObject;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class HttpResponseDTO extends ModelObject {
    private Integer statusCode;
    private String body;
    private List<CookieDTO> cookies = new ArrayList<CookieDTO>();
    private List<HeaderDTO> headers = new ArrayList<HeaderDTO>();
    private DelayDTO delay = new DelayDTO();

    public HttpResponseDTO(HttpResponse httpResponse) {
        statusCode = httpResponse.getStatusCode();
        body = httpResponse.getBody();
        headers = Lists.transform(httpResponse.getHeaders(), new Function<Header, HeaderDTO>() {
            public HeaderDTO apply(Header header) {
                return new HeaderDTO(header);
            }
        });
        cookies = Lists.transform(httpResponse.getCookies(), new Function<Cookie, CookieDTO>() {
            public CookieDTO apply(Cookie cookie) {
                return new CookieDTO(cookie);
            }
        });
        delay = new DelayDTO(httpResponse.getDelay());
    }

    public HttpResponseDTO() {
    }

    public HttpResponse buildObject() {
        return new HttpResponse()
                .withStatusCode(statusCode)
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
                .withDelay(delay.buildObject());
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public HttpResponseDTO setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
        return this;
    }

    public String getBody() {
        return body;
    }

    public HttpResponseDTO setBody(String body) {
        this.body = body;
        return this;
    }

    public List<CookieDTO> getCookies() {
        return cookies;
    }

    public HttpResponseDTO setCookies(List<CookieDTO> cookies) {
        this.cookies = cookies;
        return this;
    }

    public List<HeaderDTO> getHeaders() {
        return headers;
    }

    public HttpResponseDTO setHeaders(List<HeaderDTO> headers) {
        this.headers = headers;
        return this;
    }

    public DelayDTO getDelay() {
        return delay;
    }

    public HttpResponseDTO setDelay(DelayDTO delay) {
        this.delay = delay;
        return this;
    }
}

