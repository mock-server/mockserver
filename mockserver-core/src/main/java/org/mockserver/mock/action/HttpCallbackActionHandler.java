package org.mockserver.mock.action;

import io.netty.handler.codec.http.HttpHeaders;
import org.apache.http.client.utils.URIBuilder;
import org.mockserver.client.http.ApacheHttpClient;
import org.mockserver.model.HttpCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Parameter;
import org.mockserver.proxy.filters.Filters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;

import static org.mockserver.model.Header.header;

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
            return filters.applyFilters(httpRequest, instantiateCallback(httpCallback).handle(httpRequest));
        } else {
            return null;
        }
    }
}
