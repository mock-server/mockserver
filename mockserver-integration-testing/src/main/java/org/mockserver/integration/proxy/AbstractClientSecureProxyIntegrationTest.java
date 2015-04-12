package org.mockserver.integration.proxy;

import com.google.common.base.Charsets;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.junit.Test;
import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.socket.SSLFactory;
import org.mockserver.streams.IOStreamUtils;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.OutputStream;
import java.net.*;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.test.Assert.assertContains;
import static org.mockserver.verify.VerificationTimes.exactly;

/**
 * @author jamesdbloom
 */
public abstract class AbstractClientSecureProxyIntegrationTest {

    public abstract int getProxyPort();

    public abstract int getServerSecurePort();

    public abstract ProxyClient getProxyClient();

    @Test
    public void shouldConnectToSecurePort() throws Exception {
        Socket socket = null;
        try {
            socket = new Socket("localhost", getProxyPort());
            // given
            OutputStream output = socket.getOutputStream();

            // when
            output.write(("" +
                    "CONNECT localhost:666 HTTP/1.1\r\n" +
                    "Host: localhost:" + getServerSecurePort() + "\r\n" +
                    "\r\n"
            ).getBytes(Charsets.UTF_8));
            output.flush();

            // then
            assertContains(IOStreamUtils.readInputStreamToString(socket), "HTTP/1.1 200 OK");
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    @Test
    public void shouldForwardRequestsToSecurePortUsingSocketDirectly() throws Exception {
        Socket socket = null;
        try {
            socket = new Socket("localhost", getProxyPort());
            // given
            OutputStream output = socket.getOutputStream();

            // when
            // - send CONNECT request
            output.write(("" +
                    "CONNECT localhost:666 HTTP/1.1\r\n" +
                    "Host: localhost:" + getServerSecurePort() + "\r\n" +
                    "\r\n"
            ).getBytes(Charsets.UTF_8));
            output.flush();

            // - flush CONNECT response
            assertContains(IOStreamUtils.readInputStreamToString(socket), "HTTP/1.1 200 OK");

            // Upgrade the socket to SSL
            SSLSocket sslSocket = null;
            try {
                sslSocket = SSLFactory.getInstance().wrapSocket(socket);

                output = sslSocket.getOutputStream();

                // - send GET request for headers only
                output.write(("" +
                        "GET /test_headers_only HTTP/1.1\r\n" +
                        "Host: localhost:" + getServerSecurePort() + "\r\n" +
                        "X-Test: test_headers_only\r\n" +
                        "Connection: keep-alive\r\n" +
                        "\r\n"
                ).getBytes(Charsets.UTF_8));
                output.flush();

                // then
                assertContains(IOStreamUtils.readInputStreamToString(sslSocket), "X-Test: test_headers_only");

                // - send GET request for headers and body
                output.write(("" +
                        "GET /test_headers_and_body HTTP/1.1\r\n" +
                        "Host: localhost:" + getServerSecurePort() + "\r\n" +
                        "Content-Length: " + "an_example_body".getBytes(Charsets.UTF_8).length + "\r\n" +
                        "X-Test: test_headers_and_body\r\n" +
                        "\r\n" +
                        "an_example_body"
                ).getBytes(Charsets.UTF_8));
                output.flush();

                // then
                String response = IOStreamUtils.readInputStreamToString(sslSocket);
                assertContains(response, "X-Test: test_headers_and_body");
                assertContains(response, "an_example_body");

                // and
                getProxyClient().verify(
                        request()
                                .withMethod("GET")
                                .withPath("/test_headers_and_body")
                                .withBody("an_example_body"),
                        exactly(1)
                );
            } finally {
                if (sslSocket != null) {
                    sslSocket.close();
                }
            }
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    @Test
    public void shouldForwardRequestsToSecurePortUsingHttpClientViaHTTP_CONNECT() throws Exception {
        // given
        HttpClient httpClient = HttpClients
                .custom()
                .setSslcontext(SSLFactory.getInstance().sslContext())
                .setHostnameVerifier(new AllowAllHostnameVerifier())
                .setRoutePlanner(
                        new DefaultProxyRoutePlanner(
                                new HttpHost(
                                        System.getProperty("http.proxyHost"),
                                        Integer.parseInt(System.getProperty("http.proxyPort")
                                        )
                                )
                        )
                ).build();

        // when
        HttpPost request = new HttpPost(
                new URIBuilder()
                        .setScheme("https")
                        .setHost("localhost")
                        .setPort(getServerSecurePort())
                        .setPath("/test_headers_and_body")
                        .build()
        );
        request.setEntity(new StringEntity("an_example_body"));
        HttpResponse response = httpClient.execute(request);

        // then
        assertEquals(HttpStatusCode.OK_200.code(), response.getStatusLine().getStatusCode());
        assertEquals("an_example_body", new String(EntityUtils.toByteArray(response.getEntity()), com.google.common.base.Charsets.UTF_8));

        // and
        getProxyClient().verify(
                request()
                        .withPath("/test_headers_and_body")
                        .withBody("an_example_body"),
                exactly(1)
        );
    }

    @Test
    public void shouldForwardRequestsToSecurePortAndUnknownPath() throws Exception {
        Socket socket = null;
        try {
            socket = new Socket("localhost", getProxyPort());
            // given
            OutputStream output = socket.getOutputStream();

            // when
            // - send CONNECT request
            output.write(("" +
                    "CONNECT localhost:666 HTTP/1.1\r\n" +
                    "Host: localhost:" + getServerSecurePort() + "\r\n" +
                    "\r\n"
            ).getBytes(Charsets.UTF_8));
            output.flush();

            // - flush CONNECT response
            assertContains(IOStreamUtils.readInputStreamToString(socket), "HTTP/1.1 200 OK");

            // Upgrade the socket to SSL
            SSLSocket sslSocket = null;
            try {
                sslSocket = SSLFactory.getInstance().wrapSocket(socket);

                // - send GET request
                output = sslSocket.getOutputStream();
                output.write(("" +
                        "GET /not_found HTTP/1.1\r\n" +
                        "Host: localhost:" + getServerSecurePort() + "\r\n" +
                        "\r\n"
                ).getBytes(Charsets.UTF_8));
                output.flush();

                // then
                assertContains(IOStreamUtils.readInputStreamToString(sslSocket), "HTTP/1.1 404 Not Found");

                // and
                getProxyClient().verify(
                        request()
                                .withMethod("GET")
                                .withPath("/not_found"),
                        exactly(1)
                );
            } finally {
                if (sslSocket != null) {
                    sslSocket.close();
                }
            }
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }
}
