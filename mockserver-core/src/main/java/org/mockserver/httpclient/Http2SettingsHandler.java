/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.mockserver.httpclient;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http2.Http2Settings;
import org.mockserver.model.Protocol;

import java.util.concurrent.CompletableFuture;

/**
 * Reads the first {@link Http2Settings} object
 */
public class Http2SettingsHandler extends SimpleChannelInboundHandler<Http2Settings> {
    private final CompletableFuture<Http2Settings> settingsFuture;

    public Http2SettingsHandler(CompletableFuture<Protocol> protocolFuture) {
        this.settingsFuture = new CompletableFuture<>();
        settingsFuture.whenComplete(((http2Settings, throwable) -> {
            if (throwable != null) {
                protocolFuture.completeExceptionally(throwable);
            } else if (http2Settings != null) {
                protocolFuture.complete(Protocol.HTTP2);
            } else {
                protocolFuture.complete(Protocol.HTTP);
            }
        }));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Http2Settings http2Settings) throws Exception {
        settingsFuture.complete(http2Settings);

        // Only care about the first settings message
        ctx.pipeline().remove(this);
    }
}
