package org.mockserver.matchers;

import org.mockserver.model.HttpRequest;

/**
 * @author jamesdbloom
 */
public class MatcherBuilder {

    public HttpRequestMatcher transformsToMatcher(HttpRequest httpRequest) {
        if (httpRequest != null) {
            return new HttpRequestMatcher()
                    .withMethod(httpRequest.getMethod())
                    .withURL(httpRequest.getURL())
                    .withPath(httpRequest.getPath())
                    .withQueryString(httpRequest.getQueryString())
                    .withParameters(httpRequest.getParameters())
                    .withBody(httpRequest.getBody())
                    .withHeaders(httpRequest.getHeaders())
                    .withCookies(httpRequest.getCookies());
        } else {
            return new HttpRequestMatcher();
        }
    }

}
