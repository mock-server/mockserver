package org.jamesdbloom.mockserver.client.serialization.model;

import org.jamesdbloom.mockserver.mock.Expectation;
import org.jamesdbloom.mockserver.model.ModelObject;

/**
 * @author jamesdbloom
 */
public class ExpectationDTO extends ModelObject {

    private HttpRequestDTO httpRequest;

    private HttpResponseDTO httpResponse;

    private TimesDTO times;

    public ExpectationDTO(Expectation expectation) {
        if (expectation.getHttpRequest() != null) {
            httpRequest = new HttpRequestDTO(expectation.getHttpRequest());
        }
        if (expectation.getHttpResponse() != null) {
            httpResponse = new HttpResponseDTO(expectation.getHttpResponse());
        }
        if (expectation.getTimes() != null) {
            times = new TimesDTO(expectation.getTimes());
        }
    }

    public ExpectationDTO() {
    }

    public HttpRequestDTO getHttpRequest() {
        return httpRequest;
    }

    public ExpectationDTO setHttpRequest(HttpRequestDTO httpRequest) {
        this.httpRequest = httpRequest;
        return this;
    }

    public TimesDTO getTimes() {
        return times;
    }

    public ExpectationDTO setTimes(TimesDTO times) {
        this.times = times;
        return this;
    }

    public HttpResponseDTO getHttpResponse() {
        return httpResponse;
    }

    public ExpectationDTO setHttpResponse(HttpResponseDTO httpResponse) {
        this.httpResponse = httpResponse;
        return this;
    }
}
