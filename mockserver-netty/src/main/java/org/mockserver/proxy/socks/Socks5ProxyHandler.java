package org.mockserver.proxy.socks;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialResponse;
import io.netty.handler.codec.socksx.v5.DefaultSocks5PasswordAuthResponse;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.handler.codec.socksx.v5.Socks5AuthMethod;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.handler.codec.socksx.v5.Socks5CommandType;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequest;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5Message;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthRequest;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthStatus;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.logging.MockServerLogger;

@ChannelHandler.Sharable
public class Socks5ProxyHandler extends SocksProxyHandler<Socks5Message> {

    public Socks5ProxyHandler(LifeCycle server, MockServerLogger mockServerLogger) {
        super(server, mockServerLogger);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, Socks5Message socksRequest) {
        if (socksRequest instanceof Socks5InitialRequest) {
            handleInitialRequest(ctx, (Socks5InitialRequest) socksRequest);
        } else if (socksRequest instanceof Socks5PasswordAuthRequest) {
            handlePasswordAuthRequest(ctx, (Socks5PasswordAuthRequest) socksRequest);
        } else if (socksRequest instanceof Socks5CommandRequest) {
            handleCommandRequest(ctx, (Socks5CommandRequest) socksRequest);
        } else {
            ctx.close();
        }
    }

    private void handleInitialRequest(ChannelHandlerContext ctx, Socks5InitialRequest initialRequest) {
        String username = ConfigurationProperties.socksProxyServerUsername();
        String password = ConfigurationProperties.socksProxyServerPassword();
        Socks5AuthMethod requiredAuthMethod;
        ChannelHandler nextRequestDecoder;
        if (!username.isEmpty() && !password.isEmpty()) {
            requiredAuthMethod = Socks5AuthMethod.PASSWORD;
            nextRequestDecoder = new Socks5PasswordAuthRequestDecoder();
        } else {
            requiredAuthMethod = Socks5AuthMethod.NO_AUTH;
            nextRequestDecoder = new Socks5CommandRequestDecoder();
        }
        answerInitialRequest(ctx, initialRequest, requiredAuthMethod, nextRequestDecoder);
    }

    private void answerInitialRequest(ChannelHandlerContext ctx, Socks5InitialRequest initialRequest, Socks5AuthMethod requiredAuthMethod, ChannelHandler nextRequestDecoder) {
        ctx.pipeline().remove(Socks5InitialRequestDecoder.class);
        for (Socks5AuthMethod authMethod : initialRequest.authMethods()) {
            if (requiredAuthMethod.equals(authMethod)) {
                ctx.pipeline().addFirst(nextRequestDecoder);
                ctx.write(new DefaultSocks5InitialResponse(requiredAuthMethod));
                return;
            }
        }
        ctx.write(new DefaultSocks5InitialResponse(Socks5AuthMethod.UNACCEPTED));
    }

    private void handlePasswordAuthRequest(ChannelHandlerContext ctx, Socks5PasswordAuthRequest passwordAuthRequest) {
        String username = ConfigurationProperties.socksProxyServerUsername();
        String password = ConfigurationProperties.socksProxyServerPassword();
        // we need the null-check again here, in case the properties got unset between init and auth request
        if (!username.isEmpty() && !password.isEmpty()
            && username.equals(passwordAuthRequest.username()) && password.equals(passwordAuthRequest.password())) {

            ctx.pipeline().replace(Socks5PasswordAuthRequestDecoder.class, null, new Socks5CommandRequestDecoder());
            ctx.write(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS));
        } else {
            ctx.writeAndFlush(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.FAILURE)).addListener(ChannelFutureListener.CLOSE);
        }
    }

    private void handleCommandRequest(ChannelHandlerContext ctx, final Socks5CommandRequest commandRequest) {
        if (commandRequest.type().equals(Socks5CommandType.CONNECT)) {
            forwardConnection(ctx, new Socks5ConnectHandler(server, mockServerLogger, commandRequest.dstAddr(), commandRequest.dstPort()), commandRequest.dstAddr(), commandRequest.dstPort());
            ctx.fireChannelRead(commandRequest);
        } else {
            ctx.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.COMMAND_UNSUPPORTED, Socks5AddressType.DOMAIN, "", 0)).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
