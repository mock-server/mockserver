package org.mockserver.serialization.model;

import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.LogEventRequestAndResponse;
import org.mockserver.model.ObjectWithJsonToString;

/**
 * @author jamesdbloom
 */
public class LogEventRequestAndResponseDTO extends ObjectWithJsonToString implements DTO<LogEventRequestAndResponse> {

    private String timestamp;
    private HttpRequestDTO httpRequest;
    private HttpResponseDTO httpResponse;

    public LogEventRequestAndResponseDTO() {
    }

    public LogEventRequestAndResponseDTO(LogEventRequestAndResponse httpRequestAndHttpResponse) {
        if (httpRequestAndHttpResponse != null) {
            HttpRequest httpRequest = httpRequestAndHttpResponse.getHttpRequest();
            if (httpRequest != null) {
                this.httpRequest = new HttpRequestDTO(httpRequest, httpRequest.getNot());
            }
            HttpResponse httpResponse = httpRequestAndHttpResponse.getHttpResponse();
            if (httpResponse != null) {
                this.httpResponse = new HttpResponseDTO(httpResponse);
            }
            timestamp = httpRequestAndHttpResponse.getTimestamp();
        }
    }

    @Override
    public LogEventRequestAndResponse buildObject() {
        HttpRequest httpRequest = null;
        HttpResponse httpResponse = null;
        if (this.httpRequest != null) {
            httpRequest = this.httpRequest.buildObject();
        }
        if (this.httpResponse != null) {
            httpResponse = this.httpResponse.buildObject();
        }
        return new LogEventRequestAndResponse()
            .withHttpRequest(httpRequest)
            .withHttpResponse(httpResponse)
            .withTimestamp(timestamp);
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public HttpRequestDTO getHttpRequest() {
        return httpRequest;
    }

    public void setHttpRequest(HttpRequestDTO httpRequest) {
        this.httpRequest = httpRequest;
    }

    public HttpResponseDTO getHttpResponse() {
        return httpResponse;
    }

    public void setHttpResponse(HttpResponseDTO httpResponse) {
        this.httpResponse = httpResponse;
    }
}
