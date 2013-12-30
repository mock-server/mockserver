package org.mockserver.matchers;

import org.mockserver.model.HttpRequest;

/**
 * @author jamesdbloom
 */
public class MatcherBuilder {

    public static HttpRequestMatcher transformsToMatcher(HttpRequest httpRequest) {
        return new HttpRequestMatcher()
                .withMethod(httpRequest.getMethod())
                .withURL(httpRequest.getURL())
                .withPath(httpRequest.getPath())
                .withQueryString(httpRequest.getQueryString())
                .withBody(httpRequest.getBody())
                .withHeaders(httpRequest.getHeaders())
                .withCookies(httpRequest.getCookies())
                .withXpathBody(httpRequest.getBodyXpath());
    }

}
