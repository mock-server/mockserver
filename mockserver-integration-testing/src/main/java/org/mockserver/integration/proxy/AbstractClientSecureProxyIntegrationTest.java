package org.mockserver.integration.proxy;

import com.google.common.base.Charsets;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;
import org.apache.http.util.EntityUtils;
import org.junit.Test;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.socket.SSLFactory;
import org.mockserver.streams.IOStreamUtils;

import javax.net.ssl.SSLSocket;
import java.io.OutputStream;
import java.net.ProxySelector;
import java.net.Socket;

import static org.junit.Assert.assertEquals;
import static org.mockserver.test.Assert.assertContains;

/**
 * @author jamesdbloom
 */
public abstract class AbstractClientSecureProxyIntegrationTest {

    protected static String servletContext = "";

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

    public abstract int getServerSecurePort();

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
    public void shouldForwardRequestsToSecurePortUsingHttpClient() throws Exception {
        // given
        HttpClient httpClient = createHttpClient();

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
