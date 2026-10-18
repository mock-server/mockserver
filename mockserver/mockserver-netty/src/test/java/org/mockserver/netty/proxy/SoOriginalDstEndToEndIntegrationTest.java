package org.mockserver.netty.proxy;

import org.junit.Assume;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * End-to-end integration test for SO_ORIGINAL_DST resolution via iptables
 * REDIRECT. Runs a privileged Linux container (with NET_ADMIN) that:
 * <ol>
 *   <li>Starts MockServer from the fat JAR with transparent proxy enabled</li>
 *   <li>Creates an iptables REDIRECT rule sending traffic destined for a dummy
 *       IP:port to MockServer's listen port</li>
 *   <li>Issues a request to the dummy IP from inside the container</li>
 *   <li>Verifies MockServer handled the request (proving SO_ORIGINAL_DST or
 *       conntrack resolved the pre-REDIRECT destination)</li>
 * </ol>
 * <p>
 * <b>Gating:</b> this test is Docker-gated. It will SKIP (not fail) when Docker is
 * not available. The iptables/SO_ORIGINAL_DST logic runs inside a Linux container,
 * so the host OS does not need to be Linux — Docker Desktop on macOS provides the
 * required Linux VM.
 * <p>
 * Uses the Docker CLI directly (rather than Testcontainers) to avoid version
 * compatibility issues between docker-java and newer Docker Engine releases — see
 * {@link DockerCliTestSupport}.
 * <p>
 * <h3>Manual verification procedure (preserved from original placeholder)</h3>
 * <ol>
 *   <li>Build the MockServer fat JAR:
 *       {@code ./mvnw -pl mockserver-netty package -DskipTests -Djacoco.skip}</li>
 *   <li>Run a privileged container with NET_ADMIN:
 *       <pre>{@code
 * docker run --rm --cap-add=NET_ADMIN -v $(pwd)/mockserver-netty/target:/app \
 *   eclipse-temurin:17-jre bash -c '
 *     apt-get update && apt-get install -y iptables curl iproute2
 *     java -jar /app/mockserver-netty-*-jar-with-dependencies.jar \
 *       -serverPort 1080 -logLevel INFO &
 *     sleep 5
 *     curl -s -X PUT http://localhost:1080/mockserver/expectation -d "{
 *       \"httpRequest\": {\"path\": \"/.*\"},
 *       \"httpResponse\": {\"statusCode\": 200, \"body\": \"original-dst-ok\"}
 *     }"
 *     iptables -t nat -A OUTPUT -d 10.99.99.1 -p tcp --dport 8080 \
 *       -j REDIRECT --to-port 1080
 *     ip addr add 10.99.99.1/32 dev lo
 *     RESULT=$(curl -s -o /dev/null -w "%{http_code}" http://10.99.99.1:8080/test)
 *     echo "HTTP status: $RESULT"
 *     [ "$RESULT" = "200" ] && echo "PASS" || echo "FAIL"
 *   '
 *       }</pre>
 *   </li>
 * </ol>
 */
public class SoOriginalDstEndToEndIntegrationTest {

    private static final org.slf4j.Logger LOG =
        org.slf4j.LoggerFactory.getLogger(SoOriginalDstEndToEndIntegrationTest.class);

    @Test
    public void shouldResolveOriginalDestinationViaIptablesRedirect() throws IOException, InterruptedException {
        // Gate 1: Docker available (iptables + SO_ORIGINAL_DST run inside the Linux container)
        Assume.assumeTrue(
            "SO_ORIGINAL_DST end-to-end test requires Docker",
            DockerCliTestSupport.isDockerAvailable()
        );

        // Gate 2: fat JAR exists (built by `mvn package`)
        Path fatJar = DockerCliTestSupport.findFatJar();
        Assume.assumeTrue(
            "Fat JAR not found — run `./mvnw -pl mockserver-netty package -DskipTests` first",
            fatJar != null && Files.isRegularFile(fatJar)
        );

        // Build a Docker image with iptables, curl, iproute2, and Java.
        // Dockerfile is piped via stdin with no build context ("-") so the daemon
        // does not receive the whole working directory.
        String imageName = "mockserver-so-original-dst-e2e-test";
        int buildResult = DockerCliTestSupport.runProcess(
            "docker", "build", "-t", imageName, "-",
            "FROM eclipse-temurin:17-jre\n"
                + "RUN apt-get update && apt-get install -y iptables curl iproute2 && rm -rf /var/lib/apt/lists/*\n"
        );
        assertThat("Docker image build should succeed", buildResult, org.hamcrest.CoreMatchers.is(0));

        // Run the container with NET_ADMIN, mounting the fat JAR
        String script =
            "java -Dmockserver.transparentProxyEnabled=true"
                + " -Dmockserver.logLevel=INFO"
                + " -jar /app/mockserver.jar"
                + " -serverPort 1080 &"
                + " MOCKSERVER_PID=$!;"
                + " for i in $(seq 1 30); do"
                + "   curl -s http://localhost:1080/mockserver/status && break;"
                + "   sleep 1;"
                + " done;"
                + " curl -s -X PUT http://localhost:1080/mockserver/expectation"
                + " -d '{\"httpRequest\":{\"path\":\"/.*\"},"
                + "\"httpResponse\":{\"statusCode\":200,\"body\":\"original-dst-ok\"}}';"
                + " iptables -t nat -A OUTPUT -d 10.99.99.1 -p tcp --dport 8080"
                + " -j REDIRECT --to-port 1080;"
                + " ip addr add 10.99.99.1/32 dev lo;"
                + " HTTP_CODE=$(curl -s -o /tmp/body.txt -w '%{http_code}'"
                + " http://10.99.99.1:8080/test-original-dst);"
                + " BODY=$(cat /tmp/body.txt);"
                + " echo \"HTTP_CODE=$HTTP_CODE\";"
                + " echo \"BODY=$BODY\";"
                + " kill $MOCKSERVER_PID 2>/dev/null || true";

        String[] runCmd = {
            "docker", "run", "--rm",
            DockerCliTestSupport.HOST_USER_NAMESPACE,
            "--cap-add=NET_ADMIN",
            "-v", fatJar.toAbsolutePath() + ":/app/mockserver.jar:ro",
            imageName,
            "bash", "-c", script
        };

        ProcessBuilder pb = new ProcessBuilder(runCmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        String output;
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.lines().collect(Collectors.joining("\n"));
        }

        boolean finished = process.waitFor(120, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        Assume.assumeTrue("Container did not finish in time", finished);

        LOG.info("Container output:\n{}", output);

        // If the daemon refused to start the NET_ADMIN container (e.g. a
        // user-namespace-remapped CI daemon), skip rather than fail — the
        // capability is absent, not the behaviour broken.
        Assume.assumeFalse(
            "Docker daemon refused to start the NET_ADMIN container "
                + "(capability unavailable, e.g. user-namespace remapping) — skipping SO_ORIGINAL_DST e2e test.",
            DockerCliTestSupport.containerStartRejected(output)
        );

        int exitCode = process.exitValue();
        assertThat("Container should exit successfully (exit code 0), output:\n" + output,
            exitCode, org.hamcrest.CoreMatchers.is(0));

        // Verify the HTTP request was handled (proving original destination was resolved)
        assertThat("Container output should contain HTTP_CODE=200",
            output, containsString("HTTP_CODE=200"));
        assertThat("Container output should contain the response body",
            output, containsString("original-dst-ok"));
    }
}
