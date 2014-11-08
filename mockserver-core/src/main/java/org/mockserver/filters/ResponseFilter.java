package org.mockserver.filters;

import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

/**
 * @author jamesdbloom
 */
public interface ResponseFilter extends Filter {

    public HttpResponse onResponse(HttpRequest httpRequest, HttpResponse httpResponse);

}
