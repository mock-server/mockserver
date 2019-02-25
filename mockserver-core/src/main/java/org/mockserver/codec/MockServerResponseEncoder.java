package org.mockserver.codec;

import com.google.common.base.Strings;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.mappers.ContentTypeMapper;
import org.mockserver.model.*;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static org.mockserver.codec.BodyDecoderEncoder.bodyToByteBuf;
import static org.mockserver.model.ConnectionOptions.isFalseOrNull;

/**
 * @author jamesdbloom
 */
public class MockServerResponseEncoder extends MessageToMessageEncoder<HttpResponse> {
    @Override
    protected void encode(ChannelHandlerContext ctx, HttpResponse response, List<Object> out) {
        out.add(encode(response));
    }

    public DefaultFullHttpResponse encode(HttpResponse httpResponse) {
        DefaultFullHttpResponse defaultFullHttpResponse = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            getStatus(httpResponse),
            getBody(httpResponse)
        );
        setHeaders(httpResponse, defaultFullHttpResponse);
        setCookies(httpResponse, defaultFullHttpResponse);
        return defaultFullHttpResponse;
    }

    private HttpResponseStatus getStatus(HttpResponse httpResponse) {
        int statusCode = httpResponse.getStatusCode() != null ? httpResponse.getStatusCode() : 200;
        if (!StringUtils.isEmpty(httpResponse.getReasonPhrase())) {
            return new HttpResponseStatus(statusCode, httpResponse.getReasonPhrase());
        } else {
            return HttpResponseStatus.valueOf(statusCode);
        }
    }

    private ByteBuf getBody(HttpResponse httpResponse) {
        return bodyToByteBuf(httpResponse.getBody(), httpResponse.getFirstHeader(CONTENT_TYPE.toString()));
    }

    private void setHeaders(HttpResponse httpResponse, DefaultFullHttpResponse response) {
        if (httpResponse.getHeaderList() != null) {
            for (Header header : httpResponse.getHeaderList()) {
                for (NottableString value : header.getValues()) {
                    response.headers().add(header.getName().getValue(), value.getValue());
                }
            }
        }

        // Content-Type
        if (Strings.isNullOrEmpty(httpResponse.getFirstHeader(CONTENT_TYPE.toString()))) {
            if (httpResponse.getBody() != null
                && httpResponse.getBody().getContentType() != null) {
                response.headers().set(CONTENT_TYPE, httpResponse.getBody().getContentType());
            }
        }

        // Content-Length
        if (Strings.isNullOrEmpty(httpResponse.getFirstHeader(CONTENT_LENGTH.toString()))) {
            ConnectionOptions connectionOptions = httpResponse.getConnectionOptions();
            boolean overrideContentLength = connectionOptions != null && connectionOptions.getContentLengthHeaderOverride() != null;
            boolean addContentLength = connectionOptions == null || isFalseOrNull(connectionOptions.getSuppressContentLengthHeader());
            if (overrideContentLength) {
                response.headers().set(CONTENT_LENGTH, connectionOptions.getContentLengthHeaderOverride());
            } else if (addContentLength) {
                Body body = httpResponse.getBody();
                byte[] bodyBytes = new byte[0];
                if (body != null) {
                    Object bodyContents = body.getValue();
                    Charset bodyCharset = body.getCharset(ContentTypeMapper.getCharsetFromContentTypeHeader(httpResponse.getFirstHeader(CONTENT_TYPE.toString())));
                    if (bodyContents instanceof byte[]) {
                        bodyBytes = (byte[]) bodyContents;
                    } else if (bodyContents instanceof String) {
                        bodyBytes = ((String) bodyContents).getBytes(bodyCharset);
                    } else if (body.toString() != null) {
                        bodyBytes = body.toString().getBytes(bodyCharset);
                    }
                }
                response.headers().set(CONTENT_LENGTH, bodyBytes.length);
            }
        }
    }

    private void setCookies(HttpResponse httpResponse, DefaultFullHttpResponse response) {
        if (httpResponse.getCookieList() != null) {
            List<Cookie> cookieValues = new ArrayList<Cookie>();
            for (org.mockserver.model.Cookie cookie : httpResponse.getCookieList()) {
                if (!cookieHeaderAlreadyExists(httpResponse, cookie)) {
                    cookieValues.add(new DefaultCookie(cookie.getName().getValue(), cookie.getValue().getValue()));
                }
            }
            for (Cookie cookieValue : cookieValues) {
                response.headers().add(SET_COOKIE, ServerCookieEncoder.LAX.encode(cookieValue));
            }
        }
    }

    private boolean cookieHeaderAlreadyExists(HttpResponse httpResponse, org.mockserver.model.Cookie cookieValue) {
        List<String> setCookieHeaders = httpResponse.getHeader(SET_COOKIE.toString());
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
