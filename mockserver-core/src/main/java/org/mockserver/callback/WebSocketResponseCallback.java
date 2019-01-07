package org.mockserver.callback;

import org.mockserver.model.HttpResponse;

/**
 * @author jamesdbloom
 */
public interface WebSocketResponseCallback {

    void handle(HttpResponse httpResponse);

}
