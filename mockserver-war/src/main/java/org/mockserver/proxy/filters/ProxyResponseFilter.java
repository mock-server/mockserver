package org.mockserver.proxy.filters;

import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

/**
 * @author jamesdbloom
 */
public interface ProxyResponseFilter {

    public HttpResponse onResponse(HttpRequest httpRequest, HttpResponse httpResponse);

}
