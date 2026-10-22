package org.mockserver.lifecycle;

import io.netty.util.concurrent.FastThreadLocalThread;
import org.junit.After;
import org.junit.Test;
import org.mockserver.extension.AdminApiExtension;
import org.mockserver.extension.Extension;
import org.mockserver.netty.MockServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpResponse.response;

/**
 * Extension admin routes on a Netty server: served off the event loop on their own capped pool, so a blocked handler
 * delays no mocked response, and a call back into the server at the cap is refused rather than deadlocking.
 */
public class ExtensionAdminRouteTest {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private MockServer mockServer;

    static final class Admin implements AdminApiExtension {
        interface Handler {
            org.mockserver.model.HttpResponse handle(org.mockserver.model.HttpRequest request, String subPath) throws Exception;
        }

        final List<Thread> threads = new CopyOnWriteArrayList<>();
        volatile Handler handler = (request, subPath) -> response().withStatusCode(200).withBody("served " + subPath);

        @Override
        public String name() {
            return "acme";
        }

        @Override
        public org.mockserver.model.HttpResponse handle(org.mockserver.model.HttpRequest request, String subPath) throws Exception {
            threads.add(Thread.currentThread());
            return handler.handle(request, subPath);
        }
    }

    @After
    public void stopServer() {
        if (mockServer != null) {
            mockServer.stop();
        }
    }

    private int start(org.mockserver.configuration.Configuration configuration, Admin admin) {
        if (admin != null) {
            configuration.extensions((Supplier<Extension>) () -> admin);
        }
        mockServer = new MockServer(configuration);
        return mockServer.getLocalPort();
    }

    private static HttpResponse<String> get(int port, String path) throws Exception {
        return HTTP_CLIENT.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(30)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void expect(int port, String json) throws Exception {
        HttpResponse<String> created = HTTP_CLIENT.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mockserver/expectation"))
            .PUT(HttpRequest.BodyPublishers.ofString(json)).header("Content-Type", "application/json").build(), HttpResponse.BodyHandlers.ofString());
        assertThat(created.body(), created.statusCode(), is(201));
    }

    @Test
    public void servesTheRouteOnAnAdminThreadNotAnEventLoop() throws Exception {
        Admin admin = new Admin();
        int port = start(configuration(), admin);

        HttpResponse<String> response = get(port, "/mockserver/extensions/acme/hello");

        assertThat(response.statusCode(), is(200));
        assertThat(response.body(), is("served /hello"));
        assertThat(admin.threads, hasSize(1));
        Thread thread = admin.threads.get(0);
        assertThat(thread.getName(), startsWith("MockServer-ext-admin-"));
        assertThat("never a Netty event-loop thread", thread instanceof FastThreadLocalThread, is(false));
    }

    @Test
    public void aBlockedHandlerDelaysNoMockedResponse() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Admin admin = new Admin();
        admin.handler = (request, subPath) -> {
            entered.countDown();
            release.await(30, SECONDS);
            return response().withStatusCode(200);
        };
        int port = start(configuration().extensionAdminMaxInFlight(1), admin);
        expect(port, "{\"httpRequest\":{\"path\":\"/plain\"},\"httpResponse\":{\"body\":\"plain\"}}");
        expect(port, "{\"httpRequest\":{\"path\":\"/delayed\"},\"httpResponse\":{\"body\":\"delayed\",\"delay\":{\"timeUnit\":\"MILLISECONDS\",\"value\":50}}}");
        try {
            java.util.concurrent.CompletableFuture<HttpResponse<String>> blocked = HTTP_CLIENT.sendAsync(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mockserver/extensions/acme/block")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(entered.await(30, SECONDS), is(true));

            long started = System.nanoTime();
            assertThat(get(port, "/plain").body(), is("plain"));
            assertThat(get(port, "/delayed").body(), is("delayed"));
            assertThat("mocked responses are not held up by the blocked admin call",
                Duration.ofNanos(System.nanoTime() - started).toMillis(), lessThan(5_000L));
            assertThat("a second admin call at the cap is refused", get(port, "/mockserver/extensions/acme/other").statusCode(), is(503));

            release.countDown();
            assertThat(blocked.get(30, SECONDS).statusCode(), is(200));
        } finally {
            release.countDown();
        }
    }

    @Test
    public void refusesACallBackIntoTheServerAtTheCapInsteadOfDeadlocking() throws Exception {
        Admin admin = new Admin();
        int port = start(configuration().extensionAdminMaxInFlight(1), admin);
        admin.handler = (request, subPath) -> {
            if (subPath.equals("/outer")) {
                int inner = get(port, "/mockserver/extensions/acme/inner").statusCode();
                return response().withStatusCode(200).withBody("inner answered " + inner);
            }
            return response().withStatusCode(200);
        };

        HttpResponse<String> outer = get(port, "/mockserver/extensions/acme/outer");

        assertThat(outer.statusCode(), is(200));
        assertThat(outer.body(), is("inner answered 503"));
    }

    @Test
    public void answersAHungHandlerWith500AtTheTimeout() throws Exception {
        Admin admin = new Admin();
        admin.handler = (request, subPath) -> {
            SECONDS.sleep(30);
            return response().withStatusCode(200);
        };
        int port = start(configuration().extensionAdminTimeoutMillis(300L), admin);

        long started = System.nanoTime();
        HttpResponse<String> response = get(port, "/mockserver/extensions/acme/hang");

        assertThat(response.statusCode(), is(500));
        assertThat(response.body(), containsString("correlation id"));
        assertThat(Duration.ofNanos(System.nanoTime() - started).toMillis(), lessThan(10_000L));
    }

