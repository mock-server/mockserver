package org.jamesdbloom.mockserver.mappers;

import org.jamesdbloom.mockserver.matchers.HttpRequestMatcher;
import org.jamesdbloom.mockserver.model.Cookie;
import org.jamesdbloom.mockserver.model.Header;
import org.jamesdbloom.mockserver.model.HttpRequest;
import org.jamesdbloom.mockserver.model.Parameter;
import org.junit.Test;

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
