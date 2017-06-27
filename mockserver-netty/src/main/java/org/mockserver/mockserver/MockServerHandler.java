package org.mockserver.mockserver;

import com.google.common.net.MediaType;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import org.mockserver.client.serialization.*;
import org.mockserver.file.FileReader;
import org.mockserver.filters.RequestLogFilter;
import org.mockserver.logging.LogFormatter;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.mock.action.ActionHandler;
import org.mockserver.mockserver.callback.ExpectationCallbackResponse;
import org.mockserver.mockserver.callback.WebSocketClientRegistry;
import org.mockserver.model.*;
import org.mockserver.socket.KeyAndCertificateFactory;
import org.mockserver.validator.JsonSchemaValidator;
import org.mockserver.validator.Validator;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.BindException;
import java.util.ArrayList;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpHeaderValues.CLOSE;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.configuration.ConfigurationProperties.enableCORSForAPI;
import static org.mockserver.configuration.ConfigurationProperties.enableCORSForAllResponses;
import static org.mockserver.model.ConnectionOptions.connectionOptions;
import static org.mockserver.model.ConnectionOptions.isFalseOrNull;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.PortBinding.portBinding;

@ChannelHandler.Sharable
public class MockServerHandler extends SimpleChannelInboundHandler<HttpRequest> {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private LogFormatter logFormatter = new LogFormatter(logger);
    // mockserver
    private MockServer server;
    private RequestLogFilter requestLogFilter;
    private MockServerMatcher mockServerMatcher;
    private WebSocketClientRegistry webSocketClientRegistry;
    private ActionHandler actionHandler;
    // serializers
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer();
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();
    private PortBindingSerializer portBindingSerializer = new PortBindingSerializer();
    private VerificationSerializer verificationSerializer = new VerificationSerializer();
    private VerificationSequenceSerializer verificationSequenceSerializer = new VerificationSequenceSerializer();
    // validators
    private Validator<String> expectationValidator = new JsonSchemaValidator(FileReader.readFileFromClassPathOrPath("org/mockserver/model/schema/expectationsFullModel.json"));

