package org.mockserver.mockserver;

import com.google.common.net.MediaType;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;
import org.mockserver.client.netty.proxy.ProxyConfiguration;
import org.mockserver.client.serialization.Base64Converter;
import org.mockserver.client.serialization.PortBindingSerializer;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.dashboard.DashboardHandler;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.mock.action.ActionHandler;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.PortBinding;
import org.mockserver.proxy.connect.HttpConnectHandler;
import org.mockserver.responsewriter.NettyResponseWriter;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.socket.KeyAndCertificateFactory;

import javax.annotation.Nullable;
import java.net.BindException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http.HttpHeaderNames.PROXY_AUTHENTICATE;
import static io.netty.handler.codec.http.HttpHeaderNames.PROXY_AUTHORIZATION;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Locale.US;
import static org.mockserver.exception.ExceptionHandler.closeOnFlush;
import static org.mockserver.exception.ExceptionHandler.shouldNotIgnoreException;
import static org.mockserver.mock.HttpStateHandler.PATH_PREFIX;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.PortBinding.portBinding;
import static org.mockserver.unification.PortUnificationHandler.enableSslUpstreamAndDownstream;

/**
 * @author jamesdbloom
 */
@ChannelHandler.Sharable
public class MockServerHandler extends SimpleChannelInboundHandler<HttpRequest> {

    public static final AttributeKey<Boolean> PROXYING = AttributeKey.valueOf("PROXYING");
    public static final AttributeKey<Set> LOCAL_HOST_HEADERS = AttributeKey.valueOf("LOCAL_HOST_HEADERS");
    private MockServerLogger mockServerLogger;
    private HttpStateHandler httpStateHandler;
    private PortBindingSerializer portBindingSerializer;
    private LifeCycle server;
    private ActionHandler actionHandler;
    private DashboardHandler dashboardHandler = new DashboardHandler();

    public MockServerHandler(LifeCycle server, HttpStateHandler httpStateHandler, @Nullable ProxyConfiguration proxyConfiguration) {
        super(false);
        this.server = server;
        this.httpStateHandler = httpStateHandler;
        this.mockServerLogger = httpStateHandler.getMockServerLogger();
        this.portBindingSerializer = new PortBindingSerializer(mockServerLogger);
        this.actionHandler = new ActionHandler(httpStateHandler, proxyConfiguration);
    }

    private static boolean isProxyingRequest(ChannelHandlerContext ctx) {
        if (ctx != null && ctx.channel().attr(PROXYING).get() != null) {
            return ctx.channel().attr(PROXYING).get();
        }
        return false;
    }

    public static Set<String> getLocalAddresses(ChannelHandlerContext ctx) {
        if (ctx != null &&
            ctx.channel().attr(LOCAL_HOST_HEADERS) != null &&
            ctx.channel().attr(LOCAL_HOST_HEADERS).get() != null) {
            return ctx.channel().attr(LOCAL_HOST_HEADERS).get();
        }
        return new HashSet<>();
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final HttpRequest request) {

        ResponseWriter responseWriter = new NettyResponseWriter(ctx);
        try {

            server.getScheduler().submit(new Runnable() {
                @Override
                public void run() {
                    final String hostHeader = request.getFirstHeader(HOST.toString());
                    KeyAndCertificateFactory.addSubjectAlternativeName(hostHeader);
                }
            });

            if (!httpStateHandler.handle(request, responseWriter, false)) {

                if (request.matches("PUT", PATH_PREFIX + "/status", "/status")) {

                    responseWriter.writeResponse(request, OK, portBindingSerializer.serialize(portBinding(server.getLocalPorts())), "application/json");

                } else if (request.matches("PUT", PATH_PREFIX + "/bind", "/bind")) {

                    PortBinding requestedPortBindings = portBindingSerializer.deserialize(request.getBodyAsString());
                    try {
                        List<Integer> actualPortBindings = server.bindServerPorts(requestedPortBindings.getPorts());
                        responseWriter.writeResponse(request, OK, portBindingSerializer.serialize(portBinding(actualPortBindings)), "application/json");
                    } catch (RuntimeException e) {
                        if (e.getCause() instanceof BindException) {
                            responseWriter.writeResponse(request, BAD_REQUEST, e.getMessage() + " port already in use", MediaType.create("text", "plain").toString());
                        } else {
                            throw e;
                        }
                    }

                } else if (request.getMethod().getValue().equals("GET") && request.getPath().getValue().startsWith(PATH_PREFIX + "/dashboard")) {

                    dashboardHandler.renderDashboard(ctx, request);

                } else if (request.matches("PUT", PATH_PREFIX + "/stop", "/stop")) {

                    ctx.writeAndFlush(response().withStatusCode(OK.code()));
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            server.stop();
                        }
                    }).start();

                } else if (request.getMethod().getValue().equals("CONNECT")) {

                    String username = ConfigurationProperties.httpProxyServerUsername();
                    String password = ConfigurationProperties.httpProxyServerPassword();
                    if ((!username.isEmpty() && !password.isEmpty())
                        && (!request.containsHeader(PROXY_AUTHORIZATION.toString())
                            || !request.getFirstHeader(PROXY_AUTHORIZATION.toString()).toLowerCase(US).startsWith("basic ")
                            || !request.getFirstHeader(PROXY_AUTHORIZATION.toString()).substring(6).equals(
                                new Base64Converter().bytesToBase64String((username + ":" + password).getBytes(UTF_8))))) {

                        ctx.writeAndFlush(response()
                            .withStatusCode(PROXY_AUTHENTICATION_REQUIRED.code())
                            .withHeader(PROXY_AUTHENTICATE.toString(), "Basic realm=\"" + ConfigurationProperties.httpProxyServerRealm().replace("\"", "\\\"") + "\""));
                    } else {
                        ctx.channel().attr(PROXYING).set(Boolean.TRUE);
                        // assume SSL for CONNECT request
                        enableSslUpstreamAndDownstream(ctx.channel());
                        // add Subject Alternative Name for SSL certificate
                        server.getScheduler().submit(new Runnable() {
                            @Override
                            public void run() {
                                KeyAndCertificateFactory.addSubjectAlternativeName(request.getPath().getValue());
                            }
                        });
                        ctx.pipeline().addLast(new HttpConnectHandler(server, mockServerLogger, request.getPath().getValue(), -1));
                        ctx.pipeline().remove(this);
                        ctx.fireChannelRead(request);
                    }

                } else {

                    actionHandler.processAction(request, responseWriter, ctx, getLocalAddresses(ctx), isProxyingRequest(ctx), false);

                }
            }
        } catch (IllegalArgumentException iae) {
            mockServerLogger.error(request, "exception processing: {} error: {}", request, iae.getMessage());
            // send request without API CORS headers
            responseWriter.writeResponse(request, BAD_REQUEST, iae.getMessage(), MediaType.create("text", "plain").toString());
        } catch (Exception e) {
            mockServerLogger.error(request, e, "exception processing " + request);
            responseWriter.writeResponse(request, response().withStatusCode(BAD_REQUEST.code()).withBody(e.getMessage()), true);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (shouldNotIgnoreException(cause)) {
            new MockServerLogger(this.getClass()).error("Exception caught by " + server.getClass() + " handler -> closing pipeline " + ctx.channel(), cause);
        }
        closeOnFlush(ctx.channel());
    }
}
