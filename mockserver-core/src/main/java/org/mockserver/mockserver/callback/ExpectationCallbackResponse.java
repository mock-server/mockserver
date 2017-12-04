package org.mockserver.mockserver.callback;

import org.mockserver.model.HttpResponse;

/**
 * @author jamesdbloom
 */
public interface ExpectationCallbackResponse {

    void handle(HttpResponse httpResponse);
}
