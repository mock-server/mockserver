package org.mockserver.closurecallback.websocketregistry;

import org.mockserver.model.HttpResponse;

/**
 * @author jamesdbloom
 */
public interface WebSocketResponseCallback {

    void handle(HttpResponse httpResponse);

}
