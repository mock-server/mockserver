package org.mockserver.netty.proxy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * Shared test infrastructure for Docker-CLI-based end-to-end integration tests
 * ({@link SoOriginalDstEndToEndIntegrationTest}, {@link TproxyEndToEndIntegrationTest}). These tests use
 * {@code docker build} / {@code docker run} directly (via {@link ProcessBuilder})
 * rather than Testcontainers, to avoid version-compatibility issues between
 * docker-java and newer Docker Engine releases.
 * <p>
 * Package-private — only used within the proxy test package.
 */
final class DockerCliTestSupport {

    private static final org.slf4j.Logger LOG =
        org.slf4j.LoggerFactory.getLogger(DockerCliTestSupport.class);

    /**
     * Passed to every {@code docker run}: a daemon with user-namespace remapping refuses
     * {@code --privileged} unless the container opts out of the remap, and on a daemon
     * without remapping the flag changes nothing.
     */
    static final String HOST_USER_NAMESPACE = "--userns=host";

    private DockerCliTestSupport() {
        // utility class
    }

    /**
     * Checks Docker availability using the {@code docker info} CLI command.
     *
     * @return {@code true} if Docker is available and responsive
     */
    static boolean isDockerAvailable() {
        try {
            Process process = new ProcessBuilder("docker", "info")
                .redirectErrorStream(true)
                .start();
            // Drain output to prevent hanging
            try (var reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                while (reader.readLine() != null) {
                    // discard
                }
            }
            boolean exited = process.waitFor(10, TimeUnit.SECONDS);
            return exited && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Detects whether the Docker daemon <em>refused to start</em> the privileged /
     * {@code NET_ADMIN} container these transparent-proxy end-to-end tests require,
     * rather than the container running and the test proper failing.
     * <p>
     * The three transparent-proxy suites need either {@code --privileged} (eBPF) or
     * {@code --cap-add=NET_ADMIN} (SO_ORIGINAL_DST / TPROXY) to set up iptables/BPF
     * inside a sibling container. A daemon configured with <b>user-namespace
     * remapping</b> — as the CI build agents are — rejects {@code --privileged}
     * outright ("privileged mode is incompatible with user namespaces") and can
     * reject added capabilities or the OCI runtime setup. In that case the container
     * never runs, so asserting a clean exit would turn a <em>missing capability</em>
     * into a hard FAILURE. This lets the suites SKIP cleanly instead, so they degrade
     * gracefully wherever the privilege is unavailable (per AGENTS.md).
     * <p>
     * Because {@code redirectErrorStream(true)} folds the {@code docker run} CLI's own
     * stderr into the captured output, the daemon's rejection message is visible here.
     * The markers are specific to container-start refusal, so a genuine in-container
     * test failure is NOT masked.
     *
     * @param dockerRunOutput combined stdout+stderr captured from {@code docker run}
     * @return {@code true} if the daemon refused to start the privileged container
     */
    static boolean containerStartRejected(String dockerRunOutput) {
        if (dockerRunOutput == null) {
            return false;
        }
        String lower = dockerRunOutput.toLowerCase(java.util.Locale.ROOT);
        // Scoped to container-START refusals so a genuine in-container test failure is
        // never masked: user-namespace remapping rejecting --privileged, or the OCI
        // runtime refusing the requested capabilities when the container is created.
        return lower.contains("incompatible with user namespaces")
            || lower.contains("privileged mode is incompatible")
            || (lower.contains("error response from daemon") && lower.contains("cap_"))
            || (lower.contains("oci runtime create failed") && lower.contains("capabilit"));
    }

    /**
     * Runs a process, optionally feeding stdin, and returns the exit code.
     * <p>
     * If the last element of {@code cmdAndStdin} contains "{@code FROM }" it is
     * treated as Dockerfile content to pipe to stdin; the remaining elements form
     * the command array.
     *
     * @param cmdAndStdin command tokens, with an optional Dockerfile-content last element
     * @return the process exit code, or {@code -1} if it did not finish within 120 seconds
     */
    static int runProcess(String... cmdAndStdin) throws IOException, InterruptedException {
        // Last element is stdin content if it contains a Dockerfile directive
        String stdin = null;
        String[] cmd;
        if (cmdAndStdin[cmdAndStdin.length - 1].contains("FROM ")) {
            stdin = cmdAndStdin[cmdAndStdin.length - 1];
            cmd = new String[cmdAndStdin.length - 1];
            System.arraycopy(cmdAndStdin, 0, cmd, 0, cmd.length);
        } else {
            cmd = cmdAndStdin;
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        if (stdin != null) {
            process.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();
        }

        // Drain output
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                LOG.debug("docker build: {}", line);
            }
        }

        boolean finished = process.waitFor(120, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return -1;
        }
        return process.exitValue();
    }

    /**
     * Finds the MockServer fat JAR in the build output directory, or the jar named by the
     * {@code mockserver.e2e.jar} system property (e.g. the shaded mockserver-netty-docker jar the
     * images ship, whose JNA must load from a shaded jar).
     *
     * @return path to the fat JAR, or {@code null} if not found
     */
    static Path findFatJar() {
        String override = System.getProperty("mockserver.e2e.jar", "");
        if (!override.isEmpty()) {
            return Paths.get(override);
        }
        try {
            Path targetDir = Paths.get("target");
            if (!Files.isDirectory(targetDir)) {
                // Try relative to mockserver-netty module
                targetDir = Paths.get("mockserver-netty", "target");
            }
            if (!Files.isDirectory(targetDir)) {
                return null;
            }
            return Files.list(targetDir)
                .filter(p -> {
                    String name = p.getFileName().toString();
                    return name.startsWith("mockserver-netty-")
                        && name.endsWith("-jar-with-dependencies.jar");
                })
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }
}
