package org.mockserver.mappers;

import io.netty.handler.codec.http.HttpUtil;
import io.netty.util.CharsetUtil;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.MediaType;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class ContentTypeMapperTest {

    private List<String> utf8ContentTypes = Arrays.asList(
        "application/atom+xml",
        "application/ecmascript",
        "application/javascript",
        "application/json",
        "application/jsonml+json",
        "application/lost+xml",
        "application/wsdl+xml",
        "application/xaml+xml",
        "application/xhtml+xml",
        "application/xml",
        "application/xml-dtd",
        "application/xop+xml",
        "application/xslt+xml",
        "application/xspf+xml",
        "application/x-www-form-urlencoded",
        "image/svg+xml",
        "text/css",
        "text/csv",
        "text/html",
        "text/plain",
        "text/richtext",
        "text/sgml",
        "text/tab-separated-values",
        "text/x-fortran",
        "text/x-java-source"
    );

    private List<String> binaryContentTypes = Arrays.asList(
        "application/applixware",
        "application/font-tdpfr",
        "application/java-archive",
        "application/java-serialized-object",
        "application/java-vm",
        "application/mp4",
        "application/octet-stream",
        "application/pdf",
        "application/pkcs10",
        "application/pkix-cert",
        "application/x-font-bdf",
        "application/x-font-ghostscript",
        "application/x-font-linux-psf",
        "application/x-font-otf",
        "application/x-font-pcf",
        "application/x-font-snf",
        "application/x-font-ttf",
        "application/x-font-type1",
        "application/font-woff",
        "application/x-java-jnlp-file",
        "application/x-latex",
        "application/x-shockwave-flash",
        "application/x-silverlight-app",
        "application/x-stuffit",
        "application/x-tar",
        "application/x-tex",
        "application/x-tex-tfm",
        "application/x-x509-ca-cert",
        "application/zip",
        "audio/midi",
        "audio/mp4",
        "audio/mpeg",
        "audio/ogg",
        "audio/x-aiff",
        "audio/x-wav",
        "audio/xm",
        "image/bmp",
        "image/gif",
        "image/jpeg",
        "image/png",
        "image/sgi",
        "image/tiff",
        "image/x-xbitmap",
        "video/jpeg",
        "video/mp4",
        "video/mpeg",
        "video/ogg",
        "video/quicktime",
        "video/x-msvideo",
        "video/x-sgi-movie"
    );

    private final MockServerLogger mockServerLogger = new MockServerLogger();

    @Test
    public void shouldNotDetectAsBinaryBody() {
        for (String contentType : utf8ContentTypes) {
            assertThat(contentType + " should not be binary", ContentTypeMapper.isBinary(contentType), is(false));
        }
    }

    @Test
    public void shouldDetectAsBinaryBody() {
        for (String contentType : binaryContentTypes) {
            assertThat(contentType + " should be binary", ContentTypeMapper.isBinary(contentType), is(true));
        }
    }

    @Test
    public void shouldDefaultToNotBinary() {
        assertThat("null should not be binary", ContentTypeMapper.isBinary(null), is(false));
    }

    @Test
    public void shouldDetermineCharsetFromResponseContentType() {
        // when
        Charset charset = new ContentTypeMapper(mockServerLogger).getCharsetFromContentTypeHeader(MediaType.create("text", "plain").withCharset(StandardCharsets.UTF_16).toString());

        // then
        assertThat(charset, is(StandardCharsets.UTF_16));
    }

    @Test
    public void shouldDetermineUTFCharsetWhenFileTypeIsUtf() {
        Charset charset = new ContentTypeMapper(mockServerLogger).getCharsetFromContentTypeHeader("application/json");

        assertThat(charset, is(ContentTypeMapper.DEFAULT_HTTP_CHARACTER_SET));
    }

    @Test
    public void shouldDetermineCharsetWhenIllegalContentTypeHeader() {
        // when
        Charset charset = new ContentTypeMapper(mockServerLogger).getCharsetFromContentTypeHeader("some_rubbish");

        // then
        assertThat(charset, is(ContentTypeMapper.DEFAULT_HTTP_CHARACTER_SET));
    }

    @Test
    public void shouldDetermineCharsetWithQuotes() {
        // when
        Charset charset = new ContentTypeMapper(mockServerLogger).getCharsetFromContentTypeHeader("text/html; charset=\"utf-8\"");

        // then
        assertThat(charset, is(StandardCharsets.UTF_8));
    }

    @Test
    public void shouldDetermineCharsetWhenUnsupportedCharset() {
        // when
        Charset charset = new ContentTypeMapper(mockServerLogger).getCharsetFromContentTypeHeader("text/plain; charset=some_rubbish");

        // then
        assertThat(charset, is(ContentTypeMapper.DEFAULT_HTTP_CHARACTER_SET));
    }

    @Test
    public void shouldDetermineCharsetWhenNoContentTypeHeader() {
        // when
        Charset charset = new ContentTypeMapper(mockServerLogger).getCharsetFromContentTypeHeader(null);

        // then
        assertThat(charset, is(ContentTypeMapper.DEFAULT_HTTP_CHARACTER_SET));
    }

    @Test
    public void shouldParseRFC7231ContentTypeExamples() {
        // when
        String[] rfcContentTypeExamples = new String[]{
            "text/html;charset=utf-8",
            "text/html;charset=UTF-8",
            "Text/HTML;Charset=\"utf-8\"",
            "text/html; charset=\"utf-8\""
        };

        for (String rfcContentTypeExample : rfcContentTypeExamples) {
            // then
            assertThat(rfcContentTypeExample, HttpUtil.getCharset(rfcContentTypeExample.replaceAll("\"", "")), is(StandardCharsets.UTF_8));
            assertThat(rfcContentTypeExample, new ContentTypeMapper(mockServerLogger).getCharsetFromContentTypeHeader(rfcContentTypeExample), is(StandardCharsets.UTF_8));
        }
    }

}
