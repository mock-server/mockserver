package org.mockserver.client.serialization.model;

import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import org.mockserver.model.HttpCallback;

/**
 * @author jamesdbloom
 */
public class HttpCallbackDTO extends ObjectWithReflectiveEqualsHashCodeToString {

    private String callbackClass;

    public HttpCallbackDTO(HttpCallback httpCallback) {
        if (httpCallback != null) {
            callbackClass = httpCallback.getCallbackClass();
        }
    }

    public HttpCallbackDTO() {
    }

    public HttpCallback buildObject() {
        return new HttpCallback()
                .withCallbackClass(callbackClass);
    }

    public String getCallbackClass() {
        return callbackClass;
    }

    public HttpCallbackDTO setCallbackClass(String callbackClass) {
        this.callbackClass = callbackClass;
        return this;
    }
}

