package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.client.serialization.ObjectMapperFactory;

/**
 * @author jamesdbloom
 */
public class HttpCallback extends Action {

    private String callbackClass;

    public HttpCallback() {
    }

    public static HttpCallback callback() {
        return new HttpCallback();
    }

    @Override
    @JsonIgnore
    public Type getType() {
        return Type.CALLBACK;
    }

    public String getCallbackClass() {
        return callbackClass;
    }

    /**
     * The class to callback as a fully qualified class name
     *
     * This calls must:
     *  - implement org.mockserver.mock.action.ExpectationCallback
     *  - have a zero argument constructor
     *  - be available in the classpath of the MockServer
     *
     * @param callbackClass class to callback as a fully qualified class name, i.e. "com.foo.MyExpectationCallback"
     */
    public HttpCallback withCallbackClass(String callbackClass) {
        this.callbackClass = callbackClass;
        return this;
    }

    @Override
    public String toString() {
        try {
            return ObjectMapperFactory
                    .createObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(this);
        } catch (Exception e) {
            return super.toString();
        }
    }
}
