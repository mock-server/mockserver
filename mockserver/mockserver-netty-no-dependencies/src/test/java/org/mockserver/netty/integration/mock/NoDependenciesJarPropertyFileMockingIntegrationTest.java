package org.mockserver.netty.integration.mock;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.netty.integration.NoDependenciesJarRunner;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.ServerSocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression guard for issues #2344 and #2338: booting the shaded
 * {@code mockserver-netty-no-dependencies} uber jar with a <em>populated</em>
 * {@code mockserver.properties} on the property-file path must NOT throw
 * {@code ExceptionInInitializerError} / {@code NoClassDefFoundError}.
 *
 * <p>The original bug was a static-initialiser ordering fault in
 * {@code ConfigurationProperties}: reading a populated property file runs the
 * sensitive-value redaction path ({@code isSensitivePropertyName()} →
 * {@code REDACTED_VALUE}) during {@code <clinit>}, and that path referenced
 * fields not yet initialised, producing an {@code ExceptionInInitializerError}
 * at startup (fix commit 949cb59f1). That fix is unit-guarded by
 * {@code ConfigurationPropertiesInitializationTest} — but only on the full Maven
 * classpath. The dangerous, previously-unguarded seam is the <strong>shaded uber
 * jar</strong>: a transitive class reachable only while reading a populated
 * property file (e.g. through the redaction path) could be missing from the jar
 * and ordinary CI would never exercise it, because the other no-dependencies jar
 * smoke tests boot with only {@code -serverPort} and no property file.
 *
 * <p>This test closes that seam. It writes a temp {@code mockserver.properties}
 * containing a SENSITIVE key ({@code proxyAuthenticationPassword} — the exact
 * kind of value the redaction path must process during class init) plus a couple
 * of ordinary settings, boots the no-dependencies jar pointed at that file via
 * {@code -Dmockserver.propertyFile}, and asserts the server:
 * <ul>
 *   <li>reaches its {@code started on port} banner (no {@code ExceptionInInitializerError}
 *       / {@code NoClassDefFoundError} / {@code SLF4J} provider failure on stderr),</li>
 *   <li>actually read the populated property file (the {@code Reading properties from
 *       property file} dump appears, proving the guarded redaction path ran in the
 *       shaded jar), with the sensitive value REDACTED rather than printed, and</li>
 *   <li>serves a basic mocked request end-to-end.</li>
 * </ul>
 *
 * <p>Like its sibling {@link ExtendedNoDependenciesJarMockingIntegrationTest},
 * this uses only the JDK ({@link java.net.http.HttpClient} + {@link ProcessBuilder}
 * via the runner) so the test JVM never loads {@code MockServerClient} /
 * {@code NettyHttpClient} from both the unshaded {@code mockserver-core} and this
 * module's shaded jar at once. Docker-free, in-JVM-fork.
 */
public class NoDependenciesJarPropertyFileMockingIntegrationTest {

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    /** A genuinely sensitive value: it must be REDACTED in the startup log, never printed. */
    private static final String SENSITIVE_VALUE = "super-secret-do-not-log";

    private static NoDependenciesJarRunner runner;
    private static int port;
    private static Path propertyFile;

    @BeforeClass
    public static void startServer() throws IOException {
        port = findFreePort();
        propertyFile = writePropertyFile();
        runner = NoDependenciesJarRunner.startServerUsingNoDependenciesJar(port, propertyFile.toFile());
    }

    @AfterClass
    public static void stopServer() throws IOException {
        if (runner != null) {
            runner.stop();
        }
        if (propertyFile != null) {
            Files.deleteIfExists(propertyFile);
        }
    }

    /**
     * The headline regression assertion: with a populated property file (sensitive
     * key included) in effect, the shaded jar must boot cleanly — no
     * {@code ExceptionInInitializerError}, no {@code NoClassDefFoundError}, no SLF4J
     * provider failure — and must log its startup banner.
     */
    @Test
    public void shouldBootWithPopulatedPropertyFileContainingSensitiveKey() {
        boolean started = runner.awaitOutputContaining("started on port", Duration.ofSeconds(30));
        String output = runner.getOutput();

        assertFalse(
            "The no-dependencies jar threw ExceptionInInitializerError while reading a populated "
                + "property file — a class reachable only on the property-read/redaction path is missing "
                + "from the shaded jar (issues #2344 / #2338):\n" + output,
            output.contains("ExceptionInInitializerError"));
        assertFalse(
            "The no-dependencies jar threw NoClassDefFoundError while reading a populated property file — "
                + "a class reachable only on the property-read/redaction path is missing from the shaded jar "
                + "(issues #2344 / #2338):\n" + output,
            output.contains("NoClassDefFoundError"));
        assertFalse(
            "SLF4J reported a provider failure while booting with a property file:\n" + output,
            output.contains("SLF4J(W)") || output.contains("SLF4J(E)"));
        assertTrue(
            "The no-dependencies jar did not reach its 'started on port' banner when booted with a "
                + "populated property file:\n" + output,
            started);
    }

