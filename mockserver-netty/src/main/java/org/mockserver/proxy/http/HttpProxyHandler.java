package org.mockserver.proxy.http;

import com.google.common.net.MediaType;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.mockserver.client.serialization.PortBindingSerializer;
import org.mockserver.logging.LoggingFormatter;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.mock.action.ActionHandler;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.PortBinding;
import org.mockserver.proxy.Proxy;
import org.mockserver.proxy.connect.HttpConnectHandler;
import org.mockserver.proxy.unification.PortUnificationHandler;
import org.mockserver.responsewriter.NettyResponseWriter;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.socket.KeyAndCertificateFactory;
import org.slf4j.LoggerFactory;

import java.net.BindException;
import java.util.List;

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.mockserver.exception.ExceptionHandler.closeOnFlush;
import static org.mockserver.exception.ExceptionHandler.shouldIgnoreException;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.PortBinding.portBinding;

/**
 * @author jamesdbloom
 */
@ChannelHandler.Sharable
public class HttpProxyHandler extends SimpleChannelInboundHandler<HttpRequest> {

    private LoggingFormatter logFormatter;
    // generic handling
    private HttpStateHandler httpStateHandler;
    // serializers
    private PortBindingSerializer portBindingSerializer = new PortBindingSerializer();
    // server
    private Proxy server;
    // expectations
    private ActionHandler actionHandler;

    public HttpProxyHandler(Proxy server, HttpStateHandler httpStateHandler) {
        super(false);
        this.server = server;
        this.httpStateHandler = httpStateHandler;
        this.logFormatter = httpStateHandler.getLogFormatter();
        this.actionHandler = new ActionHandler(httpStateHandler, true);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpRequest request) {

        ResponseWriter responseWriter = new NettyResponseWriter(ctx);
        try {

            if (!httpStateHandler.handle(request, responseWriter, false)) {

                if (request.matches("PUT", "/status")) {

                    responseWriter.writeResponse(request, OK, portBindingSerializer.serialize(portBinding(server.getPorts())), "application/json");

                } else if (request.matches("PUT", "/bind")) {

                    PortBinding requestedPortBindings = portBindingSerializer.deserialize(request.getBodyAsString());
                    try {
                        List<Integer> actualPortBindings = server.bindToPorts(requestedPortBindings.getPorts());
                        responseWriter.writeResponse(request, OK, portBindingSerializer.serialize(portBinding(actualPortBindings)), "application/json");
                    } catch (RuntimeException e) {
                        if (e.getCause() instanceof BindException) {
                            responseWriter.writeResponse(request, BAD_REQUEST, e.getMessage() + " port already in use", MediaType.create("text", "plain").toString());
                        } else {
                            throw e;
                        }
                    }

                } else if (request.matches("PUT", "/stop")) {

                    ctx.writeAndFlush(response().withStatusCode(OK.code()));
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            server.stop();
                        }
                    }).start();

                } else if (request.getMethod().getValue().equals("CONNECT")) {

                    // assume CONNECT always for SSL
                    PortUnificationHandler.enabledSslUpstreamAndDownstream(ctx.channel());
                    // add Subject Alternative Name for SSL certificate
                    KeyAndCertificateFactory.addSubjectAlternativeName(request.getPath().getValue());
                    ctx.pipeline().addLast(new HttpConnectHandler(request.getPath().getValue(), -1));
                    ctx.pipeline().remove(this);
                    ctx.fireChannelRead(request);

                } else {

                    actionHandler.processAction(request, responseWriter, ctx);

                }
            }
        } catch (IllegalArgumentException iae) {
            logFormatter.errorLog(request, iae, "Exception processing " + request);
            // send request without API CORS headers
            responseWriter.writeResponse(request, BAD_REQUEST, iae.getMessage(), MediaType.create("text", "plain").toString());
        } catch (Exception e) {
            logFormatter.errorLog(request, e, "Exception processing " + request);
            responseWriter.writeResponse(request, response().withStatusCode(BAD_REQUEST.code()).withBody(e.getMessage()));
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (!shouldIgnoreException(cause)) {
            LoggerFactory.getLogger(this.getClass()).warn("Exception caught by " + server.getClass() + " handler -> closing pipeline " + ctx.channel(), cause);
        }
        closeOnFlush(ctx.channel());
    }
}
