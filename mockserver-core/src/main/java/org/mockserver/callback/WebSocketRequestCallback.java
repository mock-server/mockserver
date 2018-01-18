package org.mockserver.callback;

import org.mockserver.model.HttpRequest;

/**
 * @author jamesdbloom
 */
public interface WebSocketRequestCallback {

    void handle(HttpRequest httpRequest);
}
