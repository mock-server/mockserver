package org.mockserver.model;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.CharsetUtil;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.mockserver.url.URLParser;

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
        this.path = URLParser.returnPath(queryStringDecoder.path());
        this.parameters = queryStringDecoder.parameters();
        this.secure = secure;
    }

    public void content(ByteBuf content) {
        if (this.content == null) {
            this.content = Unpooled.copiedBuffer(content);
        } else {
            if (this.content.isWritable(content.readableBytes())) {
                this.content.writeBytes(content);
            } else {
                this.content = Unpooled.copiedBuffer(this.content, content);
            }
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

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public boolean matches(HttpMethod method, String path) {
        return getMethod() == method && this.path.equals(path);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("NettyHttpRequest => {");
        sb.append("path='").append(path).append('\'');
        sb.append(", parameters=").append(parameters);
        sb.append(", secure=").append(secure);
        sb.append(", content=").append((content != null ? new String(Unpooled.copiedBuffer(content).array(), CharsetUtil.UTF_8) : ""));
        sb.append('}');
        return sb.toString();
    }
}
