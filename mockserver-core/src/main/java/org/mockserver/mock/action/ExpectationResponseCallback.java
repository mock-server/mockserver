package org.mockserver.mock.action;

import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

/**
 * @author jamesdbloom
 */
public interface ExpectationResponseCallback extends ExpectationCallback<HttpResponse> {

    /**
     * Called for every request when expectation condition has been satisfied.
     * The request that satisfied the expectation condition is passed as the
     * parameter and the return value is the request that will be returned.
     *
     * @param httpRequest the request that satisfied the expectation condition
     * @return the response that will be returned
     */
    HttpResponse handle(HttpRequest httpRequest);

}
