package org.mockserver.netty.integration.proxy.http;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.RandomUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.util.EntityUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.testing.integration.proxy.AbstractClientProxyIntegrationTest;
import org.mockserver.netty.MockServer;
import org.mockserver.model.HttpStatusCode;

import static org.junit.Assert.assertEquals;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.stop.Stop.stopQuietly;
import static org.mockserver.verify.VerificationTimes.exactly;

/**
 * @author jamesdbloom
 */
public class NettyHttpProxyIntegrationTest extends AbstractClientProxyIntegrationTest {

    private static int mockServerPort;
    private static EchoServer echoServer;
    private static MockServerClient mockServerClient;

    @BeforeClass
    public static void setupFixture() {
        servletContext = "";

        mockServerPort = new MockServer().getLocalPort();
        mockServerClient = new MockServerClient("localhost", mockServerPort);

        echoServer = new EchoServer(false);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(echoServer);
        stopQuietly(mockServerClient);
    }

    @Before
    public void resetProxy() {
        mockServerClient.reset();
    }

    @Override
    public int getProxyPort() {
        return mockServerPort;
    }

    @Override
    public int getSecureProxyPort() {
        return mockServerPort;
    }

    @Override
    public MockServerClient getMockServerClient() {
        return mockServerClient;
    }

    @Override
    public int getServerPort() {
        return echoServer.getPort();
    }

    @Test
    public void shouldForwardRequestsAndFixContentType() throws Exception {
        // given
        getMockServerClient()
            .when(
                request()
                    .withHeader("Content-Type", "application/encrypted;charset=UTF-8")
            )
            .forward(
                httpRequest ->
                    httpRequest
                        .replaceHeader(header("Content-Type", "application/encrypted"))
                        .withBody(binary(httpRequest.getBodyAsRawBytes()))
            );

        // and
        HttpClient httpClient = createHttpClient();
        byte[] hexBytes = RandomUtils.nextBytes(150);
        String hexString = Hex.encodeHexString(hexBytes).toUpperCase();

        // when
        HttpPost request = new HttpPost(
            new URIBuilder()
                .setScheme("http")
                .setHost("127.0.0.1")
                .setPort(getServerPort())
                .setPath(addContextToPath("test_headers_and_body"))
                .build()
        );
        request.setEntity(new ByteArrayEntity(hexBytes));
        request.setHeader("Content-Type", "application/encrypted;charset=utf-8");
        HttpResponse response = httpClient.execute(request);

        // then
        assertEquals(HttpStatusCode.OK_200.code(), response.getStatusLine().getStatusCode());
        assertEquals(hexString.toUpperCase(), Hex.encodeHexString(EntityUtils.toByteArray(response.getEntity())).toUpperCase());

        // and
        getMockServerClient().verify(
            request()
                .withMethod("POST")
                .withPath("/test_headers_and_body")
                .withBody(hexBytes),
            exactly(1)
        );
    }

}
