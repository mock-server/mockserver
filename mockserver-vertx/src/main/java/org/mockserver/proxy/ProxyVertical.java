package org.mockserver.proxy;

import ch.qos.logback.classic.Level;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.mappers.HttpServerRequestMapper;
import org.mockserver.mappers.HttpServerResponseMapper;
import org.mockserver.mappers.vertx.HttpClientRequestMapper;
import org.mockserver.mappers.vertx.HttpClientResponseMapper;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.proxy.filters.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.HttpServerResponse;
import org.vertx.java.platform.Verticle;

/**
 * @author jamesdbloom
 */
public class ProxyVertical extends Verticle {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private HttpClientRequestMapper httpClientRequestMapper = new HttpClientRequestMapper();
    private HttpClientResponseMapper httpClientResponseMapper = new HttpClientResponseMapper();
    private Filters filters = new Filters();
    private Handler<HttpServerRequest> requestHandler = new Handler<HttpServerRequest>() {
        public void handle(final HttpServerRequest request) {
            System.out.println("Proxying request: " + request.uri());
            final HttpServerResponse response = request.response();

            final Buffer requestBody = new Buffer();
            request.dataHandler(new Handler<Buffer>() {
                public void handle(Buffer data) {
                    requestBody.appendBuffer(data);
                    System.out.println("Proxying request body:" + data);
                }
            });

            // The entire body has now been received
            request.endHandler(new VoidHandler() {
                public void handle() {
                    final HttpRequest httpRequest = new HttpServerRequestMapper().mapHttpServerRequestToHttpRequest(request, requestBody.getBytes());
                    filters.applyFilters(httpRequest);
                    HttpClientRequest clientRequest = vertx
                            .createHttpClient()
                            .setHost(StringUtils.substringBefore(request.headers().get("Host"), ":"))
                            .setPort(httpRequest.getPort())
                            .request(request.method(), request.uri(), new Handler<HttpClientResponse>() {
                                public void handle(final HttpClientResponse clientResponse) {
                                    System.out.println("Proxying response: " + clientResponse.statusCode());

                                    final Buffer responseBody = new Buffer();
                                    clientResponse.dataHandler(new Handler<Buffer>() {
                                        public void handle(Buffer data) {
                                            responseBody.appendBuffer(data);
                                            System.out.println("Proxying response body:" + data);
                                        }
                                    });
                                    clientResponse.endHandler(new VoidHandler() {
                                        public void handle() {
                                            HttpResponse httpResponse = httpClientResponseMapper.mapHttpServerResponseToHttpResponse(clientResponse, responseBody.getBytes());
                                            filters.applyFilters(httpRequest, httpResponse);
                                            new HttpServerResponseMapper().mapHttpResponseToHttpServerResponse(httpResponse, response);
                                            System.out.println("end of the response");
                                        }
                                    });
                                }
                            });
                    httpClientRequestMapper.mapHttpRequestToHttpClientRequest(httpRequest, clientRequest);
                    System.out.println("end of the request");
                }
            });
        }
    };

    public ProxyVertical() {
        withFilter(new HttpRequest(), new HopByHopHeaderFilter());
        withFilter(new HttpRequest(), new LogFilter());
    }

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
     * Add filter for HTTP requests, each filter get called before each request is proxied, if the filter return null then the request is not proxied
     *
     * @param httpRequest the request to match against for this filter
     * @param filter the filter to execute for this request, if the filter returns null the request will not be proxied
     */
    public ProxyVertical withFilter(HttpRequest httpRequest, ProxyRequestFilter filter) {
        filters.withFilter(httpRequest, filter);
        return this;
    }

    /**
     * Add filter for HTTP response, each filter get called after each request has been proxied
     *
     * @param httpRequest the request to match against for this filter
     * @param filter the filter that is executed after this request has been proxied
     */
    public ProxyVertical withFilter(HttpRequest httpRequest, ProxyResponseFilter filter) {
        filters.withFilter(httpRequest, filter);
        return this;
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

    @VisibleForTesting
    public Handler<HttpServerRequest> getRequestHandler() {
        return requestHandler;
    }

}
