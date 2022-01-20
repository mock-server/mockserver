package org.mockserver.mappers;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.springframework.mock.web.MockHttpServletResponse;

import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;

/**
 * @author jamesdbloom
 */
public class MockServerHttpResponseToHttpServletResponseEncoderCookieHeaderTest {

    @Test
    public void shouldOnlyMapACookieIfThereIsNoSetCookieHeader() {
        // given
        // - an HttpResponse
        HttpResponse httpResponse = new HttpResponse();
        String cookieOne = "cookieName1=\"\"; Max-Age=15552000; Expires=Sat, 19 Mar 2016 18:43:26 GMT; Path=/";
        String cookieTwo = "cookieName2=\"cookie==Value2\"; Version=1; Comment=\"Anonymous cookie for site\"; Max-Age=15552000; Expires=Sat, 19 Mar 2016 18:43:26 GMT; Path=/";
        httpResponse.withHeaders(
            new Header("Set-Cookie", cookieOne),
            new Header("Set-Cookie", cookieTwo)
        );
        httpResponse.withCookies(
            new Cookie("cookieName1", ""),
            new Cookie("cookieName2", "cookie==Value2"),
            new Cookie("cookieName3", "cookie==Value3")
        );
        // - an HttpServletResponse
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

        // when
        new MockServerHttpResponseToHttpServletResponseEncoder(new MockServerLogger()).mapMockServerResponseToHttpServletResponse(httpResponse, httpServletResponse);

        // then
        assertEquals(HttpStatusCode.OK_200.code(), httpServletResponse.getStatus());
        assertThat(httpServletResponse.getHeaders("Set-Cookie"), containsInAnyOrder(
            "cookieName1=\"\"; Path=/; Max-Age=15552000; Expires=Sat, 19 Mar 2016 18:43:26 GMT",
            "cookieName2=\"cookie==Value2\"; Path=/; Max-Age=15552000; Expires=Sat, 19 Mar 2016 18:43:26 GMT",
            "cookieName3=cookie==Value3"
        ));
        assertThat(httpServletResponse.getHeaderNames(), contains("Set-Cookie"));
    }
}
