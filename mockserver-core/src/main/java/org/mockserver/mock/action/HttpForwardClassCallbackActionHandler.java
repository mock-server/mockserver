package org.mockserver.mock.action;

import com.google.common.util.concurrent.SettableFuture;
import org.mockserver.logging.LoggingFormatter;
import org.mockserver.model.HttpClassCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.mockserver.model.HttpResponse.notFoundResponse;

/**
 * @author jamesdbloom
 */
public class HttpForwardClassCallbackActionHandler extends HttpForwardAction {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public HttpForwardClassCallbackActionHandler(LoggingFormatter logFormatter) {
        super(logFormatter);
    }

    public SettableFuture<HttpResponse> handle(HttpClassCallback httpClassCallback, HttpRequest request) {
        return invokeCallbackMethod(httpClassCallback, request);
    }

    private ExpectationForwardCallback instantiateCallback(HttpClassCallback httpClassCallback) {
        try {
            Class expectationCallbackClass = Class.forName(httpClassCallback.getCallbackClass());
            if (ExpectationForwardCallback.class.isAssignableFrom(expectationCallbackClass)) {
                Constructor<? extends ExpectationForwardCallback> constructor = expectationCallbackClass.getConstructor();
                return constructor.newInstance();
            }
        } catch (ClassNotFoundException e) {
            logger.error("ClassNotFoundException - while trying to instantiate ExpectationCallback class \"" + httpClassCallback.getCallbackClass() + "\"", e);
        } catch (NoSuchMethodException e) {
            logger.error("NoSuchMethodException - while trying to create default constructor on ExpectationCallback class \"" + httpClassCallback.getCallbackClass() + "\"", e);
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            logger.error("InvocationTargetException - while trying to execute default constructor on ExpectationCallback class \"" + httpClassCallback.getCallbackClass() + "\"", e);
        }
        return null;
    }

    private SettableFuture<HttpResponse> invokeCallbackMethod(HttpClassCallback httpClassCallback, HttpRequest httpRequest) {
        if (httpRequest != null) {
            ExpectationForwardCallback expectationCallback = instantiateCallback(httpClassCallback);
            if (expectationCallback != null) {
                return sendRequest(expectationCallback.handle(httpRequest), null);
            } else {
                return notFoundFuture();
            }
        } else {
            return notFoundFuture();
        }
    }
}
