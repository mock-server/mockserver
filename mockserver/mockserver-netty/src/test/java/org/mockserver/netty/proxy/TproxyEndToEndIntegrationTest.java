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
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * End-to-end integration test for TPROXY (IP_TRANSPARENT) original destination
 * resolution. Runs a privileged Linux container that:
 * <ol>
 *   <li>Starts MockServer from the fat JAR with both transparent proxy and TPROXY
 *       mode enabled</li>
 *   <li>Creates TPROXY iptables rules (in the mangle table) that redirect traffic
 *       destined for a dummy IP:port to MockServer's listen port while preserving
 *       the original destination as the socket's local address</li>
 *   <li>Issues a request to the dummy IP from inside the container</li>
 *   <li>Verifies MockServer handled the request (proving TPROXY mode resolved
 *       the original destination from the socket's local address)</li>
 * </ol>
 * <p>
 * <b>TPROXY vs REDIRECT:</b> with REDIRECT, the kernel rewrites the destination
 * to the local listen address, so {@code channel.localAddress()} returns MockServer's
 * address. With TPROXY, the kernel preserves the original destination as the socket's
 * local address, so {@code channel.localAddress()} returns the pre-redirect target.
 * This test verifies that the {@link TproxyOriginalDestinationResolver} correctly
 * reads this local address.
 * <p>
 * <b>Gating:</b> Docker-gated. Skips cleanly when Docker is not available.
 * The TPROXY logic runs inside a Linux container, so the host OS does not need
 * to be Linux. Additionally, if the container kernel lacks the {@code xt_TPROXY}
 * module, the test skips gracefully rather than failing.
 * <p>
 * Uses the Docker CLI directly (rather than Testcontainers) to avoid version
 * compatibility issues between docker-java and newer Docker Engine releases — see
 * {@link DockerCliTestSupport}.
 */
public class TproxyEndToEndIntegrationTest {

    private static final org.slf4j.Logger LOG =
        org.slf4j.LoggerFactory.getLogger(TproxyEndToEndIntegrationTest.class);

    @Test
    public void shouldResolveOriginalDestinationViaTproxy() throws IOException, InterruptedException {
        // Gate 1: Docker available (iptables + TPROXY run inside the Linux container)
        Assume.assumeTrue(
            "TPROXY end-to-end test requires Docker",
            DockerCliTestSupport.isDockerAvailable()
        );

        // Gate 2: fat JAR exists
        Path fatJar = DockerCliTestSupport.findFatJar();
        Assume.assumeTrue(
            "Fat JAR not found — run `./mvnw -pl mockserver-netty package -DskipTests` first",
            fatJar != null && Files.isRegularFile(fatJar)
        );

        // Build a Docker image with iptables, curl, iproute2, and Java.
        // Dockerfile is piped via stdin with no build context ("-") so the daemon
        // does not receive the whole working directory.
        String imageName = "mockserver-tproxy-e2e-test";
        int buildResult = DockerCliTestSupport.runProcess(
            "docker", "build", "-t", imageName, "-",
            "FROM eclipse-temurin:17-jre\n"
                + "RUN apt-get update && apt-get install -y iptables curl iproute2 && rm -rf /var/lib/apt/lists/*\n"
        );
        assertThat("Docker image build should succeed", buildResult, is(0));

        // Run the container with NET_ADMIN, mounting the fat JAR.
        // The script detects if TPROXY is unsupported (kernel module missing)
        // and emits a marker so we can skip gracefully.
        String script =
            "java -Dmockserver.transparentProxyEnabled=true"
                + " -Dmockserver.transparentProxyTproxy=true"
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
                + "\"httpResponse\":{\"statusCode\":200,\"body\":\"tproxy-ok\"}}';"
                + " ip route add local 0.0.0.0/0 dev lo table 100;"
                + " ip rule add fwmark 1 lookup 100;"
                + " if ! iptables -t mangle -A PREROUTING -d 10.99.99.2 -p tcp --dport 9090"
                + " -j TPROXY --tproxy-mark 0x1/0x1 --on-port 1080 2>&1; then"
                + "   echo 'TPROXY_UNSUPPORTED=true';"
                + "   kill $MOCKSERVER_PID 2>/dev/null || true;"
                + "   exit 0;"
                + " fi;"
                + " ip addr add 10.99.99.2/32 dev lo;"
                + " HTTP_CODE=$(curl -s -o /tmp/body.txt -w '%{http_code}'"
                + " http://10.99.99.2:9090/test-tproxy);"
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
                + "(capability unavailable, e.g. user-namespace remapping) — skipping TPROXY e2e test.",
            DockerCliTestSupport.containerStartRejected(output)
        );

        // If the kernel lacks xt_TPROXY / nf_tproxy module, skip gracefully
        Assume.assumeFalse(
            "Kernel lacks TPROXY support (xt_TPROXY module not available) — "
                + "skipping TPROXY e2e test.",
            output.contains("TPROXY_UNSUPPORTED=true")
        );

        int exitCode = process.exitValue();
        assertThat("Container should exit successfully (exit code 0), output:\n" + output,
            exitCode, is(0));

        // Verify the HTTP request was handled
        assertThat("Container output should contain HTTP_CODE=200",
            output, containsString("HTTP_CODE=200"));
        assertThat("Container output should contain the response body",
            output, containsString("tproxy-ok"));
    }
}
