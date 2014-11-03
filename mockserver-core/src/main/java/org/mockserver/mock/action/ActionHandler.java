package org.mockserver.mock.action;

import org.mockserver.model.*;
import org.mockserver.proxy.filters.Filters;
import org.mockserver.proxy.filters.HopByHopHeaderFilter;
import org.mockserver.proxy.filters.LogFilter;

import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class ActionHandler {

    private HttpForwardActionHandler httpForwardActionHandler;
    private HttpCallbackActionHandler httpCallbackActionHandler;
    private HttpResponseActionHandler httpResponseActionHandler;

    public ActionHandler(LogFilter logFilter) {
        Filters filters = new Filters();
        filters.withFilter(new org.mockserver.model.HttpRequest(), new HopByHopHeaderFilter());
        filters.withFilter(new org.mockserver.model.HttpRequest(), logFilter);
        httpResponseActionHandler = new HttpResponseActionHandler(filters);
        httpCallbackActionHandler = new HttpCallbackActionHandler(filters);
        httpForwardActionHandler = new HttpForwardActionHandler(filters);
    }

    public synchronized HttpResponse processAction(Action action, HttpRequest httpRequest) {
        if (action != null) {
            switch (action.getType()) {
                case FORWARD:
                    return httpForwardActionHandler.handle((HttpForward) action, httpRequest);
                case CALLBACK:
                    return httpCallbackActionHandler.handle((HttpCallback) action, httpRequest);
                case RESPONSE:
                    return httpResponseActionHandler.handle((HttpResponse) action, httpRequest);
                default:
                    return response().withStatusCode(404);
            }
        } else {
            return response().withStatusCode(404);
        }
    }
}
