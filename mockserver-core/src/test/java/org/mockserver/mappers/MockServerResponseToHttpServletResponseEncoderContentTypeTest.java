package org.mockserver.mappers;

import org.junit.Test;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.JsonSchemaBody.jsonSchema;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.ParameterBody.params;
import static org.mockserver.model.RegexBody.regex;

/**
 * @author jamesdbloom
 */
public class MockServerResponseToHttpServletResponseEncoderContentTypeTest {

    @Test
    public void shouldReturnNoDefaultContentTypeWhenNoBodySpecified() {
        // given
        HttpResponse httpResponse = response();
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

        // when
        new MockServerResponseToHttpServletResponseEncoder().mapMockServerResponseToHttpServletResponse(httpResponse, httpServletResponse);

        // then
        assertEquals(null, httpServletResponse.getHeader("Content-Type"));
    }

    @Test
    public void shouldReturnContentTypeForStringBody() {
        // given
        HttpResponse httpResponse = response().withBody("somebody");
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

        // when
        new MockServerResponseToHttpServletResponseEncoder().mapMockServerResponseToHttpServletResponse(httpResponse, httpServletResponse);

        // then
        assertEquals("text/plain", httpServletResponse.getHeader("Content-Type"));
    }

    @Test
    public void shouldReturnContentTypeForJsonBody() {
        // given
        HttpResponse httpResponse = response().withBody(json("somebody"));
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

        // when
        new MockServerResponseToHttpServletResponseEncoder().mapMockServerResponseToHttpServletResponse(httpResponse, httpServletResponse);

        // then
        assertEquals("application/json", httpServletResponse.getHeader("Content-Type"));
    }

    @Test
    public void shouldReturnContentTypeForJsonSchemaBody() {
        // given
        HttpResponse httpResponse = response().withBody(jsonSchema("somebody"));
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

        // when
        new MockServerResponseToHttpServletResponseEncoder().mapMockServerResponseToHttpServletResponse(httpResponse, httpServletResponse);

        // then
        assertEquals("application/json", httpServletResponse.getHeader("Content-Type"));
    }

    @Test
    public void shouldReturnContentTypeForParameterBody() {
        // given
        HttpResponse httpResponse = response().withBody(params(param("key", "value")));
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

        // when
        new MockServerResponseToHttpServletResponseEncoder().mapMockServerResponseToHttpServletResponse(httpResponse, httpServletResponse);

        // then
        assertEquals("application/x-www-form-urlencoded", httpServletResponse.getHeader("Content-Type"));
    }

    @Test
    public void shouldReturnNoContentTypeForBodyWithNoAssociatedContentType() {
        // given
        HttpResponse httpResponse = response().withBody(regex("some_value"));
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

        // when
        new MockServerResponseToHttpServletResponseEncoder().mapMockServerResponseToHttpServletResponse(httpResponse, httpServletResponse);

        // then
        assertEquals(null, httpServletResponse.getHeader("Content-Type"));
    }

    @Test
    public void shouldNotSetDefaultContentTypeWhenContentTypeExplicitlySpecified() {
        // given
        HttpResponse httpResponse = response()
                .withBody(json("somebody"))
                .withHeaders(new Header("Content-Type", "some/value"));
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

        // when
        new MockServerResponseToHttpServletResponseEncoder().mapMockServerResponseToHttpServletResponse(httpResponse, httpServletResponse);

        // then
        assertEquals("some/value", httpServletResponse.getHeader("Content-Type"));

    }
}
