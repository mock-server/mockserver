package org.jamesdbloom.mockserver.mappers;

import org.jamesdbloom.mockserver.matchers.HttpRequestMatcher;
import org.jamesdbloom.mockserver.model.HttpRequest;

/**
 * @author jamesdbloom
 */
public class ExpectationMapper {

    public HttpRequestMatcher transformsToMatcher(HttpRequest httpRequest) {
        return new HttpRequestMatcher()
                .withPath(httpRequest.getPath())
                .withBody(httpRequest.getBody())
                .withHeaders(httpRequest.getHeaders())
                .withCookies(httpRequest.getCookies())
                .withQueryParameters(httpRequest.getQueryParameters())
                .withBodyParameters(httpRequest.getBodyParameters());
    }

}
