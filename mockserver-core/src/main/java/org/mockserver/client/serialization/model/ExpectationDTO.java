package org.mockserver.client.serialization.model;

import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;

/**
 * @author jamesdbloom
 */
public class ExpectationDTO extends ObjectWithJsonToString implements DTO<Expectation> {

    private HttpRequestDTO httpRequest;
    private HttpResponseDTO httpResponse;
    private HttpTemplateDTO httpResponseTemplate;
    private HttpForwardDTO httpForward;
    private HttpTemplateDTO httpForwardTemplate;
    private HttpErrorDTO httpError;
    private HttpClassCallbackDTO httpClassCallback;
    private HttpObjectCallbackDTO httpObjectCallback;
    private TimesDTO times;
    private TimeToLiveDTO timeToLive;

    public ExpectationDTO(Expectation expectation) {
        if (expectation != null) {
            HttpRequest httpRequest = expectation.getHttpRequest();
            if (httpRequest != null) {
                this.httpRequest = new HttpRequestDTO(httpRequest, httpRequest.getNot());
            }
            HttpResponse httpResponse = expectation.getHttpResponse();
            if (httpResponse != null) {
                this.httpResponse = new HttpResponseDTO(httpResponse);
            }
            HttpTemplate httpResponseTemplate = expectation.getHttpResponseTemplate();
            if (httpResponseTemplate != null) {
                this.httpResponseTemplate = new HttpTemplateDTO(httpResponseTemplate);
            }
            HttpForward httpForward = expectation.getHttpForward();
            if (httpForward != null) {
                this.httpForward = new HttpForwardDTO(httpForward);
            }
            HttpTemplate httpForwardTemplate = expectation.getHttpForwardTemplate();
            if (httpForwardTemplate != null) {
                this.httpForwardTemplate = new HttpTemplateDTO(httpForwardTemplate);
            }
            HttpError httpError = expectation.getHttpError();
            if (httpError != null) {
                this.httpError = new HttpErrorDTO(httpError);
            }
            HttpClassCallback httpClassCallback = expectation.getHttpClassCallback();
            if (httpClassCallback != null) {
                this.httpClassCallback = new HttpClassCallbackDTO(httpClassCallback);
            }
            HttpObjectCallback httpObjectCallback = expectation.getHttpObjectCallback();
            if (httpObjectCallback != null) {
                this.httpObjectCallback = new HttpObjectCallbackDTO(httpObjectCallback);
            }
            Times times = expectation.getTimes();
            if (times != null) {
                this.times = new TimesDTO(times);
            }
            TimeToLive timeToLive = expectation.getTimeToLive();
            if (timeToLive != null) {
                this.timeToLive = new TimeToLiveDTO(timeToLive);
            }
        }
    }

    public ExpectationDTO() {
    }

    public Expectation buildObject() {
        HttpRequest httpRequest = null;
        HttpResponse httpResponse = null;
        HttpTemplate httpResponseTemplate = null;
        HttpForward httpForward = null;
        HttpTemplate httpForwardTemplate = null;
        HttpError httpError = null;
        HttpClassCallback httpClassCallback = null;
        HttpObjectCallback httpObjectCallback = null;
        Times times;
        TimeToLive timeToLive;
        if (this.httpRequest != null) {
            httpRequest = this.httpRequest.buildObject();
        }
        if (this.httpResponse != null) {
            httpResponse = this.httpResponse.buildObject();
        }
        if (this.httpResponseTemplate != null) {
            httpResponseTemplate = this.httpResponseTemplate.buildObject();
        }
        if (this.httpForward != null) {
            httpForward = this.httpForward.buildObject();
        }
        if (this.httpForwardTemplate != null) {
            httpForwardTemplate = this.httpForwardTemplate.buildObject();
        }
        if (this.httpError != null) {
            httpError = this.httpError.buildObject();
        }
        if (this.httpClassCallback != null) {
            httpClassCallback = this.httpClassCallback.buildObject();
        }
        if (this.httpObjectCallback != null) {
            httpObjectCallback = this.httpObjectCallback.buildObject();
        }
        if (this.times != null) {
            times = this.times.buildObject();
        } else {
            times = Times.once();
        }
        if (this.timeToLive != null) {
            timeToLive = this.timeToLive.buildObject();
        } else {
            timeToLive = TimeToLive.unlimited();
        }
        return new Expectation(httpRequest, times, timeToLive)
            .thenRespond(httpResponse)
            .thenRespond(httpResponseTemplate)
            .thenForward(httpForward)
            .thenForward(httpForwardTemplate)
            .thenError(httpError)
            .thenCallback(httpClassCallback)
            .thenCallback(httpObjectCallback);
    }

    public HttpRequestDTO getHttpRequest() {
        return httpRequest;
    }

    public ExpectationDTO setHttpRequest(HttpRequestDTO httpRequest) {
        this.httpRequest = httpRequest;
        return this;
    }

    public HttpResponseDTO getHttpResponse() {
        return httpResponse;
    }

    public ExpectationDTO setHttpResponse(HttpResponseDTO httpResponse) {
        this.httpResponse = httpResponse;
        return this;
    }

    public HttpTemplateDTO getHttpResponseTemplate() {
        return httpResponseTemplate;
    }

    public ExpectationDTO setHttpResponseTemplate(HttpTemplateDTO httpResponseTemplate) {
        this.httpResponseTemplate = httpResponseTemplate;
        return this;
    }

    public HttpForwardDTO getHttpForward() {
        return httpForward;
    }

    public ExpectationDTO setHttpForward(HttpForwardDTO httpForward) {
        this.httpForward = httpForward;
        return this;
    }

    public HttpTemplateDTO getHttpForwardTemplate() {
        return httpForwardTemplate;
    }

    public ExpectationDTO setHttpForwardTemplate(HttpTemplateDTO httpForwardTemplate) {
        this.httpForwardTemplate = httpForwardTemplate;
        return this;
    }

    public HttpErrorDTO getHttpError() {
        return httpError;
    }

    public ExpectationDTO setHttpError(HttpErrorDTO httpError) {
        this.httpError = httpError;
        return this;
    }

    public HttpClassCallbackDTO getHttpClassCallback() {
        return httpClassCallback;
    }

    public ExpectationDTO setHttpClassCallback(HttpClassCallbackDTO httpClassCallback) {
        this.httpClassCallback = httpClassCallback;
        return this;
    }

    public HttpObjectCallbackDTO getHttpObjectCallback() {
        return httpObjectCallback;
    }

    public ExpectationDTO setHttpObjectCallback(HttpObjectCallbackDTO httpObjectCallback) {
        this.httpObjectCallback = httpObjectCallback;
        return this;
    }

    public TimesDTO getTimes() {
        return times;
    }

    public ExpectationDTO setTimes(TimesDTO times) {
        this.times = times;
        return this;
    }

    public TimeToLiveDTO getTimeToLive() {
        return timeToLive;
    }

    public ExpectationDTO setTimeToLive(TimeToLiveDTO timeToLive) {
        this.timeToLive = timeToLive;
        return this;
    }
}
