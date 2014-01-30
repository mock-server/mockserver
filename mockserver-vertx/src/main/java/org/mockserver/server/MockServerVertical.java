package org.mockserver.server;

import ch.qos.logback.classic.Level;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.mappers.VertXToMockServerRequestMapper;
import org.mockserver.mappers.MockServerToVertXResponseMapper;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.MockServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.proxy.filters.LogFilter;
import org.mockserver.socket.SSLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.platform.Verticle;

import java.nio.charset.Charset;

/**
 * @author jamesdbloom
 */
public class MockServerVertical extends Verticle {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private static LogFilter logFilter = new LogFilter();
    private final Handler<HttpServerRequest> requestHandler = new Handler<HttpServerRequest>() {
        public void handle(final HttpServerRequest request) {
            final Buffer body = new Buffer(0);

            // The receive body
            request.dataHandler(new Handler<Buffer>() {
                public void handle(Buffer buffer) {
                    body.appendBuffer(buffer);
                }
            });

            // The entire body has now been received
            request.endHandler(new VoidHandler() {
                public void handle() {
                    try {
                        if (request.method().equals("PUT") && request.path().equals("/stop")) {

                            setStatusAndEnd(request, HttpStatusCode.ACCEPTED_202);
                            vertx.stop();

                        } else if (request.method().equals("PUT") && request.path().equals("/dumpToLog")) {

                            mockServer.dumpToLog(httpRequestSerializer.deserialize(new String(body.getBytes(), Charsets.UTF_8)));
                            setStatusAndEnd(request, HttpStatusCode.ACCEPTED_202);

                        } else if (request.method().equals("PUT") && request.path().equals("/reset")) {

                            logFilter.reset();
                            mockServer.reset();
                            setStatusAndEnd(request, HttpStatusCode.ACCEPTED_202);

                        } else if (request.method().equals("PUT") && request.path().equals("/clear")) {

                            HttpRequest httpRequest = httpRequestSerializer.deserialize(new String(body.getBytes(), Charsets.UTF_8));
                            logFilter.clear(httpRequest);
                            mockServer.clear(httpRequest);
                            setStatusAndEnd(request, HttpStatusCode.ACCEPTED_202);

                        } else if (request.method().equals("PUT") && request.path().equals("/expectation")) {

                            Expectation expectation = expectationSerializer.deserialize(new String(body.getBytes(), Charsets.UTF_8));
                            mockServer.when(expectation.getHttpRequest(), expectation.getTimes()).thenRespond(expectation.getHttpResponse());
                            setStatusAndEnd(request, HttpStatusCode.CREATED_201);

                        } else if (request.method().equals("PUT") && request.path().equals("/retrieve")) {

                            Expectation[] expectations = logFilter.retrieve(httpRequestSerializer.deserialize(new String(body.getBytes(), Charsets.UTF_8)));
                            Buffer body = new Buffer(expectationSerializer.serialize(expectations));
                            request.response().putHeader("Content-Length", "" + body.length());
                            request.response().write(body);
                            request.response().setStatusCode(HttpStatusCode.OK_200.code());
                            request.response().setStatusMessage(HttpStatusCode.OK_200.reasonPhrase());
                            request.response().end();

                        } else {

                            HttpRequest httpRequest = vertXToMockServerRequestMapper.mapVertXRequestToMockServerRequest(request, body.getBytes());
                            HttpResponse httpResponse = mockServer.handle(httpRequest);
                            logFilter.onResponse(httpRequest, httpResponse);
                            if (httpResponse != null) {
                                mockServerToVertXResponseMapper.mapMockServerResponseToVertXResponse(httpResponse, request.response());
                            } else {
                                request.response().setStatusCode(HttpStatusCode.NOT_FOUND_404.code());
                                request.response().setStatusMessage(HttpStatusCode.NOT_FOUND_404.reasonPhrase());
                                request.response().end();
                            }

                        }
                    } catch (Exception e) {
                        handleException(e, request);
                    }
                }
            });
        }
    };
    private MockServer mockServer = new MockServer();
    private VertXToMockServerRequestMapper vertXToMockServerRequestMapper = new VertXToMockServerRequestMapper();
    private MockServerToVertXResponseMapper mockServerToVertXResponseMapper = new MockServerToVertXResponseMapper();
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer();
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();

    /**
     * Override the debug WARN logging level
     *
     * @param level the log level, which can be ALL, DEBUG, INFO, WARN, ERROR, OFF
     */
    public static void overrideLogLevel(String level) {
        ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.mockserver");
        rootLogger.setLevel(Level.toLevel(level));
    }

    private void handleException(Exception e, HttpServerRequest request) {
        request.response().setChunked(false);
        Buffer body = new Buffer(e.toString());
        request.response().putHeader("Content-Length", "" + body.length());
        request.response().write(body);
        request.response().setStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR_500.code());
        request.response().setStatusMessage(HttpStatusCode.INTERNAL_SERVER_ERROR_500.reasonPhrase());
        request.response().end();
    }

    /**
     * Starts the MockServer verticle using system properties to override default port and logging level
     * <p/>
     * -Dmockserver.serverPort=<port> - override the default port
     * -Dmockserver.logLevel=<level> - override the default logging level
     */
    public void start() {
        int port = Integer.parseInt(System.getProperty("mockserver.serverPort", "-1"));
        int securePort = Integer.parseInt(System.getProperty("mockserver.serverSecurePort", "-1"));
        MockServerVertical.overrideLogLevel(System.getProperty("mockserver.logLevel"));

        String startedMessage = "Started " + this.getClass().getSimpleName().replace("Vertical", "") + " listening on:";
        if (port != -1) {
            startedMessage += " standard port " + port;
            vertx.createHttpServer().requestHandler(requestHandler).listen(port, "localhost");
        }
        if (securePort != -1) {
            startedMessage += " secure port " + securePort;
            SSLFactory.buildKeyStore();
            vertx.createHttpServer().requestHandler(requestHandler).setSSL(true).setKeyStorePath(SSLFactory.KEY_STORE_FILENAME).setKeyStorePassword(SSLFactory.KEY_STORE_PASSWORD).listen(securePort, "localhost");
        }

        logger.info(startedMessage);
        System.out.println(startedMessage);
    }

    private void setStatusAndEnd(HttpServerRequest request, HttpStatusCode httpStatusCode) {
        request.response().setStatusCode(httpStatusCode.code());
        request.response().setStatusMessage(httpStatusCode.reasonPhrase());
        request.response().end();
    }

    @VisibleForTesting
    public Handler<HttpServerRequest> getRequestHandler() {
        return requestHandler;
    }
}
