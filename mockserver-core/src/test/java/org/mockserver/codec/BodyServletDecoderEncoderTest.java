package org.mockserver.codec;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Body;
import org.mockserver.model.BodyWithContentType;
import org.mockserver.model.MediaType;
import org.mockserver.model.StringBody;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.MediaType.DEFAULT_HTTP_CHARACTER_SET;
import static org.mockserver.model.StringBody.exact;

@SuppressWarnings("rawtypes")
public class BodyServletDecoderEncoderTest {

    private final MockServerLogger mockServerLogger = new MockServerLogger();

    @Test
    public void shouldSerialiseBodyToServletResponseWithNoContentType() throws IOException {
        // given
        Body body = new StringBody("bytes");
        HttpServletResponse servletResponse = mock(HttpServletResponse.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(servletResponse.getOutputStream()).thenReturn(
            new DelegatingServletOutputStream(outputStream)
        );

        // when
        new BodyServletDecoderEncoder(mockServerLogger).bodyToServletResponse(servletResponse, body, null);

        // then
        assertThat(outputStream.toByteArray(), is("bytes".getBytes(DEFAULT_HTTP_CHARACTER_SET)));
    }

    @Test
    public void shouldSerialiseBodyToServletResponseWithJsonContentType() throws IOException {
        // given
        Body body = new StringBody("şarəs");
        HttpServletResponse servletResponse = mock(HttpServletResponse.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(servletResponse.getOutputStream()).thenReturn(
            new DelegatingServletOutputStream(outputStream)
        );

        // when
        new BodyServletDecoderEncoder(mockServerLogger).bodyToServletResponse(servletResponse, body, MediaType.APPLICATION_JSON_UTF_8.toString());

        // then
        assertThat(outputStream.toByteArray(), is("şarəs".getBytes(UTF_8)));
    }

    @Test
    public void shouldReadServletRequestToStringBodyWithNoContentType() throws IOException {
        // given
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getInputStream()).thenReturn(
            new DelegatingServletInputStream(IOUtils.toInputStream("bytes", DEFAULT_HTTP_CHARACTER_SET))
        );

        // when
        BodyWithContentType result = new BodyServletDecoderEncoder(mockServerLogger).servletRequestToBody(servletRequest);

        // then
        assertThat(result, is(exact("bytes")));
    }

    @Test
    public void shouldReadServletRequestToStringBodyWithStringContentType() throws IOException {
        // given
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getHeader(CONTENT_TYPE.toString())).thenReturn(MediaType.TEXT_PLAIN.toString());
        when(servletRequest.getInputStream()).thenReturn(
            new DelegatingServletInputStream(IOUtils.toInputStream("bytes", DEFAULT_HTTP_CHARACTER_SET))
        );

        // when
        BodyWithContentType result = new BodyServletDecoderEncoder(mockServerLogger).servletRequestToBody(servletRequest);

        // then
        assertThat(result, is(exact("bytes", MediaType.TEXT_PLAIN)));
    }

    @Test
    public void shouldReadServletRequestToStringBodyWithStringContentTypeAndCharset() throws IOException {
        // given
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getHeader(CONTENT_TYPE.toString())).thenReturn(MediaType.TEXT_HTML_UTF_8.toString());
        when(servletRequest.getInputStream()).thenReturn(
            new DelegatingServletInputStream(IOUtils.toInputStream("bytes", UTF_8))
        );

        // when
        BodyWithContentType result = new BodyServletDecoderEncoder(mockServerLogger).servletRequestToBody(servletRequest);

        // then
        assertThat(result, is(exact("bytes", MediaType.TEXT_HTML_UTF_8)));
    }

    @Test
    public void shouldReadServletRequestToJsonBodyWithJsonContentType() throws IOException {
        // given
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getHeader(CONTENT_TYPE.toString())).thenReturn(MediaType.APPLICATION_JSON_UTF_8.toString());
        when(servletRequest.getInputStream()).thenReturn(
            new DelegatingServletInputStream(IOUtils.toInputStream("şarəs", UTF_8))
        );

        // when
        BodyWithContentType result = new BodyServletDecoderEncoder(mockServerLogger).servletRequestToBody(servletRequest);

        // then
        assertThat(result, is(json("şarəs", MediaType.APPLICATION_JSON_UTF_8)));
    }

    @Test
    public void shouldReadServletRequestToJsonBodyWithJsonContentTypeAndCharset() throws IOException {
        // given
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getHeader(CONTENT_TYPE.toString())).thenReturn(MediaType.APPLICATION_JSON.toString());
        when(servletRequest.getInputStream()).thenReturn(
            new DelegatingServletInputStream(IOUtils.toInputStream("şarəs", DEFAULT_HTTP_CHARACTER_SET))
        );

        // when
        BodyWithContentType result = new BodyServletDecoderEncoder(mockServerLogger).servletRequestToBody(servletRequest);

        // then
        assertThat(result, is(json("?ar?s", MediaType.APPLICATION_JSON)));
    }

    @Test
    public void shouldReadServletRequestToBinaryBodyWithBinaryContentType() throws IOException {
        // given
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getHeader(CONTENT_TYPE.toString())).thenReturn(MediaType.ANY_VIDEO_TYPE.toString());
        when(servletRequest.getInputStream()).thenReturn(
            new DelegatingServletInputStream(IOUtils.toInputStream("bytes", DEFAULT_HTTP_CHARACTER_SET))
        );

        // when
        BodyWithContentType result = new BodyServletDecoderEncoder(mockServerLogger).servletRequestToBody(servletRequest);

        // then
        assertThat(result, is(binary("bytes".getBytes(DEFAULT_HTTP_CHARACTER_SET), MediaType.ANY_VIDEO_TYPE)));
    }

    @Test
    public void shouldReadServletRequestToBinaryBodyWithBinaryContentTypeAndCharset() throws IOException {
        // given
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getHeader(CONTENT_TYPE.toString())).thenReturn(MediaType.ANY_VIDEO_TYPE.withCharset(UTF_8).toString());
        when(servletRequest.getInputStream()).thenReturn(
            new DelegatingServletInputStream(IOUtils.toInputStream("bytes", UTF_8))
        );

        // when
        BodyWithContentType result = new BodyServletDecoderEncoder(mockServerLogger).servletRequestToBody(servletRequest);

        // then
        assertThat(result, is(exact("bytes", MediaType.ANY_VIDEO_TYPE.withCharset(UTF_8))));
    }

    @Test(expected = RuntimeException.class)
    public void shouldHandleExceptionWhenReadInputStreamToByteArray() throws IOException {
        // given
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getHeader(CONTENT_TYPE.toString())).thenReturn("");
        when(servletRequest.getInputStream()).thenThrow(new IOException("TEST EXCEPTION"));

        // when
        new BodyServletDecoderEncoder(mockServerLogger).servletRequestToBody(servletRequest);
    }

    static class DelegatingServletInputStream extends ServletInputStream {

        private final InputStream inputStream;

        DelegatingServletInputStream(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        public int read() throws IOException {
            return this.inputStream.read();
        }

        public void close() throws IOException {
            super.close();
            this.inputStream.close();
        }

        @Override
        public boolean isFinished() {
            return false;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {

        }
    }

    static class DelegatingServletOutputStream extends ServletOutputStream {

        private final OutputStream outputStream;

        DelegatingServletOutputStream(OutputStream inputStream) {
            this.outputStream = inputStream;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {

        }

        @Override
        public void write(int b) throws IOException {
            this.outputStream.write(b);
        }
    }
}