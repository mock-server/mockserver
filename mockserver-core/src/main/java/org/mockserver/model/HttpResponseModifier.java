package org.mockserver.model;

import java.util.List;
import java.util.Objects;

public class HttpResponseModifier extends ObjectWithJsonToString {

    private int hashCode;
    private HeadersModifier headers;
    private CookiesModifier cookies;

    public static HttpResponseModifier responseModifier() {
        return new HttpResponseModifier();
    }

    public HeadersModifier getHeaders() {
        return headers;
    }

    public HttpResponseModifier withHeaders(HeadersModifier headers) {
        this.headers = headers;
        this.hashCode = 0;
        return this;
    }

    public HttpResponseModifier withHeaders(Headers add, Headers replace, List<String> remove) {
        this.headers = new HeadersModifier()
            .withAdd(add)
            .withReplace(replace)
            .withRemove(remove);
        this.hashCode = 0;
        return this;
    }

    public CookiesModifier getCookies() {
        return cookies;
    }

    public HttpResponseModifier withCookies(CookiesModifier cookies) {
        this.cookies = cookies;
        this.hashCode = 0;
        return this;
    }

    public HttpResponseModifier withCookies(Cookies add, Cookies replace, List<String> remove) {
        this.cookies = new CookiesModifier()
            .withAdd(add)
            .withReplace(replace)
            .withRemove(remove);
        this.hashCode = 0;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (hashCode() != o.hashCode()) {
            return false;
        }
        HttpResponseModifier that = (HttpResponseModifier) o;
        return Objects.equals(headers, that.headers) &&
            Objects.equals(cookies, that.cookies);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(headers, cookies);
        }
        return hashCode;
    }

}
