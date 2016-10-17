package org.mockserver.codec;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.mappers.ContentTypeMapper;
import org.mockserver.mappers.MockServerResponseToHttpServletResponseEncoder;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.collection.IsEmptyIterable.emptyIterable;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class MockServerResponseEncoderCookieHeaderTest {

    private MockServerResponseEncoder mockServerResponseEncoder;
    private List<Object> output;
    private HttpResponse httpResponse;

    @Before
    public void setupFixture() {
        mockServerResponseEncoder = new MockServerResponseEncoder();
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
                new Header("Set-Cookie", cookieTwo)
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
        assertThat(headers.names(), containsInAnyOrder("Set-Cookie", "Content-Length"));
        assertThat(headers.getAll("Set-Cookie"), containsInAnyOrder(
                cookieOne,
                cookieTwo,
                "cookieName3=cookie==Value3"
        ));
    }

}
