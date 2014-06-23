package org.mockserver.integration.proxy;

import com.google.common.base.Charsets;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.Test;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.socket.SSLFactory;
import org.mockserver.streams.IOStreamUtils;

import javax.net.ssl.SSLSocket;
import java.io.OutputStream;
import java.net.Socket;

import static org.junit.Assert.assertEquals;
import static org.mockserver.test.Assert.assertContains;

/**
 * @author jamesdbloom
 */
public abstract class AbstractClientSecureProxyIntegrationTest extends AbstractClientProxyIntegrationTest {

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
                        "\r\n"
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
        HttpResponse response = httpClient.execute(
                new HttpGet(
                        new URIBuilder()
                                .setScheme("https")
                                .setHost("localhost")
                                .setPort(getServerSecurePort())
                                .setPath("/test_headers_and_body")
                                .build()
                )
        );

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
                        "GET /unknown HTTP/1.1\r\n" +
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
