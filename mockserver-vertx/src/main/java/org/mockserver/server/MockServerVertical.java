package org.mockserver.server;

import ch.qos.logback.classic.Level;
import com.google.common.annotations.VisibleForTesting;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.mappers.HttpServerRequestMapper;
import org.mockserver.mappers.HttpServerResponseMapper;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.MockServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.platform.Verticle;

/**
 * @author jamesdbloom
 */
public class MockServerVertical extends Verticle {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
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
                    if (request.method().equals("PUT")) {
                        if (request.path().equals("/stop")) {
                            setStatusAndEnd(request, HttpStatusCode.ACCEPTED_202);
                            vertx.stop();
                        } else if (request.path().equals("/dumpToLog")) {
                            mockServer.dumpToLog();
                            setStatusAndEnd(request, HttpStatusCode.ACCEPTED_202);
                        } else if (request.path().equals("/reset")) {
                            mockServer.reset();
                            setStatusAndEnd(request, HttpStatusCode.ACCEPTED_202);
                        } else if (request.path().equals("/clear")) {
                            Expectation expectation = expectationSerializer.deserialize(body.getBytes());
                            mockServer.clear(expectation.getHttpRequest());
                            setStatusAndEnd(request, HttpStatusCode.ACCEPTED_202);
                        } else {
                            Expectation expectation = expectationSerializer.deserialize(body.getBytes());
                            mockServer.when(expectation.getHttpRequest(), expectation.getTimes()).respond(expectation.getHttpResponse());
                            setStatusAndEnd(request, HttpStatusCode.CREATED_201);
                        }
                    } else if (request.method().equals("GET") || request.method().equals("POST")) {
                        HttpRequest httpRequest = httpServerRequestMapper.createHttpRequest(request, body.getBytes());
                        HttpResponse httpResponse = mockServer.handle(httpRequest);
                        if (httpResponse != null) {
                            httpServerResponseMapper.mapHttpServerResponse(httpResponse, request.response());
                        } else {
                            request.response().setStatusCode(HttpStatusCode.NOT_FOUND_404.code());
                            request.response().setStatusMessage(HttpStatusCode.NOT_FOUND_404.reasonPhrase());
                        }
                        request.response().end();
                    }
                }
            });
        }
    };
    private MockServer mockServer = new MockServer();
    private HttpServerRequestMapper httpServerRequestMapper = new HttpServerRequestMapper();
    private HttpServerResponseMapper httpServerResponseMapper = new HttpServerResponseMapper();
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer();

    /**
     * Override the debug WARN logging level
     *
     * @param level the log level, which can be ALL, DEBUG, INFO, WARN, ERROR, OFF
     */
    public static void overrideLogLevel(String level) {
        ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.mockserver");
        rootLogger.setLevel(Level.toLevel(level));
    }

    /**
     * Starts the MockServer verticle using system properties to override default port and logging level
     * <p/>
     * -Dmockserver.port=<port> - override the default port (default: 8080)
     * -Dmockserver.logLevel=<level> - override the default logging level (default: WARN)
     */
    public void start() {
        int port = Integer.parseInt(System.getProperty("mockserver.port", "8080"));
        MockServerVertical.overrideLogLevel(System.getProperty("mockserver.logLevel"));

        logger.info("Starting MockServer listening on " + port);
        System.out.println("Starting MockServer listening on " + port);

        vertx.createHttpServer().requestHandler(requestHandler).listen(port, "localhost");
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
