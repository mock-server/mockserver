package org.mockserver.mappers;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.*;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.JsonSchemaBody.jsonSchema;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.ParameterBody.params;
import static org.mockserver.model.RegexBody.regex;
import static org.mockserver.model.StringBody.exact;
import static org.mockserver.model.XmlBody.xml;

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
        new MockServerResponseToHttpServletResponseEncoder(new MockServerLogger()).mapMockServerResponseToHttpServletResponse(httpResponse, httpServletResponse);

        // then
        assertNull(httpServletResponse.getHeader("Content-Type"));
    }

    @Test
    public void shouldReturnContentTypeForStringBodyWithCharset() {
        // given
        HttpResponse httpResponse = response().withBody(exact("somebody", StandardCharsets.US_ASCII));
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

        // when
        new MockServerResponseToHttpServletResponseEncoder(new MockServerLogger()).mapMockServerResponseToHttpServletResponse(httpResponse, httpServletResponse);

        // then
        assertEquals("text/plain; charset=us-ascii", httpServletResponse.getHeader("Content-Type"));
    }

    @Test
    public void shouldReturnContentTypeForStringBodyWithMediaType() {
        // given
        HttpResponse httpResponse = response().withBody(exact("somebody", MediaType.ATOM_UTF_8));
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

        // when
        new MockServerResponseToHttpServletResponseEncoder(new MockServerLogger()).mapMockServerResponseToHttpServletResponse(httpResponse, httpServletResponse);

        // then
        assertEquals("application/atom+xml; charset=utf-8", httpServletResponse.getHeader("Content-Type"));
    }

    @Test
    public void shouldReturnContentTypeForStringBodyWithoutMediaTypeOrCharset() {
        // given
        HttpResponse httpResponse = response().withBody("somebody");
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

        // when
        new MockServerResponseToHttpServletResponseEncoder(new MockServerLogger()).mapMockServerResponseToHttpServletResponse(httpResponse, httpServletResponse);

        // then
        assertThat(httpServletResponse.getHeader("Content-Type"), nullValue());
    }

    @Test
    public void shouldReturnContentTypeForJsonBody() {
        // given
        HttpResponse httpResponse = response().withBody(json("somebody"));
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

        // when
        new MockServerResponseToHttpServletResponseEncoder(new MockServerLogger()).mapMockServerResponseToHttpServletResponse(httpResponse, httpServletResponse);

        // then
        assertThat(httpServletResponse.getHeader("Content-Type"), is("application/json"));
    }

    @Test
    public void shouldReturnContentTypeForParameterBody() {
        // given
        HttpResponse httpResponse = response().withBody(params(param("key", "value")));
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

        // when
        new MockServerResponseToHttpServletResponseEncoder(new MockServerLogger()).mapMockServerResponseToHttpServletResponse(httpResponse, httpServletResponse);

        // then
        assertEquals("application/x-www-form-urlencoded", httpServletResponse.getHeader("Content-Type"));
    }

    @Test
    public void shouldReturnNoContentTypeForBodyWithNoAssociatedContentType() {
        // given
        HttpResponse httpResponse = response().withBody(xml("some_value", (MediaType) null));
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

        // when
        new MockServerResponseToHttpServletResponseEncoder(new MockServerLogger()).mapMockServerResponseToHttpServletResponse(httpResponse, httpServletResponse);

        // then
        assertNull(httpServletResponse.getHeader("Content-Type"));
    }

    @Test
    public void shouldNotSetDefaultContentTypeWhenContentTypeExplicitlySpecified() {
        // given
        HttpResponse httpResponse = response()
                .withBody(json("somebody"))
                .withHeaders(new Header("Content-Type", "some/value"));
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();

        // when
        new MockServerResponseToHttpServletResponseEncoder(new MockServerLogger()).mapMockServerResponseToHttpServletResponse(httpResponse, httpServletResponse);

        // then
        assertEquals("some/value", httpServletResponse.getHeader("Content-Type"));

    }
}
