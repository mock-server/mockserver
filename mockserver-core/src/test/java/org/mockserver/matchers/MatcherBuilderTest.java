package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;

import static org.junit.Assert.assertTrue;

/**
 * @author jamesdbloom
 */
public class MatcherBuilderTest {

    @Test
    public void transformsHttpRequestToHttpRequestMatcher() {
        // given
        HttpRequest httpRequest = new HttpRequest()
                .withPath("somepath")
                .withBody("somebody")
                .withHeaders(new Header("name", "value"))
                .withCookies(new Cookie("name", "value"));

        // when
        HttpRequestMatcher httpRequestMapper = MatcherBuilder.transformsToMatcher(httpRequest);

        // then
        assertTrue(httpRequestMapper.matches(httpRequest));
    }
}
