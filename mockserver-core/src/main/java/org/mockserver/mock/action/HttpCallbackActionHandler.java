package org.mockserver.mock.action;

import org.mockserver.model.HttpCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.proxy.filters.Filters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.mockserver.model.HttpResponse.notFoundResponse;

/**
 * @author jamesdbloom
 */
public class HttpCallbackActionHandler {
    private final Filters filters;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public HttpCallbackActionHandler(Filters filters) {
        this.filters = filters;
    }

    public HttpResponse handle(HttpCallback httpCallback, HttpRequest httpRequest) {
        return sendRequest(httpCallback, filters.applyFilters(httpRequest));
    }

    private ExpectationCallback instantiateCallback(HttpCallback httpCallback) {
        try {
            Class expectationCallbackClass = Class.forName(httpCallback.getCallbackClass());
            if (ExpectationCallback.class.isAssignableFrom(expectationCallbackClass)) {
                Constructor<? extends ExpectationCallback> constructor = expectationCallbackClass.getConstructor();
                return constructor.newInstance();
            }
        } catch (ClassNotFoundException e) {
            logger.error("ClassNotFoundException - while trying to instantiate ExceptionCallback class \"" + httpCallback.getCallbackClass() + "\"", e);
        } catch (NoSuchMethodException e) {
            logger.error("NoSuchMethodException - while trying to create default constructor on ExceptionCallback class \"" + httpCallback.getCallbackClass() + "\"", e);
        } catch (InvocationTargetException e) {
            logger.error("InvocationTargetException - while trying to execute default constructor on ExceptionCallback class \"" + httpCallback.getCallbackClass() + "\"", e);
        } catch (InstantiationException e) {
            logger.error("InvocationTargetException - while trying to execute default constructor on ExceptionCallback class \"" + httpCallback.getCallbackClass() + "\"", e);
        } catch (IllegalAccessException e) {
            logger.error("InvocationTargetException - while trying to execute default constructor on ExceptionCallback class \"" + httpCallback.getCallbackClass() + "\"", e);
        }
        return null;
    }

    private HttpResponse sendRequest(HttpCallback httpCallback, HttpRequest httpRequest) {
        if (httpRequest != null) {
            ExpectationCallback expectationCallback = instantiateCallback(httpCallback);
            if (expectationCallback != null) {
                return filters.applyFilters(httpRequest, expectationCallback.handle(httpRequest));
            } else {
                return notFoundResponse();
            }
        } else {
            return notFoundResponse();
        }
    }
}
