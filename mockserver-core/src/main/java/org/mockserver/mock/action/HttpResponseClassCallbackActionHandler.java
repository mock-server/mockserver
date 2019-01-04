package org.mockserver.mock.action;

import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpClassCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.mockserver.model.HttpResponse.notFoundResponse;

/**
 * @author jamesdbloom
 */
public class HttpResponseClassCallbackActionHandler {
    private final MockServerLogger mockServerLogger;

    public HttpResponseClassCallbackActionHandler(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
    }

    public HttpResponse handle(HttpClassCallback httpClassCallback, HttpRequest request) {
        return invokeCallbackMethod(httpClassCallback, request);
    }

    private ExpectationResponseCallback instantiateCallback(HttpClassCallback httpClassCallback) {
        try {
            Class expectationResponseCallbackClass = Class.forName(httpClassCallback.getCallbackClass());
            if (ExpectationResponseCallback.class.isAssignableFrom(expectationResponseCallbackClass)) {
                Constructor<? extends ExpectationResponseCallback> constructor = expectationResponseCallbackClass.getConstructor();
                return constructor.newInstance();
            } else {
                mockServerLogger.error(httpClassCallback.getCallbackClass() + " does not implement " + ExpectationForwardCallback.class.getCanonicalName() + " which required for forwarded requests generated from a class callback");
            }
        } catch (ClassNotFoundException e) {
            mockServerLogger.error("ClassNotFoundException - while trying to instantiate ExpectationResponseCallback class \"" + httpClassCallback.getCallbackClass() + "\"", e);
        } catch (NoSuchMethodException e) {
            mockServerLogger.error("NoSuchMethodException - while trying to create default constructor on ExpectationResponseCallback class \"" + httpClassCallback.getCallbackClass() + "\"", e);
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            mockServerLogger.error("InvocationTargetException - while trying to execute default constructor on ExpectationResponseCallback class \"" + httpClassCallback.getCallbackClass() + "\"", e);
        }
        return null;
    }

    private HttpResponse invokeCallbackMethod(HttpClassCallback httpClassCallback, HttpRequest httpRequest) {
        if (httpRequest != null) {
            ExpectationResponseCallback expectationResponseCallback = instantiateCallback(httpClassCallback);
            if (expectationResponseCallback != null) {
                try {
                    return expectationResponseCallback.handle(httpRequest);
                } catch (Throwable throwable) {
                    mockServerLogger.error(httpClassCallback.getCallbackClass() + " throw exception while executing handle callback method", throwable);
                    return notFoundResponse();
                }
            } else {
                return notFoundResponse();
            }
        } else {
            return notFoundResponse();
        }
    }
}
