package org.mockserver.serialization.model;

import org.mockserver.model.HttpResponseModifier;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

public class HttpResponseModifierDTO extends ObjectWithReflectiveEqualsHashCodeToString implements DTO<HttpResponseModifier> {

    private HeadersModifierDTO headers;
    private CookiesModifierDTO cookies;

    public HttpResponseModifierDTO() {
    }

    public HttpResponseModifierDTO(HttpResponseModifier httpResponseModifier) {
        if (httpResponseModifier != null) {
            headers = httpResponseModifier.getHeaders() != null ? new HeadersModifierDTO(httpResponseModifier.getHeaders()) : null;
            cookies = httpResponseModifier.getCookies() != null ? new CookiesModifierDTO(httpResponseModifier.getCookies()) : null;
        }
    }

    public HttpResponseModifier buildObject() {
        return new HttpResponseModifier()
            .withHeaders(headers != null ? headers.buildObject() : null)
            .withCookies(cookies != null ? cookies.buildObject() : null);
    }

    public HeadersModifierDTO getHeaders() {
        return headers;
    }

    public HttpResponseModifierDTO setHeaders(HeadersModifierDTO headers) {
        this.headers = headers;
        return this;
    }

    public CookiesModifierDTO getCookies() {
        return cookies;
    }

    public HttpResponseModifierDTO setCookies(CookiesModifierDTO cookies) {
        this.cookies = cookies;
        return this;
    }
}
