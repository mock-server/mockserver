/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.mockserver.netty.proxy.http.connect;

import com.google.common.base.Charsets;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;

public class HexDumpProxyBackendHandler extends ChannelDuplexHandler {

    private final Channel inboundChannel;

    public HexDumpProxyBackendHandler(Channel inboundChannel) {
        this.inboundChannel = inboundChannel;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ctx.read();
        ctx.write(Unpooled.EMPTY_BUFFER);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (inboundChannel.isActive()) {
            inboundChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if(evt instanceof SslHandshakeCompletionEvent) {
            inboundChannel.writeAndFlush(Unpooled.copiedBuffer("HTTP/1.1 200 OK", Charsets.UTF_8)).addListeners(
                    new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            // connection complete start to read first data
                            inboundChannel.read();
                        }
                    }
            );
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
        inboundChannel.writeAndFlush(msg).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    ctx.channel().read();
                } else {
                    future.channel().close();
                }
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        Channel ch = ctx.channel();
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
