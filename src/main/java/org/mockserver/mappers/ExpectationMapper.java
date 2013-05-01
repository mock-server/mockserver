package org.mockserver.mappers;

import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.model.HttpRequest;

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
                .withParameters(httpRequest.getParameters());
    }

}
