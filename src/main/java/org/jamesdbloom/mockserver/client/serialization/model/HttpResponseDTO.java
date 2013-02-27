package org.jamesdbloom.mockserver.client.serialization.model;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.jamesdbloom.mockserver.model.*;

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

    public Integer getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(Integer responseCode) {
        this.responseCode = responseCode;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public List<CookieDTO> getCookies() {
        return cookies;
    }

    public void setCookies(List<CookieDTO> cookies) {
        this.cookies = cookies;
    }

    public List<HeaderDTO> getHeaders() {
        return headers;
    }

    public void setHeaders(List<HeaderDTO> headers) {
        this.headers = headers;
    }

    public DelayDTO getDelay() {
        return delay;
    }

    public void setDelay(DelayDTO delay) {
        this.delay = delay;
    }
}

