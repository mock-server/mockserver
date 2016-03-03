package org.mockserver.mock.action;

import org.mockserver.filters.Filters;
import org.mockserver.filters.HopByHopHeaderFilter;
import org.mockserver.filters.RequestLogFilter;
import org.mockserver.model.*;

import static org.mockserver.model.HttpResponse.notFoundResponse;

/**
 * @author jamesdbloom
 */
public class ActionHandler {

    private HttpForwardActionHandler httpForwardActionHandler = new HttpForwardActionHandler();
    private HttpCallbackActionHandler httpCallbackActionHandler = new HttpCallbackActionHandler();
    private HttpResponseActionHandler httpResponseActionHandler = new HttpResponseActionHandler();
    private Filters filters = new Filters();

    public ActionHandler(RequestLogFilter requestLogFilter) {
        filters.withFilter(new org.mockserver.model.HttpRequest(), new HopByHopHeaderFilter());
        filters.withFilter(new org.mockserver.model.HttpRequest(), requestLogFilter);
    }

    public HttpResponse processAction(Action action, HttpRequest httpRequest) {
        HttpResponse httpResponse = notFoundResponse();
        httpRequest = filters.applyOnRequestFilters(httpRequest);
        if (action != null) {
            switch (action.getType()) {
                case FORWARD:
                    httpResponse = httpForwardActionHandler.handle((HttpForward) action, httpRequest);
                    break;
                case CALLBACK:
                    httpResponse = httpCallbackActionHandler.handle((HttpCallback) action, httpRequest);
                    break;
                case RESPONSE:
                    httpResponse = httpResponseActionHandler.handle((HttpResponse) action);
                    break;
            }
        }
        return filters.applyOnResponseFilters(httpRequest, httpResponse);
    }
}
