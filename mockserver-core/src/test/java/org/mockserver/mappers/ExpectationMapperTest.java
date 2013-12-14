package org.mockserver.mappers;

import org.junit.Test;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.Parameter;

import static org.junit.Assert.assertTrue;

/**
 * @author jamesdbloom
 */
public class ExpectationMapperTest {

    @Test
    public void transformsHttpRequestToHttpRequestMatcher() {
        // given
        HttpRequest httpRequest = new HttpRequest()
                .withPath("somepath")
                .withBody("somebody")
                .withHeaders(new Header("name", "value"))
                .withCookies(new Cookie("name", "value"))
                .withParameters(new Parameter("parameterName", "parameterValue"));

        // when
        HttpRequestMatcher httpRequestMapper = new ExpectationMapper().transformsToMatcher(httpRequest);

        // then
        assertTrue(httpRequestMapper.matches(httpRequest));
    }
}
