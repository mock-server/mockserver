package org.mockserver.integration.proxy;

import com.google.common.base.Charsets;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;
import org.apache.http.util.EntityUtils;
import org.junit.Test;
import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.socket.SSLFactory;
import org.mockserver.streams.IOStreamUtils;

import java.io.OutputStream;
import java.net.ProxySelector;
import java.net.Socket;

import static org.junit.Assert.assertEquals;
import static org.mockserver.client.proxy.Times.atLeast;
import static org.mockserver.client.proxy.Times.exactly;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.test.Assert.assertContains;

/**
 * @author jamesdbloom
 */
public abstract class AbstractClientProxyIntegrationTest {

    protected HttpClient createHttpClient() throws Exception {
        HttpClientBuilder httpClientBuilder = HttpClients
                .custom()
                .setSslcontext(SSLFactory.getInstance().sslContext())
                .setHostnameVerifier(new AllowAllHostnameVerifier());
        if (Boolean.parseBoolean(System.getProperty("defaultProxySet"))) {
            httpClientBuilder.setRoutePlanner(new SystemDefaultRoutePlanner(ProxySelector.getDefault())).build();
        } else if (Boolean.parseBoolean(System.getProperty("proxySet"))) {
            HttpHost httpHost = new HttpHost(System.getProperty("http.proxyHost"), Integer.parseInt(System.getProperty("http.proxyPort")));
            DefaultProxyRoutePlanner defaultProxyRoutePlanner = new DefaultProxyRoutePlanner(httpHost);
            httpClientBuilder.setRoutePlanner(defaultProxyRoutePlanner).build();
        } else {
            HttpHost httpHost = new HttpHost("localhost", getProxyPort());
            DefaultProxyRoutePlanner defaultProxyRoutePlanner = new DefaultProxyRoutePlanner(httpHost);
            httpClientBuilder.setRoutePlanner(defaultProxyRoutePlanner);
        }
        return httpClientBuilder.build();
    }

    public abstract int getProxyPort();

    public abstract int getServerPort();

    public abstract int getServerSecurePort();

