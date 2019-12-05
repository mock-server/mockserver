package org.mockserver.mappers;

import com.google.common.collect.ImmutableSet;
import io.netty.handler.codec.http.HttpConstants;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.util.CharsetUtil;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Set;

import static io.netty.handler.codec.http.HttpHeaderValues.CHARSET;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.slf4j.event.Level.WARN;

/**
 * @author jamesdbloom
 */
public class ContentTypeMapper {
    /**
     * The default character set for an HTTP message, if none is specified in the Content-Type header. From the HTTP 1.1 specification
     * section 3.7.1 (http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.7.1):
     * <pre>
     *     The "charset" parameter is used with some media types to define the character set (section 3.4) of the data.
     *     When no explicit charset parameter is provided by the sender, media subtypes of the "text" type are defined to
     *     have a default charset value of "ISO-8859-1" when received via HTTP. Data in character sets other than
     *     "ISO-8859-1" or its subsets MUST be labeled with an appropriate charset value.
     * </pre>
     */
    public static final Charset DEFAULT_HTTP_CHARACTER_SET = CharsetUtil.ISO_8859_1;

    private static final Set<String> UTF_8_CONTENT_TYPES = ImmutableSet.<String>builder()
        .add("application/atom+xml")
        .add("application/ecmascript")
        .add("application/javascript")
        .add("application/json")
        .add("application/jsonml+json")
        .add("application/lost+xml")
        .add("application/wsdl+xml")
        .add("application/xaml+xml")
        .add("application/xhtml+xml")
        .add("application/xml")
        .add("application/xml-dtd")
        .add("application/xop+xml")
        .add("application/xslt+xml")
        .add("application/xspf+xml")
        .add("application/x-www-form-urlencoded")
        .add("image/svg+xml")
        .add("text/css")
        .add("text/csv")
        .add("text/html")
        .add("text/plain")
        .add("text/richtext")
        .add("text/sgml")
        .add("text/tab-separated-values")
        .add("text/x-fortran")
        .add("text/x-java-source")
        .build();

    public static boolean isBinary(String contentTypeHeader) {
        boolean isBinary = false;
        if (isNotBlank(contentTypeHeader)) {
            String contentType = contentTypeHeader.toLowerCase();
            boolean utf8Body = contentType.contains("utf-8")
                || contentType.contains("utf8")
                || contentType.contains("text")
                || contentType.contains("javascript")
                || contentType.contains("json")
                || contentType.contains("ecmascript")
                || contentType.contains("css")
                || contentType.contains("csv")
                || contentType.contains("html")
                || contentType.contains("xhtml")
                || contentType.contains("form")
                || contentType.contains("urlencoded")
                || contentType.contains("xml");
            if (!utf8Body) {
                isBinary = contentType.contains("ogg")
                    || contentType.contains("audio")
                    || contentType.contains("video")
                    || contentType.contains("image")
                    || contentType.contains("pdf")
                    || contentType.contains("postscript")
                    || contentType.contains("font")
                    || contentType.contains("woff")
                    || contentType.contains("model")
                    || contentType.contains("zip")
                    || contentType.contains("gzip")
                    || contentType.contains("nacl")
                    || contentType.contains("pnacl")
                    || contentType.contains("vnd")
                    || contentType.contains("application");
            }
        }
        return isBinary;
    }

    public static boolean isJson(String contentTypeHeader) {
        boolean isJson = false;
        if (isNotBlank(contentTypeHeader)) {
            String contentType = contentTypeHeader.toLowerCase();
            return contentType.contains("json");
        }
        return isJson;
    }

    private final MockServerLogger mockServerLogger;

    public ContentTypeMapper(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
    }

    public Charset getCharsetFromContentTypeHeader(String contentType) {
        Charset charset = DEFAULT_HTTP_CHARACTER_SET;
        if (contentType != null) {
            try {
                charset = HttpUtil.getCharset(contentType.replaceAll("\"", ""));
            } catch (UnsupportedCharsetException uce) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.WARN)
                        .setLogLevel(WARN)
                        .setMessageFormat("Unsupported character set {} in Content-Type header: {}.")
                        .setArguments(StringUtils.substringAfterLast(contentType, CHARSET.toString() + HttpConstants.EQUALS), contentType)
                );
            } catch (IllegalCharsetNameException icne) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.WARN)
                        .setLogLevel(WARN)
                        .setMessageFormat("Illegal character set {} in Content-Type header: {}.")
                        .setArguments(StringUtils.substringAfterLast(contentType, CHARSET.toString() + HttpConstants.EQUALS), contentType)
                );
            }
        }
        return charset;
    }
}
