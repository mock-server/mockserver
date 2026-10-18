package org.mockserver.testing.integration.mock;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockserver.configuration.Configuration.configuration;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.fail;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class SharedEchoServerGuardTest {

    @Rule
    public Timeout timeout = Timeout.seconds(120);

    private static EventLoopGroup eventLoopGroup;
    private static NettyHttpClient ownHttpClient;
    private PrintStream originalErr;
    private ByteArrayOutputStream capturedErr;

    @BeforeClass
    public static void createHttpClient() {
        eventLoopGroup = new NioEventLoopGroup(1);
        ownHttpClient = new NettyHttpClient(configuration(), new MockServerLogger(), eventLoopGroup, null, false);
    }

    @Before
    public void captureStandardError() {
        SharedEchoServerGuard.resetForTest();
        originalErr = System.err;
        capturedErr = new ByteArrayOutputStream();
        System.setErr(new PrintStream(capturedErr, true, UTF_8));
    }

    @After
    public void restoreStandardError() {
        System.setErr(originalErr);
    }

    @AfterClass
    public static void stopSharedEchoServersAndHttpClient() {
        eventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
        synchronized (AbstractMockingIntegrationTestBase.class) {
            stopQuietly(AbstractMockingIntegrationTestBase.insecureEchoServer);
            stopQuietly(AbstractMockingIntegrationTestBase.secureEchoServer);
            AbstractMockingIntegrationTestBase.insecureEchoServer = null;
            AbstractMockingIntegrationTestBase.secureEchoServer = null;
        }
    }

    @Test
    public void shouldReplaceAStoppedSharedEchoServerBeforeTheNextTestClassUsesIt() throws InterruptedException {
        // given - a test class has started the shared echo servers
        assertRan(FirstTestClass.class);
        EchoServer stoppedServer = AbstractMockingIntegrationTestBase.insecureEchoServer;
        EchoServer answeringServer = AbstractMockingIntegrationTestBase.secureEchoServer;
        int stoppedPort = stoppedServer.getPort();

        // and - the insecure one stops answering
        stoppedServer.stop();
        long deadline = System.currentTimeMillis() + 30_000;
        while (SharedEchoServerGuard.answers(stoppedPort, false)) {
            assertThat("stopped echo server still answering", System.currentTimeMillis() < deadline, equalTo(true));
            Thread.sleep(100);
        }

        // when - the next test class forwards to both shared echo servers
        assertRan(SecondTestClass.class);

        // then - the stopped one was replaced, the answering one kept
        assertThat(AbstractMockingIntegrationTestBase.insecureEchoServer, not(sameInstance(stoppedServer)));
        assertThat(AbstractMockingIntegrationTestBase.secureEchoServer, sameInstance(answeringServer));

        // and - the diagnostic names the port and carries the descriptor count and a full thread dump
        String diagnostic = capturedErr.toString(UTF_8);
        assertThat(diagnostic, containsString(SharedEchoServerGuard.DIAGNOSTIC_HEADER));
        assertThat(diagnostic, containsString("insecure echo server on port " + stoppedPort + " did not answer"));
        assertThat(diagnostic, not(containsString("secure echo server on port " + answeringServer.getPort())));
        assertThat(diagnostic, containsString("open file descriptors: "));
        assertThat(diagnostic, containsString("thread dump ("));
        assertThat(diagnostic, containsString("\"" + Thread.currentThread().getName() + "\" #" + Thread.currentThread().getId()));
        assertThat(diagnostic, containsString(SharedEchoServerGuardTest.class.getName() + ".shouldReplaceAStoppedSharedEchoServerBeforeTheNextTestClassUsesIt("));
    }

    @Test
    public void shouldKeepAnsweringSharedEchoServersWithoutADiagnostic() {
        // given
        assertRan(FirstTestClass.class);
        EchoServer insecure = AbstractMockingIntegrationTestBase.insecureEchoServer;
        EchoServer secure = AbstractMockingIntegrationTestBase.secureEchoServer;

        // when
        assertRan(SecondTestClass.class);

        // then
        assertThat(AbstractMockingIntegrationTestBase.insecureEchoServer, sameInstance(insecure));
        assertThat(AbstractMockingIntegrationTestBase.secureEchoServer, sameInstance(secure));
        assertThat(capturedErr.toString(UTF_8), not(containsString(SharedEchoServerGuard.DIAGNOSTIC_HEADER)));
    }

    @Test
    public void shouldProbeWithoutConsumingAQueuedResponseOrTheLastRequest() throws Exception {
        for (boolean secure : new boolean[]{false, true}) {
            EchoServer echoServer = new EchoServer(secure);
            try {
                // given
                echoServer.withNextResponse(response().withStatusCode(418));

                // when
                assertThat("probe answered, secure=" + secure, SharedEchoServerGuard.answers(echoServer.getPort(), secure), equalTo(true));

                // then
                HttpResponse httpResponse = sendTo(ownHttpClient, echoServer, secure, "/after_probe");
                assertThat("queued response, secure=" + secure, httpResponse.getStatusCode(), equalTo(418));
                HttpRequest lastRequest = echoServer.getLastRequest();
                assertThat(lastRequest, notNullValue());
                assertThat(lastRequest.getPath().getValue(), equalTo("/after_probe"));
            } finally {
                echoServer.stop();
            }
        }
    }

    @Test
    public void shouldPrintTheFullThreadDumpOnlyOnce() {
        // when
        SharedEchoServerGuard.printDiagnostic("first failure", System.err);
        String first = capturedErr.toString(UTF_8);
        capturedErr.reset();
        SharedEchoServerGuard.printDiagnostic("second failure", System.err);
        String second = capturedErr.toString(UTF_8);

        // then
        assertThat(first, containsString("first failure"));
        assertThat(first, containsString("thread dump ("));
        assertThat(second, containsString("second failure"));
        assertThat(second, containsString("open file descriptors: "));
        assertThat(second, not(containsString("thread dump (")));
        assertThat(second, containsString("thread dump: printed with the first"));
    }

    @Test
    public void shouldBeTheHeaderTheNettyItShardReportGrepsFor() throws Exception {
        // given
        Path report = repositoryFile(".buildkite/scripts/steps/java-netty-it-echo-guard-report.sh");

        // when
        Process process = new ProcessBuilder("bash", report.toString(), "--print-header").redirectErrorStream(true).start();
        boolean finished = process.waitFor(30, SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        String output = new String(process.getInputStream().readAllBytes(), UTF_8).trim();

        // then
        assertThat("report finished", finished, equalTo(true));
        assertThat("--print-header exit code, output: " + output, process.exitValue(), equalTo(0));
        assertThat(output, equalTo(SharedEchoServerGuard.DIAGNOSTIC_HEADER));
    }

    private static Path repositoryFile(String relativePath) {
        for (Path directory = Paths.get("").toAbsolutePath(); directory != null; directory = directory.getParent()) {
            Path candidate = directory.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        fail(relativePath + " not found above " + Paths.get("").toAbsolutePath());
        return null;
    }

    private static HttpResponse sendTo(NettyHttpClient client, EchoServer echoServer, boolean secure, String path) throws Exception {
        int port = echoServer.getPort();
        return client
            .sendRequest(
                request(path).withSecure(secure).withBody("echo me").withHeader("Host", "127.0.0.1:" + port),
                new InetSocketAddress("127.0.0.1", port)
            )
            .get(30, SECONDS);
    }

    private static void assertRan(Class<?> testClass) {
        Result result = JUnitCore.runClasses(testClass);
        assertThat(testClass.getSimpleName() + " failures: " + result.getFailures(), result.wasSuccessful(), equalTo(true));
        assertThat(result.getRunCount(), equalTo(1));
    }

    private static void stopQuietly(EchoServer echoServer) {
        if (echoServer != null) {
            echoServer.stop();
        }
    }

    public static class FirstTestClass extends AbstractMockingIntegrationTestBase {

        @Override
        public int getServerPort() {
            return 0;
        }

        @Test
        public void forwardsToBothSharedEchoServers() throws Exception {
            assertEchoes(httpClient, insecureEchoServer, false);
            assertEchoes(httpClient, secureEchoServer, true);
        }
    }

    public static class SecondTestClass extends AbstractMockingIntegrationTestBase {

        @Override
        public int getServerPort() {
            return 0;
        }

        @Test
        public void forwardsToBothSharedEchoServers() throws Exception {
            assertEchoes(httpClient, insecureEchoServer, false);
            assertEchoes(httpClient, secureEchoServer, true);
        }
    }

    private static void assertEchoes(NettyHttpClient client, EchoServer echoServer, boolean secure) throws Exception {
        HttpResponse httpResponse = sendTo(client, echoServer, secure, "/echo");
        assertThat("status, secure=" + secure, httpResponse.getStatusCode(), equalTo(200));
        assertThat("body, secure=" + secure, httpResponse.getBodyAsString(), equalTo("echo me"));
    }
}
