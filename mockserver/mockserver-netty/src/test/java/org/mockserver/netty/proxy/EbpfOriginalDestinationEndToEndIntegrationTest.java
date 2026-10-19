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
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * End-to-end integration test proving that {@link EbpfOriginalDestinationResolver} itself
 * resolves the original destination of a real intercepted connection: the BPF map lookup
 * keyed by the accepted socket's cookie, and the decode of the map value.
 * <p>
 * A privileged Linux container creates the pinned map with {@code bpftool}, starts an
 * upstream MockServer on {@code 10.99.99.2:9090} and a transparent-proxy MockServer on
 * {@code :1080}, and REDIRECTs client traffic for {@code 10.99.99.1:8080} to the proxy. The
 * proxy is frozen with {@code SIGSTOP} so the client connection waits in the accept queue;
 * its cookie is read from {@code ss -e} ({@code sk:}, the same value {@code SO_COOKIE}
 * returns), and a map entry for that cookie pointing at {@code 10.99.99.2:9090} is written
 * before the proxy is resumed. Only the eBPF resolver knows that address: SO_ORIGINAL_DST and
 * conntrack resolve {@code 10.99.99.1:8080}, where nothing listens for the proxy's own
 * connection. The test asserts the upstream's body came back and the entry was consumed.
 * <p>
 * A control request with no map entry runs first and must NOT reach the upstream, so the
 * fallback resolvers alone cannot satisfy the assertions.
 * <p>
 * <b>Gating:</b> skips when Docker, the fat JAR, the {@code --privileged} container, BPF support
 * or BPF map creation is unavailable; CI fails the step on a skip ({@code assert-suite-ran.sh}).
 *
 * @see EbpfOriginalDestinationResolver
 * @see CompositeOriginalDestinationResolver
 */
public class EbpfOriginalDestinationEndToEndIntegrationTest {

    private static final org.slf4j.Logger LOG =
        org.slf4j.LoggerFactory.getLogger(EbpfOriginalDestinationEndToEndIntegrationTest.class);

    private static final String SCRIPT = """
        set -e
        if ! bpftool version >/dev/null 2>&1; then echo 'BPF_UNSUPPORTED=true'; exit 0; fi
        mount -t bpf bpf /sys/fs/bpf 2>/dev/null || true
        MAP=/sys/fs/bpf/mockserver_orig_dst
        if ! bpftool map create $MAP type hash key 8 value 6 entries 4096 name ms_orig_dst 2>&1; then
          echo 'BPF_MAP_CREATE_FAILED=true'; exit 0
        fi

        java -Dmockserver.logLevel=WARN -jar /app/mockserver.jar -serverPort 9090 >/tmp/upstream.log 2>&1 &
        java -Dmockserver.transparentProxyEnabled=true \
          -Dmockserver.transparentProxyEbpf=true \
          -Dmockserver.transparentProxyEbpfMapPath=$MAP \
          -Dmockserver.logLevel=INFO \
          -jar /app/mockserver.jar -serverPort 1080 >/tmp/proxy.log 2>&1 &
        PROXY_PID=$!
        wait_ready() {
          for i in $(seq 1 60); do
            curl -sf -o /dev/null -X PUT "http://localhost:$1/mockserver/status" && return 0
            sleep 1
          done
          echo "MOCKSERVER_NOT_READY port=$1"; return 1
        }
        wait_ready 9090
        wait_ready 1080
        curl -sf -o /dev/null -X PUT http://localhost:9090/mockserver/expectation \
          -d '{"httpRequest":{"path":"/.*"},"httpResponse":{"statusCode":200,"body":"ebpf-upstream-ok"}}'

        ip addr add 10.99.99.1/32 dev lo
        ip addr add 10.99.99.2/32 dev lo
        ip addr add 10.99.99.3/32 dev lo
        iptables -t nat -A OUTPUT -s 10.99.99.3 -d 10.99.99.1 -p tcp --dport 8080 -j REDIRECT --to-port 1080
        request() {
          curl -s -m 30 --interface 10.99.99.3 -o /tmp/body.txt -w '%{http_code}' \
            http://10.99.99.1:8080/test-ebpf >/tmp/code.txt || true
        }

        request
        echo "CONTROL_HTTP_CODE=$(cat /tmp/code.txt)"
        echo "CONTROL_BODY=$(cat /tmp/body.txt)"

        kill -STOP $PROXY_PID
        request &
        CURL_PID=$!
        CONN=""
        for i in $(seq 1 100); do
          CONN=$(ss -tnHe state established '( sport = :1080 )' | grep -F '10.99.99.3' || true)
          [ -n "$CONN" ] && break
          sleep 0.1
        done
        echo "ACCEPT_QUEUE_CONNECTION=$CONN"
        COOKIE=$(echo "$CONN" | grep -o 'sk:[0-9a-f]*' | head -1 | cut -d: -f2)
        if [ -z "$COOKIE" ]; then echo 'SOCKET_COOKIE_NOT_FOUND=true'; kill -CONT $PROXY_PID; exit 1; fi
        KEY=$(printf '%016x' "0x$COOKIE" | sed 's/../& /g' | awk '{for (i = NF; i > 0; i--) printf "%s ", $i}')
        bpftool map update pinned $MAP key hex $KEY value hex 0a 63 63 02 23 82
        echo "MAP_ENTRY_WRITTEN cookie=0x$COOKIE key=$KEY"
        kill -CONT $PROXY_PID
        wait $CURL_PID
        EBPF_CODE=$(cat /tmp/code.txt)
        echo "EBPF_HTTP_CODE=$EBPF_CODE"
        echo "EBPF_BODY=$(cat /tmp/body.txt)"
        echo "MAP_ENTRIES_AFTER=$(bpftool map dump pinned $MAP | grep -c '^key:' || true)"
        [ "$EBPF_CODE" = 200 ] || tail -n 60 /tmp/proxy.log
        rm -f $MAP
        """;

