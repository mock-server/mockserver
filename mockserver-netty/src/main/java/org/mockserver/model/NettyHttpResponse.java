package org.mockserver.model;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import org.mockserver.model.HttpStatusCode;

/**
 * @author jamesdbloom
 */
public class NettyHttpResponse extends DefaultHttpResponse {
    private ByteBuf content;

    public NettyHttpResponse(HttpVersion httpVersion, HttpResponseStatus status) {
        super(httpVersion, status);
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
}
