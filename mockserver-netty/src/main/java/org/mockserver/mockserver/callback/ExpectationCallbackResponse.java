package org.mockserver.mockserver.callback;

import org.mockserver.model.HttpResponse;

/**
 * @author jamesdbloom
 */
public interface ExpectationCallbackResponse {

    public void handle(HttpResponse httpResponse);
}
