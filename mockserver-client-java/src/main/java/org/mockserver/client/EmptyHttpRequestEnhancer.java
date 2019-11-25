package org.mockserver.client;

import org.mockserver.model.HttpRequest;

public class EmptyHttpRequestEnhancer implements HttpRequestEnhancer {
    @Override
    public HttpRequest enhance(HttpRequest request) {
        return request;
    }
}
