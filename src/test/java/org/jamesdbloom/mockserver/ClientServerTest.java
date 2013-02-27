package org.jamesdbloom.mockserver;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.jamesdbloom.mockserver.client.MockServerClient;
import org.jamesdbloom.mockserver.model.Cookie;
import org.jamesdbloom.mockserver.model.Header;
import org.jamesdbloom.mockserver.model.HttpRequest;
import org.jamesdbloom.mockserver.model.HttpResponse;
import org.jamesdbloom.mockserver.server.EmbeddedJettyRunner;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author jamesdbloom
 */
public class ClientServerTest {

    @Test
    public void clientCanCallServer() throws Exception {
        // given
        EmbeddedJettyRunner embeddedJettyRunner = new EmbeddedJettyRunner(8080);
        MockServerClient mockServerClient = new MockServerClient("localhost", 8080);

        // when
        mockServerClient.when(new HttpRequest()).respond(new HttpResponse().withBody("somebody"));

        // then
        HttpResponse server = new HttpResponse()
                .withStatusCode(HttpStatus.OK_200)
                .withHeaders(new Header("Content-Length", "" + "somebody".length()), new Header("Server", "Jetty(9.0.0.RC0)"))
                .withBody("somebody");
        assertEquals(server, makeRequest(new HttpRequest()));
    }

    public HttpResponse makeRequest(HttpRequest httpRequest) throws Exception {
        HttpResponse httpResponse;
        HttpClient httpClient = new HttpClient();
        httpClient.start();
        Request request = httpClient.newRequest("http://localhost:8080/" + httpRequest.getPath()).method(HttpMethod.GET).content(new StringContentProvider(httpRequest.getBody()));
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
            request.header("Set-Cookie", stringBuilder.toString());
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
}
