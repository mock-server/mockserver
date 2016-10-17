package org.mockserver.codec;

import com.google.common.base.Strings;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import org.mockserver.mappers.ContentTypeMapper;
import org.mockserver.model.*;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaders.Names.SET_COOKIE;
import static org.mockserver.model.ConnectionOptions.isFalseOrNull;

/**
 * @author jamesdbloom
 */
public class MockServerResponseEncoder extends MessageToMessageEncoder<HttpResponse> {
    @Override
    protected void encode(ChannelHandlerContext ctx, HttpResponse response, List<Object> out) {
        DefaultFullHttpResponse defaultFullHttpResponse = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.valueOf((response.getStatusCode() != null ? response.getStatusCode() : 200)),
                getBody(response)
        );
        setHeaders(response, defaultFullHttpResponse);
        setCookies(response, defaultFullHttpResponse);
        out.add(defaultFullHttpResponse);
    }

    private ByteBuf getBody(HttpResponse response) {
        ByteBuf content = Unpooled.buffer(0, 0);

        Body body = response.getBody();
        if (body != null) {
            Object bodyContents = body.getValue();
            Charset bodyCharset = body.getCharset(ContentTypeMapper.determineCharsetForMessage(response));
            if (bodyContents instanceof byte[]) {
                content = Unpooled.copiedBuffer((byte[]) bodyContents);
            } else if (bodyContents instanceof String) {
                content = Unpooled.copiedBuffer(((String) bodyContents).getBytes(bodyCharset));
            } else if (body.toString() != null) {
                content = Unpooled.copiedBuffer(body.toString().getBytes(bodyCharset));
            }
        }
        return content;
    }

    private void setHeaders(HttpResponse response, DefaultFullHttpResponse defaultFullHttpResponse) {
        if (response.getHeaders() != null) {
            for (Header header : response.getHeaders()) {
                for (NottableString value : header.getValues()) {
                    defaultFullHttpResponse.headers().add(header.getName().getValue(), value.getValue());
                }
            }
        }

        // Content-Type
        if (Strings.isNullOrEmpty(response.getFirstHeader(CONTENT_TYPE))) {
            if (response.getBody() != null
                    && response.getBody().getContentType() != null) {
                defaultFullHttpResponse.headers().set(CONTENT_TYPE, response.getBody().getContentType());
            }
        }

        // Content-Length
        if (Strings.isNullOrEmpty(response.getFirstHeader(CONTENT_LENGTH))) {
            ConnectionOptions connectionOptions = response.getConnectionOptions();
            boolean overrideContentLength = connectionOptions != null && connectionOptions.getContentLengthHeaderOverride() != null;
            boolean addContentLength = connectionOptions == null || isFalseOrNull(connectionOptions.getSuppressContentLengthHeader());
            if (overrideContentLength) {
                defaultFullHttpResponse.headers().set(CONTENT_LENGTH, connectionOptions.getContentLengthHeaderOverride());
            } else if (addContentLength) {
                Body body = response.getBody();
                byte[] bodyBytes = new byte[0];
                if (body != null) {
                    Object bodyContents = body.getValue();
                    Charset bodyCharset = body.getCharset(ContentTypeMapper.determineCharsetForMessage(response));
                    if (bodyContents instanceof byte[]) {
                        bodyBytes = (byte[]) bodyContents;
                    } else if (bodyContents instanceof String) {
                        bodyBytes = ((String) bodyContents).getBytes(bodyCharset);
                    } else if (body.toString() != null) {
                        bodyBytes = body.toString().getBytes(bodyCharset);
                    }
                }
                defaultFullHttpResponse.headers().set(CONTENT_LENGTH, bodyBytes.length);
            }
        }
    }

    private void setCookies(HttpResponse response, DefaultFullHttpResponse httpServletResponse) {
        if (response.getCookies() != null) {
            List<Cookie> cookieValues = new ArrayList<Cookie>();
            for (org.mockserver.model.Cookie cookie : response.getCookies()) {
                if (!cookieHeaderAlreadyExists(response, cookie)) {
                    cookieValues.add(new DefaultCookie(cookie.getName().getValue(), cookie.getValue().getValue()));
                }
            }
            for (Cookie cookieValue : cookieValues) {
                httpServletResponse.headers().add(SET_COOKIE, ServerCookieEncoder.LAX.encode(cookieValue));
            }
        }
    }

    private boolean cookieHeaderAlreadyExists(HttpResponse response, org.mockserver.model.Cookie cookieValue) {
        List<String> setCookieHeaders = response.getHeader(SET_COOKIE);
        setCookieHeaders.addAll(response.getHeader(SET_COOKIE.toLowerCase()));
        for (String setCookieHeader : setCookieHeaders) {
            String existingCookieName = ClientCookieDecoder.LAX.decode(setCookieHeader).name();
            String existingCookieValue = ClientCookieDecoder.LAX.decode(setCookieHeader).value();
            if (existingCookieName.equalsIgnoreCase(cookieValue.getName().getValue()) && existingCookieValue.equalsIgnoreCase(cookieValue.getValue().getValue())) {
                return true;
            }
        }
        return false;
    }
}
