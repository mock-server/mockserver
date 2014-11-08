package org.mockserver.filters;

import org.mockserver.model.HttpRequest;

/**
 * @author jamesdbloom
 */
public interface RequestFilter extends Filter {

    public HttpRequest onRequest(HttpRequest httpRequest);

}
