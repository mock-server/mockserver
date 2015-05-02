package org.mockserver.mappers;

import com.google.common.base.Strings;
import io.netty.handler.codec.http.HttpMessage;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class ContentTypeMapper {
    private static final Logger logger = LoggerFactory.getLogger(ContentTypeMapper.class);

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
    public static final Charset DEFAULT_HTTP_CHARACTER_SET = Charset.forName("ISO-8859-1");

    public static boolean isBinary(String contentTypeHeader) {
        boolean binary = false;
        if (!Strings.isNullOrEmpty(contentTypeHeader)) {
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
                    || contentType.contains("xml");
            if (!utf8Body) {
                binary = contentType.contains("ogg")
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
        return binary;
    }

    /**
     * Identifies the character set from the Content-Type header in the HttpMessage. If no Content-Type header or character set
     * exists, or the character set is not supported, returns the {@link ContentTypeMapper#DEFAULT_HTTP_CHARACTER_SET}.
     *
     * @param httpMessage HTTP message
     * @return character set from the message headers, or {@link ContentTypeMapper#DEFAULT_HTTP_CHARACTER_SET} if no charset is specified
     */
    public static Charset identifyCharsetFromHttpMessage(HttpMessage httpMessage) {
        Charset charset = null;

        String contentTypeHeader = httpMessage.headers().get("Content-Type");
        if (contentTypeHeader != null) {
            charset = getCharsetFromContentTypeHeader(contentTypeHeader);
        }

        if (charset == null) {
            charset = DEFAULT_HTTP_CHARACTER_SET;
            logger.debug("No character set specified in Content-Type header. Using default charset {}.", charset);
        }

        return charset;
    }

    /**
     * Identifies the character set from the Content-Type header in the HttpServletRequest. If no Content-Type header or character set
     * exists, or the character set is not supported, returns the {@link ContentTypeMapper#DEFAULT_HTTP_CHARACTER_SET}.
     *
     * @param servletRequest HTTP request
     * @return character set from the servlet request headers, or {@link ContentTypeMapper#DEFAULT_HTTP_CHARACTER_SET} if no charset is specified
     */
    public static Charset identifyCharsetFromServletRequest(HttpServletRequest servletRequest) {
        Charset charset = null;

        String contentTypeHeader = servletRequest.getHeader("Content-Type");
        if (contentTypeHeader != null) {
            charset = getCharsetFromContentTypeHeader(contentTypeHeader);
        }

        if (charset == null) {
            charset = DEFAULT_HTTP_CHARACTER_SET;
            logger.debug("No character set specified in Content-Type header. Using default charset {}.", charset);
        }

        return charset;
    }

    /**
     * Identifies the character set from the Content-Type header in the HttpResponse. If no Content-Type header or character set
     * exists, or the character set is not supported, returns the {@link ContentTypeMapper#DEFAULT_HTTP_CHARACTER_SET}.
     *
     * @param httpResponse HTTP response
     * @return character set from the response headers, or {@link ContentTypeMapper#DEFAULT_HTTP_CHARACTER_SET} if no charset is specified
     */
    public static Charset identifyCharsetFromResponse(HttpResponse httpResponse) {
        Charset charset = null;
        List<String> contentTypeHeaderValues = httpResponse.getHeader("Content-Type");
        if (contentTypeHeaderValues != null && !contentTypeHeaderValues.isEmpty()) {
            charset = getCharsetFromContentTypeHeader(contentTypeHeaderValues.get(0));
        }

        if (charset == null) {
            charset = DEFAULT_HTTP_CHARACTER_SET;
            logger.debug("No character set specified in Content-Type header. Using default charset {}.", charset);
        }

        return charset;
    }

    /**
     * Examines the Content-Type string to determine the charset. If there is no 'charset=' text in the header, or if the specified charset
     * is not supported, returns null.
     *
     * @param contentType the value of a Content-Type header
     * @return the charset indicated by the Content-Type header, or null if no charset is specified
     */
    private static Charset getCharsetFromContentTypeHeader(String contentType) {
        if (contentType == null) {
            return null;
        }

        int charsetIndex = contentType.lastIndexOf("charset=");
        String charsetString = contentType.substring(charsetIndex + "charset=".length());
        try {
            return Charset.forName(charsetString);
        } catch (UnsupportedCharsetException e) {
            logger.info("Unsupported character set {} in Content-Type header: {}.", charsetString, contentType);

            return null;
        }
    }
}
