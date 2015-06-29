package org.mockserver;

import org.junit.Test;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.model.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockserver.model.OutboundHttpRequest.outboundRequest;

/**
 * @author jamesdbloom
 */
public class InitializerMavenPluginTest {

    private final static int SERVER_HTTP_PORT = 1082;
    private final static int SERVER_HTTPS_PORT = 1083;
    // http client
    private NettyHttpClient httpClient = new NettyHttpClient();

    @Test
    public void clientCanCallServer() {
        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("test_initializer_response_body"),
                makeRequest(
                        new HttpRequest()
                                .withMethod("POST")
                                .withPath("/test_initializer_path")
                                .withBody("test_initializer_request_body"),
                        headersToIgnore
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("test_initializer_response_body"),
                makeRequest(
                        new HttpRequest()
                                .withMethod("POST")
                                .withPath("/test_initializer_path")
                                .withBody("test_initializer_request_body"),
                        headersToIgnore
                )
        );
    }

    protected List<String> headersToIgnore = Arrays.asList(
            "server",
            "expires",
            "date",
            "host",
            "connection",
            "user-agent",
            "content-type",
            "content-length",
            "accept-encoding",
            "transfer-encoding"
    );

    protected HttpResponse makeRequest(HttpRequest httpRequest, Collection<String> headersToIgnore) {
        boolean isSsl = httpRequest.isSecure() != null && httpRequest.isSecure();
        int port = (isSsl ? SERVER_HTTPS_PORT : SERVER_HTTP_PORT);
        HttpResponse httpResponse = httpClient.sendRequest(outboundRequest("localhost", port, "", httpRequest));
        List<Header> headers = new ArrayList<Header>();
        for (Header header : httpResponse.getHeaders()) {
            if (!headersToIgnore.contains(header.getName().getValue().toLowerCase())) {
                headers.add(header);
            }
        }
        httpResponse.withHeaders(headers);
        return httpResponse;
    }

}
