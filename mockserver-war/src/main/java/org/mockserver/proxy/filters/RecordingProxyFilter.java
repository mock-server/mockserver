package org.mockserver.proxy.filters;

import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

/**
 * @author jamesdbloom
 */
public class RecordingProxyFilter extends AbstractProxyFilter {

    public HttpRequest onRequest(HttpRequest httpRequest) {
        return httpRequest;
    }

    public HttpResponse onResponse(HttpRequest httpRequest, HttpResponse httpResponse) {
        return httpResponse;
    }
}
