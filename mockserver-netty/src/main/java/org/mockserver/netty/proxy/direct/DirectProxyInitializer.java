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
package org.mockserver.netty.proxy.direct;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslHandler;
import org.mockserver.socket.SSLFactory;

import javax.net.ssl.SSLEngine;

public class DirectProxyInitializer extends ChannelInitializer<SocketChannel> {

    private final String remoteHost;
    private final int remotePort;
    private final boolean secure;
    private final int bufferedCapacity;

    public DirectProxyInitializer(String remoteHost, int remotePort, boolean secure, int bufferedCapacity) {
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.secure = secure;
        this.bufferedCapacity = bufferedCapacity;
    }

    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        // Create a default pipeline implementation.
        ChannelPipeline pipeline = ch.pipeline();

        // add HTTPS support
        if (secure) {
            SSLEngine engine = SSLFactory.sslContext().createSSLEngine();
            engine.setUseClientMode(false);
            pipeline.addLast("ssl", new SslHandler(engine));
        }

        // add logging
//        pipeline.addFirst("logger", new LoggingHandler());
//        pipeline.addFirst("log", new LogHandler());

        // add handler
        pipeline.addLast(new DirectProxyUpstreamHandler(remoteHost, remotePort, secure, bufferedCapacity));
    }
}
