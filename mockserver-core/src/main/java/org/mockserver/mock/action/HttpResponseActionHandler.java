package org.mockserver.mock.action;

import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.proxy.filters.Filters;

/**
 * @author jamesdbloom
 */
public class HttpResponseActionHandler {
    private Filters filters;

    public HttpResponseActionHandler(Filters filters) {
        this.filters = filters;
    }

    public HttpResponse handle(HttpResponse httpResponse, HttpRequest httpRequest) {
        return filters.applyFilters(httpRequest, httpResponse);
    }
}
