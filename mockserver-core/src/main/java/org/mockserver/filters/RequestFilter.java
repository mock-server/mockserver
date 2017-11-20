package org.mockserver.filters;

import org.mockserver.model.HttpRequest;

/**
 * @author jamesdbloom
 */
public interface RequestFilter extends Filter {

    HttpRequest onRequest(HttpRequest httpRequest);

}
