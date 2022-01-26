package org.mockserver.netty.proxy.socks;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.socksx.v5.*;
import io.netty.handler.ssl.SslHandler;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.slf4j.event.Level;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.log.model.LogEntry.LogMessageType.AUTHENTICATION_FAILED;
import static org.mockserver.netty.unification.PortUnificationHandler.isSslEnabledUpstream;

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
        String username = ConfigurationProperties.proxyAuthenticationUsername();
        String password = ConfigurationProperties.proxyAuthenticationPassword();
        Socks5AuthMethod requiredAuthMethod;
        ChannelHandler nextRequestDecoder;
        if (initialRequest.authMethods().contains(Socks5AuthMethod.NO_AUTH)) {
            requiredAuthMethod = Socks5AuthMethod.NO_AUTH;
            nextRequestDecoder = new Socks5CommandRequestDecoder();
        } else if (initialRequest.authMethods().contains(Socks5AuthMethod.PASSWORD)) {
            if (isNotBlank(username) && isNotBlank(password)) {
                requiredAuthMethod = Socks5AuthMethod.PASSWORD;
                nextRequestDecoder = new Socks5PasswordAuthRequestDecoder();
            } else {
                requiredAuthMethod = Socks5AuthMethod.NO_AUTH;
                nextRequestDecoder = new Socks5CommandRequestDecoder();
            }
        } else {
            requiredAuthMethod = Socks5AuthMethod.NO_AUTH;
            nextRequestDecoder = new Socks5CommandRequestDecoder();
        }
        answerInitialRequest(ctx, initialRequest, requiredAuthMethod, nextRequestDecoder);
    }

    private void answerInitialRequest(ChannelHandlerContext ctx, Socks5InitialRequest initialRequest, Socks5AuthMethod requiredAuthMethod, ChannelHandler nextRequestDecoder) {
        ctx.writeAndFlush(initialRequest
            .authMethods()
            .stream()
            .filter(authMethod -> authMethod.equals(requiredAuthMethod))
            .findFirst()
            .map(authMethod -> {
                if (isSslEnabledUpstream(ctx.channel())) {
                    ctx.pipeline().addAfter(SslHandler.class.getName(), null, nextRequestDecoder);
                } else {
                    ctx.pipeline().addFirst(nextRequestDecoder);
                }
                return new DefaultSocks5InitialResponse(requiredAuthMethod);
            })
            .orElse(new DefaultSocks5InitialResponse(Socks5AuthMethod.UNACCEPTED))
        );
        ctx.pipeline().remove(Socks5InitialRequestDecoder.class);
    }

    private void handlePasswordAuthRequest(ChannelHandlerContext ctx, Socks5PasswordAuthRequest passwordAuthRequest) {
        String username = ConfigurationProperties.proxyAuthenticationUsername();
        String password = ConfigurationProperties.proxyAuthenticationPassword();
        // we need the null-check again here, in case the properties got unset between init and auth request
        if (!username.isEmpty() && !password.isEmpty()
            && username.equals(passwordAuthRequest.username()) && password.equals(passwordAuthRequest.password())) {
            ctx.pipeline().replace(Socks5PasswordAuthRequestDecoder.class, null, new Socks5CommandRequestDecoder());
            ctx.writeAndFlush(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS)).awaitUninterruptibly();
        } else {
            ctx.writeAndFlush(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.FAILURE)).addListener(ChannelFutureListener.CLOSE);
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(AUTHENTICATION_FAILED)
                    .setLogLevel(Level.INFO)
                    .setMessageFormat("proxy authentication failed so returning SOCKS FAILURE response")
            );
        }
    }

    private void handleCommandRequest(ChannelHandlerContext ctx, final Socks5CommandRequest commandRequest) {
        if (commandRequest.type().equals(Socks5CommandType.CONNECT)) { // IN HERE
            forwardConnection(ctx, new Socks5ConnectHandler(server, mockServerLogger, commandRequest.dstAddr(), commandRequest.dstPort()), commandRequest.dstAddr(), commandRequest.dstPort());
            ctx.fireChannelRead(commandRequest);
        } else {
            ctx.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.COMMAND_UNSUPPORTED, Socks5AddressType.DOMAIN, "", 0)).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
