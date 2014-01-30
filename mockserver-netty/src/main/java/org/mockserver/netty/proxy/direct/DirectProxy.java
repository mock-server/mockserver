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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class DirectProxy {


    public DirectProxy(int localPort, String remoteHost, int remotePort) throws Exception {
        System.err.println("Proxying *:" + localPort + " to " + remoteHost + ':' + remotePort + " ...");

        // Configure the bootstrap.
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new DirectProxyInitializer(remoteHost, remotePort, false, 1048576)) // 1048576
                    .childOption(ChannelOption.AUTO_READ, false)
                    .bind(localPort)
                    .sync()
                    .channel()
                    .closeFuture()
                    .sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws Exception {
        // Validate command line options.
        if (args.length != 3) {
            System.err.println(
                    "Usage: " + DirectProxy.class.getSimpleName() +
                            " <local port> <remote host> <remote port>");
            return;
        }

        // Parse command line options.
        int localPort = Integer.parseInt(args[0]);
        String remoteHost = args[1];
        int remotePort = Integer.parseInt(args[2]);

        new DirectProxy(localPort, remoteHost, remotePort);
    }
}
