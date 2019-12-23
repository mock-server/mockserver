package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * @author jamesdbloom
 */
public class HttpError extends Action<HttpError> {
    private Boolean dropConnection;
    private byte[] responseBytes;

    public static HttpError error() {
        return new HttpError();
    }

    /**
     * Forces the connection to be dropped without any response being returned
     *
     * @param dropConnection if true the connection is drop without any response being returned
     */
    public HttpError withDropConnection(Boolean dropConnection) {
        this.dropConnection = dropConnection;
        return this;
    }

    public Boolean getDropConnection() {
        return dropConnection;
    }

    /**
     * The raw response to be returned, allowing the expectation to specify any invalid response as a raw byte[]
     *
     * @param responseBytes the exact bytes that will be returned
     */
    public HttpError withResponseBytes(byte[] responseBytes) {
        this.responseBytes = responseBytes;
        return this;
    }

    public byte[] getResponseBytes() {
        return responseBytes;
    }

    @Override
    @JsonIgnore
    public Type getType() {
        return Type.ERROR;
    }
}