    public MockServerHandler(MockServer server, MockServerMatcher mockServerMatcher, WebSocketClientRegistry webSocketClientRegistry, RequestLogFilter requestLogFilter) {
        this.server = server;
        this.requestLogFilter = requestLogFilter;
        this.mockServerMatcher = mockServerMatcher;
        this.webSocketClientRegistry = webSocketClientRegistry;
        actionHandler = new ActionHandler(requestLogFilter);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final HttpRequest request) {

        try {

            if ((enableCORSForAPI() || enableCORSForAllResponses()) && request.getMethod().getValue().equals("OPTIONS") && !request.getFirstHeader("Origin").isEmpty()) {

                writeResponse(ctx, request, OK);

            } else if (request.matches("PUT", "/status")) {

                List<Integer> actualPortBindings = server.getPorts();
                writeResponse(ctx, request, OK, portBindingSerializer.serialize(portBinding(actualPortBindings)), "application/json");

            } else if (request.matches("PUT", "/bind")) {

                PortBinding requestedPortBindings = portBindingSerializer.deserialize(request.getBodyAsString());
                try {
                    List<Integer> actualPortBindings = server.bindToPorts(requestedPortBindings.getPorts());
                    writeResponse(ctx, request, OK, portBindingSerializer.serialize(portBinding(actualPortBindings)), "application/json");
                } catch (RuntimeException e) {
                    if (e.getCause() instanceof BindException) {
                        writeResponse(ctx, request, NOT_ACCEPTABLE, e.getMessage() + " port already in use", MediaType.create("text", "plain").toString());
                    } else {
                        throw e;
                    }
                }

            } else if (request.matches("PUT", "/expectation")) {

                final List<String> strings = expectationSerializer.returnJSONObjects(request.getBodyAsString());
                if (strings.isEmpty()) {
                    writeResponse(ctx, request, NOT_ACCEPTABLE, "1 error:\n - an expectation or array of expectations is required", MediaType.create("text", "plain").toString());
                } else {
                    List<String> validationErrorsList = new ArrayList<String>();
                    for (String expectationJson : strings) {
                        String validationErrors = expectationValidator.isValid(expectationJson);
                        if (validationErrors.isEmpty()) {
                            Expectation expectation = expectationSerializer.deserialize(expectationJson);
                            KeyAndCertificateFactory.addSubjectAlternativeName(expectation.getHttpRequest().getFirstHeader(HOST.toString()));
                            mockServerMatcher
                                    .when(expectation.getHttpRequest(), expectation.getTimes(), expectation.getTimeToLive())
                                    .thenRespond(expectation.getHttpResponse())
                                    .thenForward(expectation.getHttpForward())
                                    .thenError(expectation.getHttpError())
                                    .thenCallback(expectation.getHttpClassCallback())
                                    .thenCallback(expectation.getHttpObjectCallback());
                            logFormatter.infoLog("creating expectation:{}", expectation);
                            writeResponse(ctx, request, CREATED);
                        } else {
                            validationErrorsList.add(validationErrors);
                        }
                    }
                    writeResponse(ctx, request, NOT_ACCEPTABLE,
                            (validationErrorsList.size() > 1 ? "[" : "") + Joiner.on(",\n").join(validationErrorsList) + (validationErrorsList.size() > 1 ? "]" : ""),
                            MediaType.create("text", "plain").toString());
                }

            } else if (request.matches("PUT", "/clear")) {

                org.mockserver.model.HttpRequest httpRequest = httpRequestSerializer.deserialize(request.getBodyAsString());
                if (request.hasQueryStringParameter("type", "expectation")) {
                    logFormatter.infoLog("clearing expectations that match:{}", httpRequest);
                    mockServerMatcher.clear(httpRequest);
                } else if (request.hasQueryStringParameter("type", "log")) {
                    logFormatter.infoLog("clearing request logs that match:{}", httpRequest);
                    requestLogFilter.clear(httpRequest);
                } else {
                    logFormatter.infoLog("clearing expectations and request logs that match:{}", httpRequest);
                    requestLogFilter.clear(httpRequest);
                    mockServerMatcher.clear(httpRequest);
                }
                logFormatter.infoLog("clearing expectations and request logs that match:{}", httpRequest);
                writeResponse(ctx, request, OK);

            } else if (request.matches("PUT", "/reset")) {

                requestLogFilter.reset();
                mockServerMatcher.reset();
                logFormatter.infoLog("resetting all expectations and request logs");
                writeResponse(ctx, request, OK);

            } else if (request.matches("PUT", "/dumpToLog")) {

                mockServerMatcher.dumpToLog(httpRequestSerializer.deserialize(request.getBodyAsString()));
                writeResponse(ctx, request, OK);

            } else if (request.matches("PUT", "/retrieve")) {

                HttpRequest httpRequest = httpRequestSerializer.deserialize(request.getBodyAsString());
                if (request.hasQueryStringParameter("type", "expectation")) {
                    Expectation[] expectations = mockServerMatcher.retrieveExpectations(httpRequest);
                    logFormatter.infoLog("retrieving expectations that match:{}", httpRequest);
                    writeResponse(ctx, request, OK, expectationSerializer.serialize(expectations), "application/json");
                } else {
                    HttpRequest[] requests = requestLogFilter.retrieve(httpRequest);
                    logFormatter.infoLog("retrieving requests that match:{}", httpRequest);
                    writeResponse(ctx, request, OK, httpRequestSerializer.serialize(requests), "application/json");
                }

            } else if (request.matches("PUT", "/verify")) {

                Verification verification = verificationSerializer.deserialize(request.getBodyAsString());
                String result = requestLogFilter.verify(verification);
                logFormatter.infoLog("verifying requests that match:{}", verification);
                if (result.isEmpty()) {
                    writeResponse(ctx, request, ACCEPTED);
                } else {
                    writeResponse(ctx, request, NOT_ACCEPTABLE, result, MediaType.create("text", "plain").toString());
                }

            } else if (request.matches("PUT", "/verifySequence")) {

                VerificationSequence verificationSequence = verificationSequenceSerializer.deserialize(request.getBodyAsString());
                String result = requestLogFilter.verify(verificationSequence);
                logFormatter.infoLog("verifying sequence that match:{}", verificationSequence);
                if (result.isEmpty()) {
                    writeResponse(ctx, request, ACCEPTED);
                } else {
                    writeResponse(ctx, request, NOT_ACCEPTABLE, result, MediaType.create("text", "plain").toString());
                }

            } else if (request.matches("PUT", "/stop")) {

                ctx.writeAndFlush(response().withStatusCode(OK.code()));
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        server.stop();
                    }
                }).start();

            } else {

                Action handle = mockServerMatcher.retrieveAction(request);
                if (handle instanceof HttpError) {
                    HttpError httpError = ((HttpError) handle).applyDelay();
                    if (httpError.getResponseBytes() != null) {
                        // write byte directly by skipping over HTTP codec
                        ChannelHandlerContext httpCodecContext = ctx.pipeline().context(HttpServerCodec.class);
                        if (httpCodecContext != null) {
                            httpCodecContext.writeAndFlush(Unpooled.wrappedBuffer(httpError.getResponseBytes())).awaitUninterruptibly();
                        }
                    }
                    if (httpError.getDropConnection()) {
                        ctx.close();
                    }
                } else if (handle instanceof HttpObjectCallback) {
                    String clientId = ((HttpObjectCallback) handle).getClientId();
                    webSocketClientRegistry.registerCallbackResponseHandler(clientId, new ExpectationCallbackResponse() {
                        @Override
                        public void handle(HttpResponse response) {
                            logFormatter.infoLog("returning response:{}" + NEW_LINE + " for request:{}", response, request);
                            writeResponse(ctx, request, response.withConnectionOptions(connectionOptions().withCloseSocket(true)));
                        }
                    });
                    webSocketClientRegistry.sendClientMessage(clientId, request);
                } else {
                    HttpResponse response = actionHandler.processAction(handle, request);
                    logFormatter.infoLog("returning response:{}" + NEW_LINE + " for request:{}", response, request);
                    writeResponse(ctx, request, response);
                }

            }
        } catch (Exception e) {
            logger.error("Exception processing " + request, e);
            writeResponse(ctx, request, response().withStatusCode(BAD_REQUEST.code()).withBody(e.getMessage()));
        }

    }

    private void writeResponse(ChannelHandlerContext ctx, HttpRequest request, HttpResponseStatus responseStatus) {
        writeResponse(ctx, request, responseStatus, "", "application/json");
    }

    private void writeResponse(ChannelHandlerContext ctx, HttpRequest request, HttpResponseStatus responseStatus, String body, String contentType) {
        HttpResponse response = response()
                .withStatusCode(responseStatus.code())
                .withBody(body);
        if (body != null && !body.isEmpty()) {
            response.updateHeader(header(CONTENT_TYPE.toString(), contentType + "; charset=utf-8"));
        }
        if (enableCORSForAPI()) {
            addCORSHeaders(response);
        }
        writeResponse(ctx, request, response);
    }

    private void writeResponse(ChannelHandlerContext ctx, HttpRequest request, HttpResponse response) {
        if (response == null) {
            response = notFoundResponse();
        }
        if (enableCORSForAllResponses()) {
            addCORSHeaders(response);
        }

        addConnectionHeader(request, response);

        writeAndCloseSocket(ctx, request, response);
    }

    private void addCORSHeaders(HttpResponse response) {
        String methods = "CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, TRACE";
        String headers = "Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary";
        if (response.getFirstHeader("Access-Control-Allow-Origin").isEmpty()) {
            response.withHeader("Access-Control-Allow-Origin", "*");
        }
        if (response.getFirstHeader("Access-Control-Allow-Methods").isEmpty()) {
            response.withHeader("Access-Control-Allow-Methods", methods);
        }
        if (response.getFirstHeader("Access-Control-Allow-Headers").isEmpty()) {
            response.withHeader("Access-Control-Allow-Headers", headers);
        }
        if (response.getFirstHeader("Access-Control-Expose-Headers").isEmpty()) {
            response.withHeader("Access-Control-Expose-Headers", headers);
        }
        if (response.getFirstHeader("Access-Control-Max-Age").isEmpty()) {
            response.withHeader("Access-Control-Max-Age", "1");
        }
        if (response.getFirstHeader("X-CORS").isEmpty()) {
            response.withHeader("X-CORS", "MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORSForAPI(false) or -Dmockserver.disableCORS=false");
        }
    }

    private void addConnectionHeader(HttpRequest request, HttpResponse response) {
        ConnectionOptions connectionOptions = response.getConnectionOptions();
        if (connectionOptions != null && connectionOptions.getKeepAliveOverride() != null) {
            if (connectionOptions.getKeepAliveOverride()) {
                response.updateHeader(header(CONNECTION.toString(), KEEP_ALIVE.toString()));
            } else {
                response.updateHeader(header(CONNECTION.toString(), CLOSE.toString()));
            }
        } else if (connectionOptions == null || isFalseOrNull(connectionOptions.getSuppressConnectionHeader())) {
            if (request.isKeepAlive() != null && request.isKeepAlive()
                    && (connectionOptions == null || isFalseOrNull(connectionOptions.getCloseSocket()))) {
                response.updateHeader(header(CONNECTION.toString(), KEEP_ALIVE.toString()));
            } else {
                response.updateHeader(header(CONNECTION.toString(), CLOSE.toString()));
            }
        }
    }

    private void writeAndCloseSocket(ChannelHandlerContext ctx, HttpRequest request, HttpResponse response) {
        boolean closeChannel;

        ConnectionOptions connectionOptions = response.getConnectionOptions();
        if (connectionOptions != null && connectionOptions.getCloseSocket() != null) {
            closeChannel = connectionOptions.getCloseSocket();
        } else {
            closeChannel = !(request.isKeepAlive() != null && request.isKeepAlive());
        }

        if (closeChannel) {
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        } else {
            ctx.write(response);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (!cause.getMessage().contains("Connection reset by peer")) {
            logger.warn("Exception caught by MockServer handler -> closing pipeline", cause);
        }
        ctx.close();
    }
}
