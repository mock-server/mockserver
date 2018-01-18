package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class HttpError extends Action {
    private Delay delay;
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

    /**
     * The delay before responding with this request as a Delay object, for example new Delay(TimeUnit.SECONDS, 3)
     *
     * @param delay a Delay object, for example new Delay(TimeUnit.SECONDS, 3)
     */
    public HttpError withDelay(Delay delay) {
        this.delay = delay;
        return this;
    }

    /**
     * The delay before responding with this request as a Delay object, for example new Delay(TimeUnit.SECONDS, 3)
     *
     * @param timeUnit a the time unit, for example TimeUnit.SECONDS
     * @param value    a the number of time units to delay the response
     */
    public HttpError withDelay(TimeUnit timeUnit, long value) {
        this.delay = new Delay(timeUnit, value);
        return this;
    }

    public Delay getDelay() {
        return delay;
    }

    @Override
    @JsonIgnore
    public Type getType() {
        return Type.ERROR;
    }
}

