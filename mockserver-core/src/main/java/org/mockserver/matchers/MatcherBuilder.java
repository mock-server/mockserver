package org.mockserver.matchers;

import io.netty.handler.codec.http.QueryStringDecoder;
import org.mockserver.model.HttpRequest;

/**
 * @author jamesdbloom
 */
public class MatcherBuilder {

    public HttpRequestMatcher transformsToMatcher(HttpRequest httpRequest) {
        if (httpRequest != null) {
            return new HttpRequestMatcher(httpRequest);
        } else {
            return new HttpRequestMatcher(null);
        }
    }

}
