package org.mockserver.mock.action;

import org.mockserver.model.HttpResponse;

/**
 * @author jamesdbloom
 */
public class HttpResponseActionHandler {

    public HttpResponse handle(HttpResponse httpResponse) {
        return httpResponse.clone();
    }
}
