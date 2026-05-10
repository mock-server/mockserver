package org.mockserver.serialization.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.mockserver.model.HttpError;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

/**
 * @author jamesdbloom
 */
public class HttpErrorDTO extends ObjectWithReflectiveEqualsHashCodeToString implements DTO<HttpError> {
    private DelayDTO delay;
    private Boolean dropConnection;
    private byte[] responseBytes;
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean primary;

    public HttpErrorDTO(HttpError httpError) {
        if (httpError != null) {
            if (httpError.getDelay() != null) {
                delay = new DelayDTO(httpError.getDelay());
            }
            dropConnection = httpError.getDropConnection();
            responseBytes = httpError.getResponseBytes();
            primary = httpError.isPrimary();
        }
    }

    public HttpErrorDTO() {
    }

    public HttpError buildObject() {
        return new HttpError()
            .withDelay((delay != null ? delay.buildObject() : null))
            .withDropConnection(dropConnection)
            .withResponseBytes(responseBytes)
            .withPrimary(primary);
    }

    public DelayDTO getDelay() {
        return delay;
    }

    public HttpErrorDTO setDelay(DelayDTO host) {
        this.delay = host;
        return this;
    }

    public Boolean getDropConnection() {
        return dropConnection;
    }

    public HttpErrorDTO setDropConnection(Boolean port) {
        this.dropConnection = port;
        return this;
    }

    public byte[] getResponseBytes() {
        return responseBytes;
    }

    public HttpErrorDTO setResponseBytes(byte[] scheme) {
        this.responseBytes = scheme;
        return this;
    }

    public boolean isPrimary() {
        return primary;
    }

    public HttpErrorDTO setPrimary(boolean primary) {
        this.primary = primary;
        return this;
    }
}

