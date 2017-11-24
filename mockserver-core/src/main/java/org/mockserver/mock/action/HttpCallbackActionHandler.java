package org.mockserver.mock.action;

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
public class HttpCallbackActionHandler {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public HttpResponse handle(HttpClassCallback httpClassCallback, HttpRequest httpRequest) {
        return invokeCallbackMethod(httpClassCallback, httpRequest);
    }

    private ExpectationCallback instantiateCallback(HttpClassCallback httpClassCallback) {
        try {
            Class expectationCallbackClass = Class.forName(httpClassCallback.getCallbackClass());
            if (ExpectationCallback.class.isAssignableFrom(expectationCallbackClass)) {
                Constructor<? extends ExpectationCallback> constructor = expectationCallbackClass.getConstructor();
                return constructor.newInstance();
            }
        } catch (ClassNotFoundException e) {
            logger.error("ClassNotFoundException - while trying to instantiate ExpectationCallback class \"" + httpClassCallback.getCallbackClass() + "\"", e);
        } catch (NoSuchMethodException e) {
            logger.error("NoSuchMethodException - while trying to create default constructor on ExpectationCallback class \"" + httpClassCallback.getCallbackClass() + "\"", e);
        } catch (InvocationTargetException e) {
            logger.error("InvocationTargetException - while trying to execute default constructor on ExpectationCallback class \"" + httpClassCallback.getCallbackClass() + "\"", e);
        } catch (InstantiationException e) {
            logger.error("InvocationTargetException - while trying to execute default constructor on ExpectationCallback class \"" + httpClassCallback.getCallbackClass() + "\"", e);
        } catch (IllegalAccessException e) {
            logger.error("InvocationTargetException - while trying to execute default constructor on ExpectationCallback class \"" + httpClassCallback.getCallbackClass() + "\"", e);
        }
        return null;
    }

    private HttpResponse invokeCallbackMethod(HttpClassCallback httpClassCallback, HttpRequest httpRequest) {
        if (httpRequest != null) {
            ExpectationCallback expectationCallback = instantiateCallback(httpClassCallback);
            if (expectationCallback != null) {
                return expectationCallback.handle(httpRequest);
            } else {
                return notFoundResponse();
            }
        } else {
            return notFoundResponse();
        }
    }
}
