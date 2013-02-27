package org.jamesdbloom.mockserver.client.serialization.model;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.jamesdbloom.mockserver.model.Cookie;
import org.jamesdbloom.mockserver.model.Header;
import org.jamesdbloom.mockserver.model.HttpResponse;
import org.jamesdbloom.mockserver.model.ModelObject;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class HttpResponseDTO extends ModelObject {
    private Integer responseCode;
    private String body;
    private List<CookieDTO> cookies;
    private List<HeaderDTO> headers;
    private DelayDTO delay;

    public HttpResponseDTO(HttpResponse httpResponse) {
        responseCode = httpResponse.getResponseCode();
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

    public Integer getResponseCode() {
        return responseCode;
    }

    public HttpResponseDTO setResponseCode(Integer responseCode) {
        this.responseCode = responseCode;
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