    /**
     * Proves the guarded path actually ran in the shaded jar: the property-file
     * read produced its dump, AND the sensitive value was redacted rather than
     * printed. Reaching the dump means {@code isSensitivePropertyName()} /
     * {@code REDACTED_VALUE} executed during static init without error — exactly
     * the code whose initialisation order regressed in #2338.
     */
    @Test
    public void shouldReadPopulatedPropertyFileAndRedactSensitiveValue() {
        boolean readProperties = runner.awaitOutputContaining("Reading properties from property file", Duration.ofSeconds(30));
        String output = runner.getOutput();

        assertTrue(
            "The shaded jar never logged 'Reading properties from property file' — the populated "
                + "property file at " + propertyFile + " was not read, so the redaction path under test "
                + "did not run:\n" + output,
            readProperties);
        assertTrue(
            "Expected the redacted marker '***REDACTED***' in the property dump for the sensitive key:\n" + output,
            output.contains("***REDACTED***"));
        assertFalse(
            "The sensitive value leaked into the startup log instead of being redacted — the redaction "
                + "path did not process it:\n" + output,
            output.contains(SENSITIVE_VALUE));
    }

    /**
     * End-to-end sanity: after booting with the property file, the server still
     * serves a mocked expectation correctly — confirming the property-file read did
     * not leave the server half-initialised.
     */
    @Test
    public void shouldServeMockedExpectationAfterPropertyFileBoot() throws Exception {
        HttpRequest createExpectation = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/mockserver/expectation"))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(
                "{" +
                "  \"httpRequest\": { \"method\": \"GET\", \"path\": \"/property-file-smoke/hello\" }," +
                "  \"httpResponse\": {" +
                "    \"statusCode\": 200," +
                "    \"headers\": { \"X-Smoke\": [\"ok\"] }," +
                "    \"body\": \"property-file-smoke-response\"" +
                "  }" +
                "}"))
            .build();
        HttpResponse<String> createResponse = HTTP.send(createExpectation, HttpResponse.BodyHandlers.ofString());
        assertEquals(
            "PUT /mockserver/expectation returned " + createResponse.statusCode() + " body=" + createResponse.body(),
            201, createResponse.statusCode());

        HttpRequest invokeMock = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/property-file-smoke/hello"))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();
        HttpResponse<String> mockedResponse = HTTP.send(invokeMock, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, mockedResponse.statusCode());
        assertEquals("property-file-smoke-response", mockedResponse.body());
        assertEquals("ok", mockedResponse.headers().firstValue("X-Smoke").orElse(null));
    }

    /**
     * Write a temp {@code mockserver.properties} with at least one sensitive key
     * (the original NPE was on the sensitive-value redaction path) plus a couple of
     * ordinary settings, so the shaded jar's static initialiser exercises the
     * populated-file read + redaction during boot.
     */
    private static Path writePropertyFile() throws IOException {
        Path file = Files.createTempFile("mockserver-regression-2344-2338-", ".properties");
        String contents = String.join(
            "\n",
            "# Regression guard for issues #2344 / #2338 — populated property file with a sensitive key.",
            "mockserver.proxyAuthenticationPassword=" + SENSITIVE_VALUE,
            "mockserver.maxExpectations=99",
            "mockserver.logLevel=INFO",
            "");
        Files.writeString(file, contents, StandardCharsets.UTF_8);
        return file;
    }

    /**
     * Same OS-assigned-ephemeral-port pattern used by the sibling test and by
     * {@code org.mockserver.socket.PortFactory.findFreePort()}: an IPv4 socket, which is never given a
     * port another process listens on at 127.0.0.1.
     */
    private static int findFreePort() {
        try (ServerSocketChannel s = ServerSocketChannel.open(StandardProtocolFamily.INET)) {
            s.bind(new InetSocketAddress(0));
            return s.socket().getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Could not allocate a free port for the test", e);
        }
    }
}