    @Test
    public void shouldForwardRequestsUsingSocketDirectly() throws Exception {
        Socket socket = null;
        try {
            socket = new Socket("localhost", getProxyPort());

            // given
            OutputStream output = socket.getOutputStream();

            // when
            // - send GET request for headers only
            output.write(("" +
                    "GET /test_headers_only HTTP/1.1\r\n" +
                    "Host: localhost:" + getServerPort() + "\r\n" +
                    "\r\n"
            ).getBytes(Charsets.UTF_8));
            output.flush();

            // then
            assertContains(IOStreamUtils.readInputStreamToString(socket), "X-Test: test_headers_only");

            // - send GET request for headers and body
            output.write(("" +
                    "GET /test_headers_and_body HTTP/1.1\r\n" +
                    "Host: localhost:" + getServerPort() + "\r\n" +
                    "\r\n"
            ).getBytes(Charsets.UTF_8));
            output.flush();

            // then
            String response = IOStreamUtils.readInputStreamToString(socket);
            assertContains(response, "X-Test: test_headers_and_body");
            assertContains(response, "an_example_body");
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    @Test
    public void shouldForwardRequestsUsingHttpClient() throws Exception {
        // given
        HttpClient httpClient = createHttpClient();

        // when
        HttpResponse response = httpClient.execute(
                new HttpGet(
                        new URIBuilder()
                                .setScheme("http")
                                .setHost("localhost")
                                .setPort(getServerPort())
                                .setPath("/test_headers_and_body")
                                .build()
                )
        );

        // then
        assertEquals(HttpStatusCode.OK_200.code(), response.getStatusLine().getStatusCode());
        assertEquals("an_example_body", new String(EntityUtils.toByteArray(response.getEntity()), com.google.common.base.Charsets.UTF_8));
    }

    @Test
    public void shouldForwardRequestsToUnknownPath() throws Exception {
        Socket socket = null;
        try {
            socket = new Socket("localhost", getProxyPort());
            // given
            OutputStream output = socket.getOutputStream();

            // when
            // - send GET request
            output.write(("" +
                    "GET /unknown HTTP/1.1\r\n" +
                    "Host: localhost:" + getServerPort() + "\r\n" +
                    "Connection: close\r\n" +
                    "\r\n"
            ).getBytes(Charsets.UTF_8));
            output.flush();

            // then
            assertContains(IOStreamUtils.readInputStreamToString(socket), "HTTP/1.1 404 Not Found");
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    @Test
    public void shouldVerifyRequests() throws Exception {
        // given
        HttpClient httpClient = createHttpClient();
        ProxyClient proxyClient = new ProxyClient("127.0.0.1", getProxyPort()).reset();

        // when
        httpClient.execute(
                new HttpGet(
                        new URIBuilder()
                                .setScheme("http")
                                .setHost("localhost")
                                .setPort(getServerPort())
                                .setPath("/test_headers_and_body")
                                .build()
                )
        );
        httpClient.execute(
                new HttpGet(
                        new URIBuilder()
                                .setScheme("http")
                                .setHost("localhost")
                                .setPort(getServerPort())
                                .setPath("/test_headers_only")
                                .build()
                )
        );

        // then
        proxyClient
                .verify(
                        request()
                                .withMethod("GET")
                                .withPath("/test_headers_and_body")
                );
        proxyClient
                .verify(
                        request()
                                .withMethod("GET")
                                .withPath("/test_headers_and_body"),
                        exactly(1)
                );
        proxyClient
                .verify(
                        request()
                                .withPath("/test_headers_.*"),
                        atLeast(1)
                );
        proxyClient
                .verify(
                        request()
                                .withPath("/test_headers_.*"),
                        exactly(2)
                );
        proxyClient
                .verify(
                        request()
                                .withPath("/other_path"),
                        exactly(0)
                );
    }

    @Test(expected = AssertionError.class)
    public void shouldVerifyZeroRequests() throws Exception {
        // given
        HttpClient httpClient = createHttpClient();
        ProxyClient proxyClient = new ProxyClient("127.0.0.1", getProxyPort()).reset();

        // when
        httpClient.execute(
                new HttpGet(
                        new URIBuilder()
                                .setScheme("http")
                                .setHost("localhost")
                                .setPort(getServerPort())
                                .setPath("/test_headers_and_body")
                                .build()
                )
        );

        // then
        proxyClient
                .verify(
                        request()
                                .withMethod("GET")
                                .withPath("/test_headers_and_body"),
                        exactly(0)
                );
    }

    @Test(expected = AssertionError.class)
    public void shouldVerifyNoRequestsExactly() throws Exception {
        // given
        HttpClient httpClient = createHttpClient();
        ProxyClient proxyClient = new ProxyClient("127.0.0.1", getProxyPort()).reset();

        // when
        httpClient.execute(
                new HttpGet(
                        new URIBuilder()
                                .setScheme("http")
                                .setHost("localhost")
                                .setPort(getServerPort())
                                .setPath("/test_headers_and_body")
                                .build()
                )
        );

        // then
        proxyClient
                .verify(
                        request()
                                .withMethod("GET")
                                .withPath("/other_path"),
                        exactly(1)
                );
    }

    @Test(expected = AssertionError.class)
    public void shouldVerifyNoRequestsTimesNotSpecified() throws Exception {
        // given
        HttpClient httpClient = createHttpClient();
        ProxyClient proxyClient = new ProxyClient("127.0.0.1", getProxyPort()).reset();

        // when
        httpClient.execute(
                new HttpGet(
                        new URIBuilder()
                                .setScheme("http")
                                .setHost("localhost")
                                .setPort(getServerPort())
                                .setPath("/test_headers_and_body")
                                .build()
                )
        );

        // then
        proxyClient
                .verify(
                        request()
                                .withMethod("GET")
                                .withPath("/other_path")
                );
    }

    @Test(expected = AssertionError.class)
    public void shouldVerifyNotEnoughRequests() throws Exception {
        // given
        HttpClient httpClient = createHttpClient();
        ProxyClient proxyClient = new ProxyClient("127.0.0.1", getProxyPort()).reset();

        // when
        httpClient.execute(
                new HttpGet(
                        new URIBuilder()
                                .setScheme("http")
                                .setHost("localhost")
                                .setPort(getServerPort())
                                .setPath("/test_headers_and_body")
                                .build()
                )
        );
        httpClient.execute(
                new HttpGet(
                        new URIBuilder()
                                .setScheme("http")
                                .setHost("localhost")
                                .setPort(getServerPort())
                                .setPath("/test_headers_and_body")
                                .build()
                )
        );

        // then
        proxyClient
                .verify(
                        request()
                                .withPath("/test_headers_and_body"),
                        atLeast(3)
                );
    }

    @Test
    public void shouldClearRequests() throws Exception {
        // given
        HttpClient httpClient = createHttpClient();
        ProxyClient proxyClient = new ProxyClient("127.0.0.1", getProxyPort()).reset();

        // when
        httpClient.execute(
                new HttpGet(
                        new URIBuilder()
                                .setScheme("http")
                                .setHost("localhost")
                                .setPort(getServerPort())
                                .setPath("/test_headers_and_body")
                                .build()
                )
        );
        httpClient.execute(
                new HttpGet(
                        new URIBuilder()
                                .setScheme("http")
                                .setHost("localhost")
                                .setPort(getServerPort())
                                .setPath("/test_headers_only")
                                .build()
                )
        );
        proxyClient.clear(
                request()
                        .withMethod("GET")
                        .withPath("/test_headers_and_body")
        );

        // then
        proxyClient
                .verify(
                        request()
                                .withMethod("GET")
                                .withPath("/test_headers_and_body"),
                        exactly(0)
                );
        proxyClient
                .verify(
                        request()
                                .withPath("/test_headers_.*"),
                        exactly(1)
                );
    }

    @Test
    public void shouldResetRequests() throws Exception {
        // given
        HttpClient httpClient = createHttpClient();
        ProxyClient proxyClient = new ProxyClient("127.0.0.1", getProxyPort()).reset();

        // when
        httpClient.execute(
                new HttpGet(
                        new URIBuilder()
                                .setScheme("http")
                                .setHost("localhost")
                                .setPort(getServerPort())
                                .setPath("/test_headers_and_body")
                                .build()
                )
        );
        httpClient.execute(
                new HttpGet(
                        new URIBuilder()
                                .setScheme("http")
                                .setHost("localhost")
                                .setPort(getServerPort())
                                .setPath("/test_headers_only")
                                .build()
                )
        );
        proxyClient.reset();

        // then
        proxyClient
                .verify(
                        request()
                                .withMethod("GET")
                                .withPath("/test_headers_and_body"),
                        exactly(0)
                );
        proxyClient
                .verify(
                        request()
                                .withPath("/test_headers_.*"),
                        atLeast(0)
                );
        proxyClient
                .verify(
                        request()
                                .withPath("/test_headers_.*"),
                        exactly(0)
                );
    }
}
