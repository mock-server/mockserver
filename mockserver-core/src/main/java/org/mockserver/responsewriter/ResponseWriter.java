package org.mockserver.responsewriter;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

/**
 * @author jamesdbloom
 */
public interface ResponseWriter {

    void writeResponse(HttpRequest request, HttpResponseStatus responseStatus);

    void writeResponse(HttpRequest request, HttpResponseStatus responseStatus, String body, String contentType);

    void writeResponse(HttpRequest request, HttpResponse response);
}
