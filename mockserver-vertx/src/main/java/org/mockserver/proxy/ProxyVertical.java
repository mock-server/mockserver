package org.mockserver.proxy;

import ch.qos.logback.classic.Level;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.mappers.VertXToMockServerRequestMapper;
import org.mockserver.mappers.MockServerToVertXResponseMapper;
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
import org.vertx.java.platform.Verticle;

/**
 * @author jamesdbloom
 */
public class ProxyVertical extends Verticle {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    // mappers
    private VertXToMockServerRequestMapper vertXToMockServerRequestMapper = new VertXToMockServerRequestMapper();
    private HttpClientRequestMapper httpClientRequestMapper = new HttpClientRequestMapper();
    private MockServerToVertXResponseMapper mockServerToVertXResponseMapper = new MockServerToVertXResponseMapper();
    private HttpClientResponseMapper httpClientResponseMapper = new HttpClientResponseMapper();
    // filters
    private Filters filters = new Filters() {{
        withFilter(new HttpRequest(), new HopByHopHeaderFilter());
        withFilter(new HttpRequest(), new LogFilter());
    }};
    // handler
    private Handler<HttpServerRequest> requestHandler = new Handler<HttpServerRequest>() {
        public void handle(final HttpServerRequest httpServerRequest) {

            // request data handler
            final Buffer requestBody = new Buffer();
            httpServerRequest.dataHandler(new Handler<Buffer>() {
                public void handle(Buffer data) {
                    requestBody.appendBuffer(data);
                }
            });

            // request end handler
            httpServerRequest.endHandler(new VoidHandler() {
                public void handle() {
                    final HttpRequest httpRequest = filters.applyFilters(vertXToMockServerRequestMapper.mapVertXRequestToMockServerRequest(httpServerRequest, requestBody.getBytes()));
                    HttpClientRequest clientRequest = vertx
                            .createHttpClient()
                            .setHost(StringUtils.substringBefore(httpServerRequest.headers().get("Host"), ":"))
                            .setPort(httpRequest.getPort())
                            .request(httpServerRequest.method(), httpServerRequest.uri(), new Handler<HttpClientResponse>() {
                                public void handle(final HttpClientResponse clientResponse) {

                                    // client response data handler
                                    final Buffer responseBody = new Buffer();
                                    clientResponse.dataHandler(new Handler<Buffer>() {
                                        public void handle(Buffer data) {
                                            responseBody.appendBuffer(data);
                                        }
                                    });

                                    // client response end handler
                                    clientResponse.endHandler(new VoidHandler() {
                                        public void handle() {
                                            HttpResponse httpResponse = httpClientResponseMapper.mapHttpClientResponseToHttpResponse(clientResponse, responseBody.getBytes());
                                            mockServerToVertXResponseMapper.mapMockServerResponseToVertXResponse(filters.applyFilters(httpRequest, httpResponse), httpServerRequest.response());
                                        }
                                    });
                                }
                            });
                    httpClientRequestMapper.mapHttpRequestToHttpClientRequest(httpRequest, clientRequest);
                }
            });
        }
    };

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
     * -Dmockserver.serverPort=<port> - override the default port (default: 8080)
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
