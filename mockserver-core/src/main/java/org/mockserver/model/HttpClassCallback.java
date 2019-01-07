package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * @author jamesdbloom
 */
public class HttpClassCallback extends Action<HttpClassCallback> {

    private String callbackClass;
    private Type actionType;

    /**
     * Static builder to create a callback.
     */
    public static HttpClassCallback callback() {
        return new HttpClassCallback();
    }

    /**
     * Static builder to create a callback, which take a callback class as a string.
     * <p>
     * The callback class must:
     * - implement org.mockserver.mock.action.ExpectationResponseCallback or org.mockserver.mock.action.ExpectationForwardCallback
     * - have a zero argument constructor
     * - be available in the classpath of the MockServer
     *
     * @param callbackClass class to callback as a fully qualified class name, i.e. "com.foo.MyExpectationResponseCallback"
     */
    public static HttpClassCallback callback(String callbackClass) {
        return new HttpClassCallback().withCallbackClass(callbackClass);
    }

    public String getCallbackClass() {
        return callbackClass;
    }

    /**
     * The class to callback as a fully qualified class name
     * <p>
     * The callback class must:
     * - implement org.mockserver.mock.action.ExpectationResponseCallback or org.mockserver.mock.action.ExpectationForwardCallback
     * - have a zero argument constructor
     * - be available in the classpath of the MockServer
     *
     * @param callbackClass class to callback as a fully qualified class name, i.e. "com.foo.MyExpectationResponseCallback"
     */
    public HttpClassCallback withCallbackClass(String callbackClass) {
        this.callbackClass = callbackClass;
        return this;
    }

    public HttpClassCallback withActionType(Type actionType) {
        this.actionType = actionType;
        return this;
    }

    @Override
    @JsonIgnore
    public Type getType() {
        return actionType;
    }
}
