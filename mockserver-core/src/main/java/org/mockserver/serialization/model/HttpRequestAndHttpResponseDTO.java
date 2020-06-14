package org.mockserver.serialization.model;

import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpRequestAndHttpResponse;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.ObjectWithJsonToString;

/**
 * @author jamesdbloom
 */
public class HttpRequestAndHttpResponseDTO extends ObjectWithJsonToString implements DTO<HttpRequestAndHttpResponse> {

    private HttpRequestDTO httpRequest;
    private HttpResponseDTO httpResponse;

    public HttpRequestAndHttpResponseDTO() {
    }

    public HttpRequestAndHttpResponseDTO(HttpRequestAndHttpResponse httpRequestAndHttpResponse) {
        if (httpRequestAndHttpResponse != null) {
            HttpRequest httpRequest = httpRequestAndHttpResponse.getHttpRequest();
            if (httpRequest != null) {
                this.httpRequest = new HttpRequestDTO(httpRequest);
            }
            HttpResponse httpResponse = httpRequestAndHttpResponse.getHttpResponse();
            if (httpResponse != null) {
                this.httpResponse = new HttpResponseDTO(httpResponse);
            }
        }
    }

    @Override
    public HttpRequestAndHttpResponse buildObject() {
        HttpRequest httpRequest = null;
        HttpResponse httpResponse = null;
        if (this.httpRequest != null) {
            httpRequest = this.httpRequest.buildObject();
        }
        if (this.httpResponse != null) {
            httpResponse = this.httpResponse.buildObject();
        }
        return new HttpRequestAndHttpResponse()
            .withHttpRequest(httpRequest)
            .withHttpResponse(httpResponse);
    }

    public HttpRequestDTO getHttpRequest() {
        return httpRequest;
    }

    public HttpRequestAndHttpResponseDTO setHttpRequest(HttpRequestDTO httpRequest) {
        this.httpRequest = httpRequest;
        return this;
    }

    public HttpResponseDTO getHttpResponse() {
        return httpResponse;
    }

    public HttpRequestAndHttpResponseDTO setHttpResponse(HttpResponseDTO httpResponse) {
        this.httpResponse = httpResponse;
        return this;
    }
}
