package org.mockserver.codec;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.SET_COOKIE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class MockServerResponseEncoderCookieHeaderTest {

    private MockServerToNettyResponseEncoder mockServerResponseEncoder;
    private List<Object> output;
    private HttpResponse httpResponse;

    @Before
    public void setupFixture() {
        mockServerResponseEncoder = new MockServerToNettyResponseEncoder(new MockServerLogger());
        output = new ArrayList<Object>();
        httpResponse = response();
    }

    @Test
    public void shouldOnlyMapACookieIfThereIsNoSetCookieHeader() throws UnsupportedEncodingException {
        // given
        // - an HttpResponse
        String cookieOne = "cookieName1=\"\"; Expires=Thu, 01-Jan-1970 00:00:10 GMT; Path=/";
        String cookieTwo = "cookieName2=\"cookie==Value2\"; Version=1; Comment=\"Anonymous cookie for site\"; Max-Age=15552000; Expires=Sat, 19-Mar-2016 18:43:26 GMT; Path=/";
        httpResponse.withHeaders(
                new Header("Set-Cookie", cookieOne),
                new Header("set-cookie", cookieTwo)
        );
        httpResponse.withCookies(
                new Cookie("cookieName1", ""),
                new Cookie("cookieName2", "cookie==Value2"),
                new Cookie("cookieName3", "cookie==Value3")
        );

        // when
        mockServerResponseEncoder.encode(null, httpResponse, output);

        // then
        HttpHeaders headers = ((FullHttpResponse) output.get(0)).headers();
        assertThat(headers.names(), containsInAnyOrder("Set-Cookie", SET_COOKIE.toString(), CONTENT_LENGTH.toString(), SET_COOKIE.toString()));
        assertThat(headers.getAll("Set-Cookie"), containsInAnyOrder(
                cookieOne,
                cookieTwo,
                "cookieName3=cookie==Value3"
        ));
    }

}
