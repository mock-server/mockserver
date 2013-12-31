package org.mockserver.proxy.filters;

import org.mockserver.model.HttpRequest;

/**
 * @author jamesdbloom
 */
public interface ProxyRequestFilter {

    public HttpRequest onRequest(HttpRequest httpRequest);

}
