package org.mockserver.client.netty.codec;

import com.google.common.base.Charsets;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import org.mockserver.mappers.ContentTypeMapper;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpCookie;
import java.util.*;

/**
 * @author jamesdbloom
 */
public class MockServerResponseDecoder extends MessageToMessageDecoder<FullHttpResponse> {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    protected void decode(ChannelHandlerContext ctx, FullHttpResponse fullHttpResponse, List<Object> out) throws Exception {
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
//        List<String> headersToRemove = Arrays.asList("Content-Encoding", "Content-Length", "Transfer-Encoding");
//        for (Header header : new ArrayList<Header>(headers)) {
//            if (headersToRemove.contains(header.getName())) {
//                headers.remove(header);
//            }
//        }
        httpResponse.withHeaders(headers);
    }

    private void setCookies(HttpResponse httpResponse) {
        Map<String, Cookie> mappedCookies = new HashMap<String, Cookie>();
        for (Header header : httpResponse.getHeaders()) {
            if (header.getName().equals("Cookie") || header.getName().equals("Set-Cookie")) {
                for (String cookieHeader : header.getValues()) {
                    try {
                        for (HttpCookie httpCookie : HttpCookie.parse(cookieHeader)) {
                            if (mappedCookies.containsKey(httpCookie.getName())) {
                                mappedCookies.get(httpCookie.getName()).addValue(httpCookie.getValue());
                            } else {
                                mappedCookies.put(httpCookie.getName(), new Cookie(httpCookie.getName(), httpCookie.getValue()));
                            }
                        }
                    } catch (IllegalArgumentException iae) {
                        logger.warn("Exception while parsing cookie header [" + cookieHeader + "]", iae);
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
