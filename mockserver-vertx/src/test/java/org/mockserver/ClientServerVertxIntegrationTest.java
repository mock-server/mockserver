package org.mockserver;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpMethod;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.matchers.Times;
import org.mockserver.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vertx.java.platform.impl.cli.Starter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author jamesdbloom
 */
public class ClientServerVertxIntegrationTest {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private Thread vertxServer = new Thread(new Runnable() {
        public void run() {
            Starter.main(new String[]{"run", "org.mockserver.server.MockServerVertical"});
        }
    });
    private MockServerClient mockServerClient;

    @Before
    public void startServerAndCreateClient() throws IOException {
        logger.debug("Starting Vert.X server");
        vertxServer.start();
        // vertxServer = Runtime.getRuntime().exec("java org.vertx.java.platform.impl.cli.Starter run org.mockserver.server.MockServerVertical");
        mockServerClient = new MockServerClient("localhost", 8080);
    }

    @After
    public void stopServer() throws Exception {
        makeRequest(
                new HttpRequest()
                        .withMethod("PUT")
                        .withPath("/stop")
        );
        // vertxServer.destroy();
    }

    @Test
    public void clientCanCallServer() throws Exception {
        // when
        mockServerClient.when(new HttpRequest()).respond(new HttpResponse().withBody("somebody"));

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code)
                        .withHeaders(
                                new Header("Transfer-Encoding", "chunked")
                        )
                        .withBody("somebody"),
                makeRequest(new HttpRequest()));
    }

    @Test
    public void clientCanCallServerMatchPath() throws Exception {
        // when
        mockServerClient.when(new HttpRequest().withPath("/somepath1")).respond(new HttpResponse().withBody("somebody1"));
        mockServerClient.when(new HttpRequest().withPath("/somepath2")).respond(new HttpResponse().withBody("somebody2"));

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code)
                        .withHeaders(
                                new Header("Transfer-Encoding", "chunked")
                        )
                        .withBody("somebody2"),
                makeRequest(new HttpRequest().withPath("/somepath2")));
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code)
                        .withHeaders(
                                new Header("Transfer-Encoding", "chunked")
                        )
                        .withBody("somebody1"),
                makeRequest(new HttpRequest().withPath("/somepath1")));
    }

    @Test
    public void clientCanCallServerMatchPathXTimes() throws Exception {
        // when
        mockServerClient.when(new HttpRequest().withPath("/somepath"), Times.exactly(2)).respond(new HttpResponse().withBody("somebody"));

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code)
                        .withBody("somebody")
                        .withHeaders(
                                new Header("Transfer-Encoding", "chunked")
                        ),
                makeRequest(new HttpRequest().withPath("/somepath")));
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code)
                        .withHeaders(
                                new Header("Transfer-Encoding", "chunked")
                        )
                        .withBody("somebody"),
                makeRequest(new HttpRequest().withPath("/somepath")));
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code)
                        .withHeaders(
                                new Header("Content-Length", "0")
                        ),
                makeRequest(new HttpRequest().withPath("/somepath")));
    }

    @Test
    public void clientCanCallServerPositionMatchEverythingForGET() throws Exception {
        // when
        mockServerClient
                .when(
                        new HttpRequest()
                                .withMethod("GET")
                                .withPath("/somePathRequest")
                                .withBody("someBodyRequest")
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest"))
                                .withParameters(new Parameter("parameterNameRequest", "parameterValueRequest"))
                )
                .respond(
                        new HttpResponse()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code)
                                .withBody("someBodyRequest")
                                .withHeaders(new Header("headerNameResponse", "headerValueResponse"))
                                .withCookies(new Cookie("cookieNameResponse", "cookieValueResponse"))
                );

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code)
                        .withBody("someBodyRequest")
                        .withHeaders(
                                new Header("headerNameResponse", "headerValueResponse"),
                                new Header("Set-Cookie", "cookieNameResponse=cookieValueResponse"),
                                new Header("Transfer-Encoding", "chunked")
                        ),
                makeRequest(
                        new HttpRequest()
                                .withMethod("GET")
                                .withPath("/somePathRequest")
                                .withBody("someBodyRequest")
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest"))
                                .withParameters(new Parameter("parameterNameRequest", "parameterValueRequest"))
                )
        );
    }

    @Test
    public void clientCanCallServerPositionMatchParametersForPOST() throws Exception {
        // when
        mockServerClient
                .when(
                        new HttpRequest()
                                .withMethod("POST")
                                .withPath("/somepath")
                                .withBody("bodypParameterName=bodyParameterValue")
                                .withParameters(new Parameter("queryParameterName", "queryParameterValue"))
                )
                .respond(
                        new HttpResponse()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code)
                                .withBody("somebody")
                );

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code)
                        .withBody("somebody")
                        .withHeaders(
                                new Header("Transfer-Encoding", "chunked")
                        ),
                makeRequest(
                        new HttpRequest()
                                .withMethod("POST")
                                .withPath("/somepath")
                                .withBody("bodypParameterName=bodyParameterValue")
                                .withParameters(new Parameter("queryParameterName", "queryParameterValue"))
                )
        );
    }

    @Test
    public void clientCanCallServerNegativeMatchBodyOnly() throws Exception {
        // when
        mockServerClient
                .when(
                        new HttpRequest()
                                .withMethod("GET")
                                .withPath("/somepath")
                                .withBody("somebody")
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                                .withParameters(new Parameter("parameterName", "parameterValue"))
                )
                .respond(
                        new HttpResponse()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code)
                                .withBody("somebody")
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                );

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code)
                        .withHeaders(
                                new Header("Content-Length", "0")
                        ),
                makeRequest(
                        new HttpRequest()
                                .withPath("/somepath")
                                .withBody("someotherbody")
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                                .withParameters(new Parameter("parameterName", "parameterValue"))
                )
        );
    }

    private HttpResponse makeRequest(HttpRequest httpRequest) throws Exception {
        HttpResponse httpResponse;
        HttpClient httpClient = new HttpClient();
        httpClient.start();
        String queryString = buildQueryString(httpRequest.getParameters());
        if (queryString.length() > 0) {
            queryString = '?' + queryString;
        }

        Request request = httpClient.newRequest("http://localhost:8080" + (httpRequest.getPath().startsWith("/") ? "" : "/") + httpRequest.getPath() + queryString).method(HttpMethod.fromString(httpRequest.getMethod())).content(new StringContentProvider(httpRequest.getBody()));
        for (Header header : httpRequest.getHeaders()) {
            for (String value : header.getValues()) {
                request.header(header.getName(), value);
            }
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (Cookie cookie : httpRequest.getCookies()) {
            for (String value : cookie.getValues()) {
                stringBuilder.append(cookie.getName()).append("=").append(value).append("; ");
            }
        }
        if (stringBuilder.length() > 0) {
            request.header("Cookie", stringBuilder.toString());
        }
        ContentResponse contentResponse = request.send();
        httpResponse = new HttpResponse();
        httpResponse.withBody(contentResponse.getContentAsString());
        httpResponse.withStatusCode(contentResponse.getStatus());
        List<Header> headers = new ArrayList<Header>();
        for (HttpField httpField : contentResponse.getHeaders()) {
            headers.add(new Header(httpField.getName(), httpField.getValue()));
        }
        if (headers.size() > 0) {
            httpResponse.withHeaders(headers);
        }
        return httpResponse;
    }

    private String buildQueryString(List<Parameter> parameters) {
        String queryString = "";
        for (Parameter parameter : parameters) {
            for (String parameterValue : parameter.getValues()) {
                queryString += parameter.getName() + '=' + parameterValue + '&';
            }
        }
        return StringUtils.removeEnd(queryString, "&");
    }
}
