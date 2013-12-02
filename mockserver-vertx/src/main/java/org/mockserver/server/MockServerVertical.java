package org.mockserver.server;

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
                            request.response().setStatusCode(HttpStatusCode.ACCEPTED_202.code());
                            request.response().setStatusMessage(HttpStatusCode.ACCEPTED_202.reasonPhrase());
                            request.response().end();
                            vertx.stop();
                        } else if (request.path().equals("/clear")) {
                            Expectation expectation = expectationSerializer.deserialize(body.getBytes());
                            mockServer.clear(expectation.getHttpRequest());
                            request.response().setStatusCode(HttpStatusCode.ACCEPTED_202.code());
                            request.response().setStatusMessage(HttpStatusCode.ACCEPTED_202.reasonPhrase());
                        } else {
                            Expectation expectation = expectationSerializer.deserialize(body.getBytes());
                            mockServer.when(expectation.getHttpRequest(), expectation.getTimes()).respond(expectation.getHttpResponse());
                            request.response().setStatusCode(HttpStatusCode.CREATED_201.code());
                            request.response().setStatusMessage(HttpStatusCode.CREATED_201.reasonPhrase());
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
                    }
                    if (!("" + request.path()).equals("/stop")) {
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

    public void start() {
        vertx.createHttpServer().requestHandler(requestHandler).listen(8080, "localhost");
    }

    @VisibleForTesting
    public Handler<HttpServerRequest> getRequestHandler() {
        return requestHandler;
    }
}
