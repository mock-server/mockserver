package org.jamesdbloom.mockserver.client.serialization.model;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;
import org.jamesdbloom.mockserver.matchers.HttpRequestMatcher;
import org.jamesdbloom.mockserver.matchers.Times;
import org.jamesdbloom.mockserver.mock.Expectation;
import org.jamesdbloom.mockserver.model.HttpRequest;
import org.jamesdbloom.mockserver.model.HttpResponse;
import org.jamesdbloom.mockserver.model.ModelObject;

/**
 * @author jamesdbloom
 */
public class ExpectationDTO extends ModelObject {

    private HttpRequestDTO httpRequest;

    private HttpResponseDTO httpResponse;

    private TimesDTO times;

    public ExpectationDTO(Expectation expectation) {
        httpRequest = new HttpRequestDTO(expectation.getHttpRequest());
        httpResponse = new HttpResponseDTO(expectation.getHttpResponse());
        times = new TimesDTO(expectation.getTimes());
    }

    public HttpRequestDTO getHttpRequest() {
        return httpRequest;
    }

    public void setHttpRequest(HttpRequestDTO httpRequest) {
        this.httpRequest = httpRequest;
    }

    public TimesDTO getTimes() {
        return times;
    }

    public void setTimes(TimesDTO times) {
        this.times = times;
    }

    public HttpResponseDTO getHttpResponse() {
        return httpResponse;
    }

    public void setHttpResponse(HttpResponseDTO httpResponse) {
        this.httpResponse = httpResponse;
    }
}
