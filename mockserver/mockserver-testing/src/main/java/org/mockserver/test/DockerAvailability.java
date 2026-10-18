package org.mockserver.test;

import java.util.function.BooleanSupplier;

/**
 * Fail-safe wrapper for the Docker availability probe used by Docker-gated tests.
 * <p>
 * Docker-gated suites are written as:
 * <pre>{@code
 * Assume.assumeTrue("Docker is not available",
 *     DockerAvailability.isAvailable(() -> DockerClientFactory.instance().isDockerAvailable()));
 * }</pre>
 * so they SKIP where Docker is unusable and RUN where it is usable. That design only holds if
 * the probe yields a boolean in every circumstance.
 * <p>
 * <strong>Testcontainers' own probe does not.</strong>
 * {@code DockerClientFactory.isDockerAvailable()} is documented as "true if Docker is
 * available, false if not", but is implemented as
 * {@code try { client(); return true; } catch (IllegalStateException ex) { return false; }} —
 * only {@link IllegalStateException} becomes {@code false}. {@code client()} does much more
 * than connect: it starts the Ryuk reaper via {@code ResourceReaper.instance().init()} and runs
 * version/mount checks, and those stages throw types that are NOT {@code IllegalStateException}:
 * <ul>
 *   <li>{@code com.github.dockerjava.api.exception.BadRequestException} (a
 *       {@code DockerException}, hence a plain {@code RuntimeException}) — e.g. a daemon with
 *       user-namespace remapping rejecting Ryuk's privileged container with
 *       "privileged mode is incompatible with user namespaces";</li>
 *   <li>{@code ContainerFetchException} / {@code ContainerLaunchException} — Ryuk's image cannot
 *       be pulled, or its container cannot start;</li>
 *   <li>{@link Error}s such as {@link NoClassDefFoundError} or
 *       {@code ExceptionInInitializerError} — an incomplete or conflicting test classpath.</li>
 * </ul>
 * Any of these escapes the probe and turns "no usable Docker" into a hard ERROR, defeating the
 * {@code assume} guard everywhere it is used — in CI, and equally for a contributor whose Docker
 * is configured unusually. Testcontainers also caches the failure in {@code cachedClientFailure}
 * and rethrows it on every later call, so a single bad probe fails every Docker-gated test in
 * the JVM.
 * <p>
 * <strong>Why a {@link BooleanSupplier} rather than calling Testcontainers directly:</strong>
 * this module is the shared testing utility module and deliberately depends on almost nothing
 * (JUnit and Hamcrest only). Adding Testcontainers here purely for this helper would widen that
 * surface for every consumer, which the repository-wide {@code dependencyConvergence} enforcer
 * rule makes a real risk. Taking the probe as a lambda keeps one shared implementation without
 * the coupling, and keeps it compile-time type-safe — a reflective lookup would silently degrade
 * to "unavailable" if the method were renamed, silently skipping every Docker-gated test, which
 * is the exact failure mode this helper exists to prevent.
 * <p>
 * <strong>Pass a lambda, not a method reference.</strong> Use
 * {@code () -> DockerClientFactory.instance().isDockerAvailable()} rather than
 * {@code DockerClientFactory.instance()::isDockerAvailable}: a method reference evaluates
 * {@code instance()} eagerly at the call site, outside this class's try/catch, so a failure
 * constructing the factory would still escape.
 * <p>
 * <strong>In CI, pair this with a fail-closed assertion.</strong> Being fail-SAFE is a deliberate
 * trade: an unusable Docker becomes a SKIP rather than an ERROR, which is right off-CI but is a
 * silent false positive in CI, where skipping proves nothing. Note this WIDENS the silent-skip
 * surface for a suite that previously guarded with {@code catch (Exception e)}, because an
 * {@link Error} used to escape that catch and fail loudly. Every Docker-gated suite that runs in CI
 * should therefore have its surefire/failsafe reports covered by
 * {@code .buildkite/scripts/steps/assert-suite-ran.sh}, which fails the build when a report shows
 * zero tests or all-skipped.
 * <p>
 * The CI steps that do this are listed in {@code docs/infrastructure/ci-cd.md} ("Gates That Must
 * Fail Closed"). Add the assertion only once the step can actually run the suite: a fail-closed
 * assertion is only safe once the suite can actually pass, and before that it turns the pipeline
 * permanently red rather than closing a gap.
 */
public final class DockerAvailability {

    private static final Object LOCK = new Object();

    /**
     * Cached so a slow or failing probe is paid once per JVM rather than once per test class,
     * and so the diagnostic below is printed once. Testcontainers caches its own failure and
     * rethrows it, so without this every call repeats the same work to reach the same answer.
     * <p>
     * The cache is keyed on nothing, so it assumes ONE logical probe per JVM — i.e. all callers
     * in a given JVM are asking the same question ("can Testcontainers use Docker?"). That holds
     * because Surefire forks per Maven module and no module mixes probe implementations. If a
     * module ever needs to ask a genuinely different question (for example the {@code docker
     * info} CLI probe in {@code mockserver-netty}'s {@code DockerCliTestSupport}, which is
     * already fail-safe and deliberately left alone), do not route it through here without
     * keying the cache.
     */
    private static Boolean cachedResult;

    private DockerAvailability() {
        // utility class
    }

    /**
     * @param probe the underlying availability check, e.g.
     *              {@code () -> DockerClientFactory.instance().isDockerAvailable()}
     * @return {@code true} only when the probe returns {@code true}; {@code false} for every
     * failure mode, including those the probe throws rather than reports
     */
    public static boolean isAvailable(BooleanSupplier probe) {
        synchronized (LOCK) {
            if (cachedResult == null) {
                cachedResult = safeProbe(probe);
            }
            return cachedResult;
        }
    }

    private static boolean safeProbe(BooleanSupplier probe) {
        try {
            return probe.getAsBoolean();
        } catch (VirtualMachineError e) {
            // Never swallow JVM-fatal errors (OutOfMemoryError, StackOverflowError, ...):
            // reporting those as "Docker unavailable" would hide a real failure behind a skip.
            throw e;
        } catch (Throwable t) {
            // Deliberately broad. This wrapper's whole job is to answer a yes/no question, so no
            // failure of the probe itself may fail the build; the alternative is an ERROR in every
            // Docker-gated suite. Printed rather than logged so the reason is visible in surefire
            // output even where no test logger is configured.
            System.out.println(
                "Docker probe failed - treating Docker as UNAVAILABLE, Docker-gated tests will be skipped: "
                    + t.getClass().getName() + ": " + t.getMessage()
            );
            return false;
        }
    }

    /**
     * Resets the per-JVM cache. Exposed for {@code DockerAvailabilityTest}, which must exercise
     * several probe outcomes in one JVM.
     */
    public static void resetCacheForTest() {
        synchronized (LOCK) {
            cachedResult = null;
        }
    }
}
