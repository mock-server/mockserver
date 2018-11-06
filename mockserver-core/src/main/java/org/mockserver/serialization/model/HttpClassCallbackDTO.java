package org.mockserver.serialization.model;

import org.mockserver.model.HttpClassCallback;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

/**
 * @author jamesdbloom
 */
public class HttpClassCallbackDTO extends ObjectWithReflectiveEqualsHashCodeToString implements DTO<HttpClassCallback> {

    private String callbackClass;

    public HttpClassCallbackDTO(HttpClassCallback httpClassCallback) {
        if (httpClassCallback != null) {
            callbackClass = httpClassCallback.getCallbackClass();
        }
    }

    public HttpClassCallbackDTO() {
    }

    public HttpClassCallback buildObject() {
        return new HttpClassCallback()
            .withCallbackClass(callbackClass);
    }

    public String getCallbackClass() {
        return callbackClass;
    }

    public HttpClassCallbackDTO setCallbackClass(String callbackClass) {
        this.callbackClass = callbackClass;
        return this;
    }
}

