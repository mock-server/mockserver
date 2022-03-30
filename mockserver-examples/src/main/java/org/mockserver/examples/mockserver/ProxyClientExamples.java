package org.mockserver.examples.mockserver;

import com.google.common.io.ByteStreams;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.ClearType;
import org.mockserver.model.RequestDefinition;
import org.mockserver.socket.tls.KeyStoreFactory;
import org.mockserver.verify.VerificationTimes;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;

public class ProxyClientExamples {

    public void sendRequestViaHTTPSProxy() throws Exception {
        URL url = new URL("https://mock-server.com");
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", 1080));
        SSLContext sslContext = new KeyStoreFactory(configuration(), new MockServerLogger(this.getClass())).sslContext();
        HttpsURLConnection httpURLConnection = (HttpsURLConnection) url.openConnection(proxy);
        httpURLConnection.setSSLSocketFactory(sslContext.getSocketFactory());
        httpURLConnection.setRequestMethod("GET");
        String body = new String(ByteStreams.toByteArray(httpURLConnection.getInputStream()), StandardCharsets.UTF_8);
        System.out.println("body = " + body);
    }

    public void sendRequestViaHTTPProxy() throws Exception {
        URL url = new URL("http://mock-server.com");
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", 1080));
        HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection(proxy);
        httpURLConnection.setRequestMethod("GET");
        String body = new String(ByteStreams.toByteArray(httpURLConnection.getInputStream()), StandardCharsets.UTF_8);
        System.out.println("body = " + body);
    }

    public void verifyRequests() {
        new MockServerClient("localhost", 1080)
            .verify(
                request()
                    .withPath("/some/path"),
                VerificationTimes.atLeast(2)
            );
    }

    public void verifyRequestsClientAndProxy() {
        new ClientAndServer(1080)
            .verify(
                request()
                    .withPath("/some/path"),
                VerificationTimes.atLeast(2)
            );
    }

    public void verifyRequestSequence() {
        new MockServerClient("localhost", 1080)
            .verify(
                request()
                    .withPath("/some/path/one"),
                request()
                    .withPath("/some/path/two"),
                request()
                    .withPath("/some/path/three")
            );
    }

    public void retrieveRecordedRequests() {
        RequestDefinition[] recordedRequests = new MockServerClient("localhost", 1080)
            .retrieveRecordedRequests(
                request()
                    .withPath("/some/path")
                    .withMethod("POST")
            );
    }

    public void retrieveRecordedLogMessages() {
        String[] logMessages = new MockServerClient("localhost", 1080)
            .retrieveLogMessagesArray(
                request()
                    .withPath("/some/path")
                    .withMethod("POST")
            );
    }

    public void clear() {
        new MockServerClient("localhost", 1080).clear(
            request()
                .withPath("/some/path")
                .withMethod("POST")
        );
    }

    public void clearLogs() {
        new MockServerClient("localhost", 1080).clear(
            request()
                .withPath("/some/path")
                .withMethod("POST"),
            ClearType.LOG
        );
    }

    public void reset() {
        new MockServerClient("localhost", 1080).reset();
    }

    public void bindToAdditionFreePort() {
        List<Integer> boundPorts = new MockServerClient("localhost", 1080).bind(
            0
        );
    }

    public void bindToAdditionalSpecifiedPort() {
        List<Integer> boundPorts = new MockServerClient("localhost", 1080).bind(
            1081, 1082
        );
    }

}
