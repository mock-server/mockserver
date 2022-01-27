package org.mockserver.mock.action.http;

import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.action.ExpectationCallback;
import org.mockserver.mock.action.ExpectationForwardAndResponseCallback;
import org.mockserver.mock.action.ExpectationForwardCallback;
import org.mockserver.model.HttpClassCallback;
import org.mockserver.model.HttpRequest;
import org.slf4j.event.Level;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * @author jamesdbloom
 */
public class HttpForwardClassCallbackActionHandler extends HttpForwardAction {

    public HttpForwardClassCallbackActionHandler(MockServerLogger mockServerLogger, NettyHttpClient httpClient) {
        super(mockServerLogger, httpClient);
    }

    public HttpForwardActionResult handle(HttpClassCallback httpClassCallback, HttpRequest request) {
        return invokeCallbackMethod(httpClassCallback, request);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T extends ExpectationCallback> T instantiateCallback(HttpClassCallback httpClassCallback, Class<T> callbackClass) {
        try {
            Class expectationCallbackClass = Class.forName(httpClassCallback.getCallbackClass());
            if (callbackClass.isAssignableFrom(expectationCallbackClass)) {
                Constructor<? extends T> constructor = expectationCallbackClass.getConstructor();
                return constructor.newInstance();
            } else {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.ERROR)
                        .setHttpRequest(null)
                        .setMessageFormat(httpClassCallback.getCallbackClass() + " does not implement " + callbackClass.getName() + " required for forwarded requests with class callback")
                );
            }
        } catch (ClassNotFoundException e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("ClassNotFoundException - while trying to instantiate " + callbackClass.getSimpleName() + " class \"" + httpClassCallback.getCallbackClass() + "\"")
                    .setThrowable(e)
            );
        } catch (NoSuchMethodException e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("NoSuchMethodException - while trying to create default constructor on " + callbackClass.getSimpleName() + " class \"" + httpClassCallback.getCallbackClass() + "\"")
                    .setThrowable(e)
            );
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("InvocationTargetException - while trying to execute default constructor on " + callbackClass.getSimpleName() + " class \"" + httpClassCallback.getCallbackClass() + "\"")
                    .setThrowable(e)
            );
        }
        return null;
    }

    private HttpForwardActionResult invokeCallbackMethod(HttpClassCallback httpClassCallback, HttpRequest httpRequest) {
        if (httpRequest != null) {
            ExpectationForwardCallback expectationForwardCallback = instantiateCallback(httpClassCallback, ExpectationForwardCallback.class);
            ExpectationForwardAndResponseCallback expectationForwardResponseCallback = instantiateCallback(httpClassCallback, ExpectationForwardAndResponseCallback.class);
            if (expectationForwardCallback != null || expectationForwardResponseCallback != null) {
                try {
                    HttpRequest request = expectationForwardCallback != null ? expectationForwardCallback.handle(httpRequest) : httpRequest;
                    return sendRequest(request, null, response -> {
                        try {
                            return expectationForwardResponseCallback != null ? expectationForwardResponseCallback.handle(request, response) : response;
                        } catch (Throwable throwable) {
                            mockServerLogger.logEvent(
                                new LogEntry()
                                    .setLogLevel(Level.ERROR)
                                    .setHttpRequest(httpRequest)
                                    .setMessageFormat(httpClassCallback.getCallbackClass() + " throw exception while executing handle callback method - " + throwable.getMessage())
                                    .setThrowable(throwable)
                            );
                            return response;
                        }
                    });
                } catch (Throwable throwable) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(Level.ERROR)
                            .setHttpRequest(httpRequest)
                            .setMessageFormat(httpClassCallback.getCallbackClass() + " throw exception while executing handle callback method - " + throwable.getMessage())
                            .setThrowable(throwable)
                    );
                    return notFoundFuture(httpRequest);
                }
            } else {
                return sendRequest(httpRequest, null, null);
            }
        } else {
            return notFoundFuture(null);
        }
    }
}
