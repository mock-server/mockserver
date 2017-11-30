package org.mockserver.mock.action;

import org.mockserver.filters.Filters;
import org.mockserver.filters.HopByHopHeaderFilter;
import org.mockserver.filters.RequestLogFilter;
import org.mockserver.model.*;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;

/**
 * @author jamesdbloom
 */
public class ActionHandler {

    private HttpForwardActionHandler httpForwardActionHandler = new HttpForwardActionHandler();
    private HttpForwardTemplateActionHandler httpForwardTemplateActionHandler = new HttpForwardTemplateActionHandler();
    private HttpResponseActionHandler httpResponseActionHandler = new HttpResponseActionHandler();
    private HttpResponseTemplateActionHandler httpResponseTemplateActionHandler = new HttpResponseTemplateActionHandler();
    private HttpCallbackActionHandler httpCallbackActionHandler = new HttpCallbackActionHandler();
    private Filters filters = new Filters();

    public ActionHandler(RequestLogFilter requestLogFilter) {
        filters.withFilter(request(), new HopByHopHeaderFilter());
        filters.withFilter(request(), requestLogFilter);
    }

    public HttpResponse processAction(Action action, HttpRequest httpRequest) {
        HttpResponse httpResponse = notFoundResponse();
        httpRequest = filters.applyOnRequestFilters(httpRequest);
        if (action != null) {
            switch (action.getType()) {
                case FORWARD:
                    httpResponse = httpForwardActionHandler.handle((HttpForward) action, httpRequest);
                    break;
                case FORWARD_TEMPLATE:
                    httpResponse = httpForwardTemplateActionHandler.handle((HttpTemplate) action, httpRequest);
                    break;
                case CALLBACK:
                    httpResponse = httpCallbackActionHandler.handle((HttpClassCallback) action, httpRequest);
                    break;
                case RESPONSE:
                    httpResponse = httpResponseActionHandler.handle((HttpResponse) action);
                    break;
                case RESPONSE_TEMPLATE:
                    httpResponse = httpResponseTemplateActionHandler.handle((HttpTemplate) action, httpRequest);
                    break;
            }
        }
        return filters.applyOnResponseFilters(httpRequest, httpResponse);
    }
}
