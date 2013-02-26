package org.jamesdbloom.mockserver.client;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;
import org.jamesdbloom.mockserver.matchers.HttpRequestMatcher;
import org.jamesdbloom.mockserver.matchers.Times;
import org.jamesdbloom.mockserver.model.HttpRequest;
import org.jamesdbloom.mockserver.model.HttpResponse;
import org.jamesdbloom.mockserver.model.ModelObject;

/**
 * @author jamesdbloom
 */
public class ExpectationDTO extends ModelObject {

    private final HttpRequest httpRequest;

    private final Times times;
    private HttpResponse httpResponse = new HttpResponse();

    @JsonCreator
    public ExpectationDTO(@JsonProperty("httpRequest") HttpRequest httpRequest, @JsonProperty("times") Times times) {
        this.httpRequest = httpRequest;
        this.times = times;
    }

    public void respond(HttpResponse httpResponse) {
        this.httpResponse = httpResponse;
    }

    public HttpRequest getHttpRequest() {
        return httpRequest;
    }

    public HttpResponse getHttpResponse() {
        return httpResponse;
    }

    public Times getTimes() {
        return times;
    }
}
