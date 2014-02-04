package org.mockserver.proxy.interceptor;

import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseDecoder;

public class HeadAwareHttpResponseDecoder extends HttpResponseDecoder {

    public HeadAwareHttpResponseDecoder(int maxInitialLineLength, int maxHeaderSize, int maxChunkSize) {
        super(maxInitialLineLength, maxHeaderSize, maxChunkSize);
    }

    @Override
    protected boolean isContentAlwaysEmpty(HttpMessage httpMessage) {
        if (httpMessage instanceof HttpRequest) {
            return HttpMethod.HEAD.equals(((HttpRequest) httpMessage).getMethod()) || super.isContentAlwaysEmpty(httpMessage);
        } else {
            return false;
        }
    }
}