    @Test
    public void shouldResolveOriginalDestinationViaEbpfMap() throws IOException, InterruptedException {
        Assume.assumeTrue(
            "eBPF end-to-end test requires Docker",
            DockerCliTestSupport.isDockerAvailable()
        );

        Path fatJar = DockerCliTestSupport.findFatJar();
        Assume.assumeTrue(
            "Fat JAR not found — run `./mvnw -pl mockserver-netty package -DskipTests` first",
            fatJar != null && Files.isRegularFile(fatJar)
        );

        String imageName = "mockserver-ebpf-e2e-test";
        int buildResult = DockerCliTestSupport.runProcess(
            "docker", "build", "-t", imageName, "-",
            "FROM eclipse-temurin:17-jre\n"
                + "RUN apt-get update && apt-get install -y "
                + "iptables curl iproute2 linux-tools-common bpftool "
                + "&& rm -rf /var/lib/apt/lists/*\n"
        );

        if (buildResult != 0) {
            buildResult = DockerCliTestSupport.runProcess(
                "docker", "build", "-t", imageName, "-",
                "FROM fedora:41\n"
                    + "RUN dnf install -y java-17-openjdk-headless bpftool iptables iproute curl "
                    + "&& dnf clean all\n"
            );
        }

        Assume.assumeTrue(
            "Docker image build failed — bpftool may not be available",
            buildResult == 0
        );

        String[] runCmd = {
            "docker", "run", "--rm",
            DockerCliTestSupport.HOST_USER_NAMESPACE,
            "--privileged",
            "-v", fatJar.toAbsolutePath() + ":/app/mockserver.jar:ro",
            imageName,
            "bash", "-c", SCRIPT
        };

        ProcessBuilder pb = new ProcessBuilder(runCmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        String output;
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.lines().collect(Collectors.joining("\n"));
        }

        boolean finished = process.waitFor(180, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        Assume.assumeTrue("Container did not finish in time", finished);

        LOG.info("Container output:\n{}", output);

        // If the daemon refused to start the privileged container (e.g. a
        // user-namespace-remapped CI daemon rejecting --privileged), skip rather
        // than fail — the privilege is absent, not the behaviour broken.
        Assume.assumeFalse(
            "Docker daemon refused to start the --privileged container "
                + "(e.g. user-namespace remapping) — skipping eBPF e2e test.",
            DockerCliTestSupport.containerStartRejected(output)
        );

        // Skip if BPF is not supported in this kernel
        Assume.assumeFalse(
            "Kernel lacks BPF support — skipping eBPF e2e test",
            output.contains("BPF_UNSUPPORTED=true")
        );

        // Skip if map creation failed (insufficient privileges)
        Assume.assumeFalse(
            "BPF map creation failed (insufficient privileges) — skipping eBPF e2e test",
            output.contains("BPF_MAP_CREATE_FAILED=true")
        );

        int exitCode = process.exitValue();
        assertThat("Container should exit successfully (exit code 0), output:\n" + output,
            exitCode, is(0));

        assertThat("the control request must be resolved by the fallback to the refused REDIRECTed address, output:\n" + output,
            output, containsString("CONTROL_HTTP_CODE=502"));
        assertThat("without a map entry the fallback resolvers must not reach the eBPF-only upstream, output:\n" + output,
            output, not(containsString("CONTROL_BODY=ebpf-upstream-ok")));
        assertThat("the accepted socket's cookie must be written to the map, output:\n" + output,
            output, containsString("MAP_ENTRY_WRITTEN"));
        assertThat("the proxy must forward to the destination decoded from the map entry, output:\n" + output,
            output, containsString("EBPF_HTTP_CODE=200"));
        assertThat("the proxy must forward to the destination decoded from the map entry, output:\n" + output,
            output, containsString("EBPF_BODY=ebpf-upstream-ok"));
        assertThat("the eBPF resolver must consume the map entry it answered from, output:\n" + output,
            output, containsString("MAP_ENTRIES_AFTER=0"));
    }
}
