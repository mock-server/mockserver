package org.mockserver.model;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;

import java.util.List;
import java.util.Map;

/**
 * @author jamesdbloom
 */
public class NettyHttpRequest extends DefaultHttpRequest {
    private final String path;
    private final Map<String, List<String>> parameters;
    private boolean secure;
    private ByteBuf content;

    public NettyHttpRequest(HttpVersion httpVersion, HttpMethod method, String uri, boolean secure) {
        super(httpVersion, method, uri);
        QueryStringDecoder queryStringDecoder = new QueryStringDecoder(uri);
        this.path = queryStringDecoder.path();
        this.parameters = queryStringDecoder.parameters();
        this.secure = secure;
    }

    public void content(ByteBuf content) {
        if (this.content == null) {
            this.content = Unpooled.copiedBuffer(content);
        } else {
            this.content.writeBytes(content);
        }
    }

    public ByteBuf content() {
        return content;
    }

    public String path() {
        return path;
    }

    public Map<String, List<String>> parameters() {
        return parameters;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public boolean isSecure() {
        return secure;
    }

    public boolean matches(HttpMethod method, String path) {
        return getMethod() == method && this.path.equals(path);
    }
}
