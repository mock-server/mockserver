package org.mockserver.client.serialization.model;

import org.mockserver.model.Cookies;
import org.mockserver.model.Headers;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

/**
 * @author jamesdbloom
 */
public class HttpResponseDTO extends ObjectWithReflectiveEqualsHashCodeToString implements DTO<HttpResponse> {
    private Integer statusCode;
    private String reasonPhrase;
    private BodyWithContentTypeDTO body;
    private Cookies cookies = new Cookies();
    private Headers headers = new Headers();
    private DelayDTO delay;
    private ConnectionOptionsDTO connectionOptions;

    public HttpResponseDTO(HttpResponse httpResponse) {
        if (httpResponse != null) {
            statusCode = httpResponse.getStatusCode();
            reasonPhrase = httpResponse.getReasonPhrase();
            body = BodyWithContentTypeDTO.createDTO(httpResponse.getBody());
            headers = httpResponse.getHeaders();
            cookies = httpResponse.getCookies();
            delay = (httpResponse.getDelay() != null ? new DelayDTO(httpResponse.getDelay()) : null);
            connectionOptions = (httpResponse.getConnectionOptions() != null ? new ConnectionOptionsDTO(httpResponse.getConnectionOptions()) : null);
        }
    }

    public HttpResponseDTO() {
    }

    public HttpResponse buildObject() {
        return new HttpResponse()
            .withStatusCode(statusCode)
            .withReasonPhrase(reasonPhrase)
            .withBody(body != null ? body.buildObject() : null)
            .withHeaders(headers)
            .withCookies(cookies)
            .withDelay((delay != null ? delay.buildObject() : null))
            .withConnectionOptions((connectionOptions != null ? connectionOptions.buildObject() : null));
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public HttpResponseDTO setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
        return this;
    }

    public String getReasonPhrase() {
        return reasonPhrase;
    }

    public HttpResponseDTO setReasonPhrase(String reasonPhrase) {
        this.reasonPhrase = reasonPhrase;
        return this;
    }

    public BodyWithContentTypeDTO getBody() {
        return body;
    }

    public HttpResponseDTO setBody(BodyWithContentTypeDTO body) {
        this.body = body;
        return this;
    }

    public Headers getHeaders() {
        return headers;
    }

    public HttpResponseDTO setHeaders(Headers headers) {
        this.headers = headers;
        return this;
    }

    public Cookies getCookies() {
        return cookies;
    }

    public HttpResponseDTO setCookies(Cookies cookies) {
        this.cookies = cookies;
        return this;
    }

    public DelayDTO getDelay() {
        return delay;
    }

    public HttpResponseDTO setDelay(DelayDTO delay) {
        this.delay = delay;
        return this;
    }

    public ConnectionOptionsDTO getConnectionOptions() {
        return connectionOptions;
    }

    public HttpResponseDTO setConnectionOptions(ConnectionOptionsDTO connectionOptions) {
        this.connectionOptions = connectionOptions;
        return this;
    }
}
