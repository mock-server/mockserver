package org.mockserver.matchers;

import io.netty.handler.codec.http.QueryStringDecoder;
import org.mockserver.model.HttpRequest;

/**
 * @author jamesdbloom
 */
public class MatcherBuilder {

    public HttpRequestMatcher transformsToMatcher(HttpRequest httpRequest) {
        if (httpRequest != null) {
            return new HttpRequestMatcher()
                    .withHttpRequest(httpRequest)
                    .withMethod(httpRequest.getMethod())
                    .withURL(httpRequest.getURL())
                    .withPath(httpRequest.getPath())
                    .withQueryStringParameters(httpRequest.getQueryStringParameters())
                    .withBody(httpRequest.getBody())
                    .withHeaders(httpRequest.getHeaders())
                    .withCookies(httpRequest.getCookies());
        } else {
            return new HttpRequestMatcher();
        }
    }

}
