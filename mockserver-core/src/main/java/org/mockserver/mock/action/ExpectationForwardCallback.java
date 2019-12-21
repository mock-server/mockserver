package org.mockserver.mock.action;

import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

/**
 * @author jamesdbloom
 */
public interface ExpectationForwardCallback extends ExpectationCallback<HttpRequest> {

    /**
     * Called for every request when expectation condition has been satisfied.
     * The request that satisfied the expectation condition is passed as the
     * parameter and the return value is the request that will be proxied.
     *
     * @param httpRequest the request that satisfied the expectation condition
     * @return the request that will be proxied
     */
    HttpRequest handle(HttpRequest httpRequest) throws Exception;

    /**
     * Called for every response received from a proxied request, the return
     * value is the returned by MockServer.
     *
     * @param httpRequest the request that was proxied
     * @param httpResponse the response the MockServer will return
     * @return the request that will be proxied
     */
    default HttpResponse handle(HttpRequest httpRequest, HttpResponse httpResponse) throws Exception {
        return httpResponse;
    }

}
