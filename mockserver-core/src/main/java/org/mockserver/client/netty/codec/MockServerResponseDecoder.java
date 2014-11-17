package org.mockserver.client.netty.codec;

import com.google.common.base.Charsets;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.CookieDecoder;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import org.mockserver.mappers.ContentTypeMapper;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jamesdbloom
 */
public class MockServerResponseDecoder extends MessageToMessageDecoder<FullHttpResponse> {

    @Override
    protected void decode(ChannelHandlerContext ctx, FullHttpResponse fullHttpResponse, List<Object> out) {
        HttpResponse httpResponse = new HttpResponse();
        if (fullHttpResponse != null) {
            setStatusCode(httpResponse, fullHttpResponse);
            setHeaders(httpResponse, fullHttpResponse);
            setCookies(httpResponse);
            setBody(httpResponse, fullHttpResponse);
        }
        out.add(httpResponse);
    }

    private void setStatusCode(HttpResponse httpResponse, FullHttpResponse fullHttpResponse) {
        httpResponse.withStatusCode(fullHttpResponse.getStatus().code());
    }

    private void setHeaders(HttpResponse httpResponse, FullHttpResponse fullHttpResponse) {
        Map<String, Header> mappedHeaders = new HashMap<String, Header>();
        for (String headerName : fullHttpResponse.headers().names()) {
            mappedHeaders.put(headerName, new Header(headerName, fullHttpResponse.headers().getAll(headerName)));
        }
        List<Header> headers = new ArrayList<Header>(mappedHeaders.values());
        httpResponse.withHeaders(headers);
    }

    private void setCookies(HttpResponse httpResponse) {
        Map<String, Cookie> mappedCookies = new HashMap<String, Cookie>();
        for (Header header : httpResponse.getHeaders()) {
            if (header.getName().equals("Cookie") || header.getName().equals("Set-Cookie")) {
                for (String cookieHeader : header.getValues()) {
                    for (io.netty.handler.codec.http.Cookie httpCookie : CookieDecoder.decode(cookieHeader)) {
                        String name = httpCookie.getName().trim();
                        String value = httpCookie.getValue().trim();
                        if (mappedCookies.containsKey(name)) {
                            mappedCookies.get(name).addValue(value);
                        } else {
                            mappedCookies.put(name, new Cookie(name, value));
                        }
                    }
                }
            }
        }
        httpResponse.withCookies(new ArrayList<Cookie>(mappedCookies.values()));
    }

    private void setBody(HttpResponse httpResponse, FullHttpResponse fullHttpResponse) {
        if (fullHttpResponse.content().readableBytes() > 0) {
            ByteBuf byteBuf = fullHttpResponse.content().readBytes(fullHttpResponse.content().readableBytes());
            if (ContentTypeMapper.isBinary(fullHttpResponse.headers().get(HttpHeaders.Names.CONTENT_TYPE))) {
                httpResponse.withBody(byteBuf.array());
            } else {
                httpResponse.withBody(byteBuf.toString(Charsets.UTF_8));
            }
        }
    }
}
