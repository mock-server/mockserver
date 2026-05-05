package org.mockserver.serialization.model;

import org.mockserver.model.Delay;
import org.mockserver.model.HttpClassCallback;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

/**
 * @author jamesdbloom
 */
public class HttpClassCallbackDTO extends ObjectWithReflectiveEqualsHashCodeToString implements DTO<HttpClassCallback> {

    private String callbackClass;
    private DelayDTO delay;

    public HttpClassCallbackDTO(HttpClassCallback httpClassCallback) {
        if (httpClassCallback != null) {
            callbackClass = httpClassCallback.getCallbackClass();
            if (httpClassCallback.getDelay() != null) {
                delay = new DelayDTO(httpClassCallback.getDelay());
            }
        }
    }

    public HttpClassCallbackDTO() {
    }

    public HttpClassCallback buildObject() {
        Delay delay = null;
        if (this.delay != null) {
            delay = this.delay.buildObject();
        }
        return new HttpClassCallback()
            .withCallbackClass(callbackClass)
            .withDelay(delay);
    }

    public String getCallbackClass() {
        return callbackClass;
    }

    public HttpClassCallbackDTO setCallbackClass(String callbackClass) {
        this.callbackClass = callbackClass;
        return this;
    }

    public DelayDTO getDelay() {
        return delay;
    }

    public void setDelay(DelayDTO delay) {
        this.delay = delay;
    }
}

