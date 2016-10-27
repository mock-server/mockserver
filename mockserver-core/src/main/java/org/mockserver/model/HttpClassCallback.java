package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * @author jamesdbloom
 */
public class HttpClassCallback extends Action {

    private String callbackClass;

    /**
     * Static builder to create a callback.
     */
    public static HttpClassCallback callback() {
        return new HttpClassCallback();
    }

    /**
     * Static builder to create a callback, which take a callback class as a string.
     *
     * The callback class must:
     * - implement org.mockserver.mock.action.ExpectationCallback
     * - have a zero argument constructor
     * - be available in the classpath of the MockServer
     *
     * @param callbackClass class to callback as a fully qualified class name, i.e. "com.foo.MyExpectationCallback"
     */
    public static HttpClassCallback callback(String callbackClass) {
        return new HttpClassCallback().withCallbackClass(callbackClass);
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
     * The callback class must:
     * - implement org.mockserver.mock.action.ExpectationCallback
     * - have a zero argument constructor
     * - be available in the classpath of the MockServer
     *
     * @param callbackClass class to callback as a fully qualified class name, i.e. "com.foo.MyExpectationCallback"
     */
    public HttpClassCallback withCallbackClass(String callbackClass) {
        this.callbackClass = callbackClass;
        return this;
    }
}
