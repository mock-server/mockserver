package org.mockserver.mock.action;

import com.google.common.util.concurrent.SettableFuture;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpClassCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * @author jamesdbloom
 */
public class HttpForwardClassCallbackActionHandler extends HttpForwardAction {

    public HttpForwardClassCallbackActionHandler(MockServerLogger mockServerLogger, NettyHttpClient httpClient) {
        super(mockServerLogger, httpClient);
    }

    public SettableFuture<HttpResponse> handle(HttpClassCallback httpClassCallback, HttpRequest request) {
        return invokeCallbackMethod(httpClassCallback, request);
    }

    private ExpectationForwardCallback instantiateCallback(HttpClassCallback httpClassCallback) {
        try {
            Class expectationResponseCallbackClass = Class.forName(httpClassCallback.getCallbackClass());
            if (ExpectationForwardCallback.class.isAssignableFrom(expectationResponseCallbackClass)) {
                Constructor<? extends ExpectationForwardCallback> constructor = expectationResponseCallbackClass.getConstructor();
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

    private SettableFuture<HttpResponse> invokeCallbackMethod(HttpClassCallback httpClassCallback, HttpRequest httpRequest) {
        if (httpRequest != null) {
            ExpectationForwardCallback expectationForwardCallback = instantiateCallback(httpClassCallback);
            if (expectationForwardCallback != null) {
                return sendRequest(expectationForwardCallback.handle(httpRequest), null);
            } else {
                return notFoundFuture();
            }
        } else {
            return notFoundFuture();
        }
    }
}
