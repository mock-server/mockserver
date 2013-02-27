package org.jamesdbloom.mockserver.model;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.eclipse.jetty.http.HttpStatus;
import org.jamesdbloom.mockserver.client.serialization.model.CookieDTO;
import org.jamesdbloom.mockserver.client.serialization.model.HeaderDTO;
import org.jamesdbloom.mockserver.client.serialization.model.HttpResponseDTO;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class HttpResponse extends ModelObject {
    private Integer responseCode = HttpStatus.OK_200;
    private String body = "";
    private List<Cookie> cookies = new ArrayList<Cookie>();
    private List<Header> headers = new ArrayList<Header>();
    private Delay delay = new Delay(TimeUnit.MICROSECONDS, 0);

    public HttpResponse(HttpResponseDTO httpResponse) {
        responseCode = httpResponse.getResponseCode();
        body = httpResponse.getBody();
        headers = Lists.transform(httpResponse.getHeaders(), new Function<HeaderDTO, Header>() {
            public Header apply(HeaderDTO header) {
                return new Header(header);
            }
        });
        cookies = Lists.transform(httpResponse.getCookies(), new Function<CookieDTO, Cookie>() {
            public Cookie apply(CookieDTO cookie) {
                return new Cookie(cookie);
            }
        });
        delay = new Delay(httpResponse.getDelay());
    }

    public HttpResponse() { }

    public HttpResponse withStatusCode(Integer responseCode) {
        this.responseCode = responseCode;
        return this;
    }

    public Integer getResponseCode() {
        return responseCode;
    }

    public HttpResponse withBody(String body) {
        this.body = body;
        return this;
    }

    public String getBody() {
        return body;
    }

    public HttpResponse withCookies(List<Cookie> cookies) {
        this.cookies = cookies;
        return this;
    }

    public HttpResponse withCookies(Cookie... cookies) {
        this.cookies = Arrays.asList(cookies);
        return this;
    }

    public List<Cookie> getCookies() {
        return cookies;
    }

    public HttpResponse withHeaders(List<Header> headers) {
        this.headers = headers;
        return this;
    }

    public HttpResponse withHeaders(Header... headers) {
        this.headers = Arrays.asList(headers);
        return this;
    }

    public List<Header> getHeaders() {
        return headers;
    }

    public HttpResponse withDelay(Delay delay) {
        this.delay = delay;
        return this;
    }

    public Delay getDelay() {
        return delay;
    }

    public HttpResponse applyDelay() {
        if (delay != null) {
            delay.applyDelay();
        }
        return this;
    }
}

