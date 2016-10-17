package org.mockserver.mappers;

import com.google.common.base.Strings;
import io.netty.handler.codec.http.HttpConstants;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.util.CharsetUtil;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;

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
    private static final Logger logger = LoggerFactory.getLogger(ContentTypeMapper.class);

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
                    || contentType.contains("form")
                    || contentType.contains("urlencoded")
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

    public static Charset determineCharsetForMessage(HttpMessage httpMessage) {
        return getCharsetFromContentTypeHeader(httpMessage.headers().get(CONTENT_TYPE));
    }

    public static Charset determineCharsetForMessage(HttpServletRequest servletRequest) {
        return getCharsetFromContentTypeHeader(servletRequest.getHeader(CONTENT_TYPE));
    }

    public static Charset determineCharsetForMessage(HttpResponse httpResponse) {
        return getCharsetFromContentTypeHeader(httpResponse.getFirstHeader(CONTENT_TYPE));
    }

    public static Charset determineCharsetForMessage(HttpRequest httpRequest) {
        return getCharsetFromContentTypeHeader(httpRequest.getFirstHeader(CONTENT_TYPE));
    }

    private static Charset getCharsetFromContentTypeHeader(String contentType) {
        Charset charset = DEFAULT_HTTP_CHARACTER_SET;
        if (contentType != null) {
            String charsetName = StringUtils.substringAfterLast(contentType, HttpHeaders.Values.CHARSET + (char) HttpConstants.EQUALS);
            if (!Strings.isNullOrEmpty(charsetName)) {
                try {
                    charset = Charset.forName(charsetName);
                } catch (UnsupportedCharsetException uce) {
                    logger.info("Unsupported character set {} in Content-Type header: {}.", StringUtils.substringAfterLast(contentType, HttpHeaders.Values.CHARSET + HttpConstants.EQUALS), contentType);
                } catch (IllegalCharsetNameException icne) {
                    logger.info("Illegal character set {} in Content-Type header: {}.", StringUtils.substringAfterLast(contentType, HttpHeaders.Values.CHARSET + HttpConstants.EQUALS), contentType);
                }
            }
        }
        return charset;
    }
}