    @Test
    public void leavesOtherNamesToExpectationsAsBefore() throws Exception {
        int port = start(configuration(), new Admin());
        expect(port, "{\"httpRequest\":{\"path\":\"/mockserver/extensions/other/x\"},\"httpResponse\":{\"body\":\"from an expectation\"}}");

        HttpResponse<String> response = get(port, "/mockserver/extensions/other/x");

        assertThat(response.statusCode(), is(200));
        assertThat(response.body(), is("from an expectation"));
    }

    @Test
    public void appliesControlPlaneMutualTlsOverRealTls() throws Exception {
        org.mockserver.test.TestCertificates.Issued ca = org.mockserver.test.TestCertificates.certificateAuthority("Extension Route CA");
        org.mockserver.test.TestCertificates.Issued client = org.mockserver.test.TestCertificates.clientCertificate("extension route client", ca);
        java.nio.file.Path caChain = java.nio.file.Files.createTempFile("extension-route-ca", ".pem");
        java.nio.file.Files.write(caChain, org.mockserver.test.TestCertificates.toPem(ca.certificate).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Admin admin = new Admin();
        try {
            int port = start(configuration()
                .controlPlaneTLSMutualAuthenticationRequired(true)
                .controlPlaneTLSMutualAuthenticationCAChain(caChain.toString())
                .controlPlaneJWTAuthenticationRequired(false)
                .controlPlaneOidcAuthenticationRequired(false), admin);

            assertThat("no client certificate", getOverTls(port, null, "/mockserver/extensions/acme/hello"), is("401"));
            assertThat(admin.threads, empty());
            assertThat("trusted client certificate", getOverTls(port, client, "/mockserver/extensions/acme/hello"), is("200"));
            assertThat(admin.threads, hasSize(1));
        } finally {
            java.nio.file.Files.deleteIfExists(caChain);
        }
    }

    private static String getOverTls(int port, org.mockserver.test.TestCertificates.Issued client, String path) throws Exception {
        javax.net.ssl.KeyManager[] keyManagers = null;
        if (client != null) {
            java.security.KeyStore keyStore = java.security.KeyStore.getInstance("PKCS12");
            keyStore.load(null, null);
            keyStore.setKeyEntry("client", client.keyPair.getPrivate(), "changeit".toCharArray(), new java.security.cert.X509Certificate[]{client.certificate});
            javax.net.ssl.KeyManagerFactory keyManagerFactory = javax.net.ssl.KeyManagerFactory.getInstance(javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, "changeit".toCharArray());
            keyManagers = keyManagerFactory.getKeyManagers();
        }
        javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
        sslContext.init(keyManagers, io.netty.handler.ssl.util.InsecureTrustManagerFactory.INSTANCE.getTrustManagers(), null);
        try (javax.net.ssl.SSLSocket socket = (javax.net.ssl.SSLSocket) sslContext.getSocketFactory().createSocket("localhost", port)) {
            socket.setSoTimeout(30_000);
            socket.startHandshake();
            java.io.OutputStream outputStream = socket.getOutputStream();
            outputStream.write(("GET " + path + " HTTP/1.1\r\nHost: localhost:" + port + "\r\nConnection: close\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            outputStream.flush();
            String statusLine = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.US_ASCII)).readLine();
            return statusLine == null ? "no response" : statusLine.split(" ")[1];
        }
    }

    @Test
    public void appliesControlPlaneBasicAuthenticationAndKeepsItsCredentialsOutOfTheLog() throws Exception {
        Admin admin = new Admin();
        String basic = "Basic " + java.util.Base64.getEncoder().encodeToString("admin:s3cret".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        // TRACE, so the route's request is logged at all ("received request"), and is masked
        int port = start(configuration()
            .logLevel("TRACE")
            .controlPlaneTLSMutualAuthenticationRequired(false)
            .controlPlaneJWTAuthenticationRequired(false)
            .controlPlaneOidcAuthenticationRequired(false)
            .controlPlaneBasicAuthenticationRequired(true)
            .controlPlaneBasicAuthenticationAllowInsecure(true)
            .controlPlaneBasicAuthenticationCredentials("admin:" + org.mockserver.authentication.basic.PasswordHash.pbkdf2Sha256("s3cret", 10_000)), admin);

        assertThat("no credentials", get(port, "/mockserver/extensions/acme/hello").statusCode(), is(401));
        assertThat(admin.threads, empty());
        HttpResponse<String> served = HTTP_CLIENT.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mockserver/extensions/acme/hello"))
            .timeout(Duration.ofSeconds(30)).header("Authorization", basic).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(served.statusCode(), is(200));
        assertThat(admin.threads, hasSize(1));

        String logs = HTTP_CLIENT.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mockserver/retrieve?type=LOGS&format=LOG_ENTRIES"))
            .timeout(Duration.ofSeconds(30)).header("Authorization", basic).PUT(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString()).body();
        assertThat(logs, org.hamcrest.Matchers.containsString("/mockserver/extensions/acme/hello"));
        assertThat(logs, org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(basic.substring("Basic ".length()))));
    }

    @Test
    public void servesNoRouteWithoutAnExtension() throws Exception {
        int port = start(configuration(), null);
        assertThat(get(port, "/mockserver/extensions/acme/hello").statusCode(), is(404));

        expect(port, "{\"httpRequest\":{\"path\":\"/mockserver/extensions/acme/hello\"},\"httpResponse\":{\"body\":\"from an expectation\"}}");

        assertThat(get(port, "/mockserver/extensions/acme/hello").body(), is("from an expectation"));
    }
}
