package org.mockserver.client;


import org.mockserver.model.HttpRequest;

public interface HttpRequestEnhancer {
    HttpRequest enhance(HttpRequest request);
}
