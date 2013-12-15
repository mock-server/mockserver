package org.mockserver.proxy;

import ch.qos.logback.classic.Level;
import com.google.common.util.concurrent.SettableFuture;
import org.mockserver.mappers.HttpServerRequestMapper;
import org.mockserver.model.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.platform.Verticle;

import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class ProxyVertical extends Verticle {
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
                    HttpRequest httpRequest = httpServerRequestMapper.createHttpRequest(request, body.getBytes());
                    System.out.println("STARTING http = " + httpRequest);
                    final HttpClient httpClient = vertx.createHttpClient();
                    final SettableFuture<String> future = SettableFuture.create();
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Handler<HttpClientResponse> responseHandler = new Handler<HttpClientResponse>() {
                                @Override
                                public void handle(final HttpClientResponse response) {
                                    final Buffer body = new Buffer(0);

                                    // The receive body
                                    response.dataHandler(new Handler<Buffer>() {
                                        public void handle(Buffer buffer) {
                                            body.appendBuffer(buffer);
                                        }
                                    });

                                    // The entire body has now been received
                                    response.endHandler(new VoidHandler() {
                                        public void handle() {
                                            request.response().headers().add(response.headers());
                                            request.response().end(body);
                                            future.set(response.statusMessage());
                                        }
                                    });
                                }
                            };
                            HttpClientRequest httpClientRequest = httpClient.request(request.method(), request.uri(), responseHandler);
                            httpClientRequest.end(body);
                        }
                    }).start();
                    try {
                        future.get(30, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        logger.error("Exception while waiting for http from proxy to final destination to return", e);
                    }
                    System.out.println("ENDING http = " + httpRequest);
                    // http.response().end();
                }
            });
        }
    };
    private HttpServerRequestMapper httpServerRequestMapper = new HttpServerRequestMapper();

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
        int port = Integer.parseInt(System.getProperty("mockserver.proxy.port", "1080"));
        ProxyVertical.overrideLogLevel(System.getProperty("mockserver.logLevel"));

        logger.info("Starting MockServer proxy listening on " + port);
        System.out.println("Starting MockServer proxy listening on " + port);

        vertx.createHttpServer().requestHandler(requestHandler).listen(port, "localhost");
    }

}
