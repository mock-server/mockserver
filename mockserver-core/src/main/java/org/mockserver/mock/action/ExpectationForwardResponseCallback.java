package org.mockserver.mock.action;

import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

/**
 * @author jamesdbloom
 */
public interface ExpectationForwardResponseCallback extends ExpectationCallback<HttpRequest> {

    /**
     * Called for every response received from a proxied request, the return
     * value is the returned by MockServer.
     *
     * @param httpRequest the request that was proxied
     * @param httpResponse the response the MockServer will return
     * @return the request that will be proxied
     */
    HttpResponse handle(HttpRequest httpRequest, HttpResponse httpResponse) throws Exception;

}
