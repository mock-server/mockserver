package org.mockserver.test;

/**
 * Size-parameterised, deterministic response bodies for HTTP/2 integration tests.
 *
 * <p><b>Why this exists.</b> A body that fits in the peer's first
 * {@value #FLOW_CONTROL_WINDOW_BYTES}-byte flow-control window is delivered without any
 * {@code WINDOW_UPDATE}, so the {@code writePendingBytes()} flush is never exercised and its defects —
 * whose failure mode is a <b>silent hang</b> — are invisible to the test. See
 * {@code docs/code/netty-pipeline.md} ("Testing convention: an HTTP/2 test MUST use a response body
 * larger than the flow-control window").
 *
 * <p><b>What this gives you.</b> A named set of sizes ({@link Size#SMALL}, {@link Size#OVER_WINDOW},
 * {@link Size#LARGE}) so a new HTTP/2 test crosses the window <b>by construction</b> — the author picks
 * {@code OVER_WINDOW} without having to know the window size or type a magic number. Bodies are
 * <b>deterministic and distinguishable</b>: the content is stamped throughout with a caller-supplied
 * marker, so a mis-routed body (delivered on the wrong h2 stream) or a truncated body fails an
 * <em>equality</em> assertion, not merely a length check.
 *
 * <p>Lives in {@code mockserver-testing} (package {@code org.mockserver.test}) rather than in a single
 * test tree because it is a compile-scope dependency of {@code mockserver-integration-testing} and a
 * test dependency of {@code mockserver-netty}, {@code mockserver-war} and the rest — so every module
 * with HTTP/2 tests can reach it. It has no dependency beyond the JDK.
 */
public final class Http2FlowControlBodies {

    /**
     * The HTTP/2 initial flow-control window in bytes, per RFC 7540 / 9113 §6.9.2. A response at or
     * under this size is delivered inside the peer's first window and never drives a
     * {@code WINDOW_UPDATE}, so it cannot exercise the flow-control flush. This is the threshold every
     * {@link Size#OVER_WINDOW} body must clear.
     */
    public static final int FLOW_CONTROL_WINDOW_BYTES = 65_535;

    /**
     * Named body sizes for HTTP/2 tests.
     *
     * <p>{@link #OVER_WINDOW} is the load-bearing one: it is the reason this class exists and the only
     * size that exercises the {@code WINDOW_UPDATE}-driven flush. It is fixed at 256 KB — comfortably
     * over the {@value #FLOW_CONTROL_WINDOW_BYTES}-byte window, spanning the per-stream window, the
     * connection window and many DATA frames.
     *
     * <p><b>DO NOT shrink {@code OVER_WINDOW} to at or below {@value #FLOW_CONTROL_WINDOW_BYTES}.</b>
     * Doing so makes every test built on it incapable of catching the flow-control hang it exists to
     * lock. The static
     * initialiser below fails the build if that invariant is ever broken, so an accidental shrink
     * cannot compile-and-pass — it errors loudly at class load.
     */
    public enum Size {
        /** ~1 KB — fits comfortably inside a single flow-control window; use only where body size is irrelevant. */
        SMALL(1_024),
        /**
         * 256 KB — <b>exceeds the {@value #FLOW_CONTROL_WINDOW_BYTES}-byte flow-control window</b> and is
         * the size that makes an HTTP/2 test able to catch the {@code channelReadComplete} flush family.
         * This is the default choice for any HTTP/2 body assertion. Do not shrink it (see enum javadoc).
         */
        OVER_WINDOW(262_144),
        /** ~1 MB — well over the window, for tests that want a body spanning very many DATA frames. */
        LARGE(1_048_576);

        private final int bytes;

        Size(int bytes) {
            this.bytes = bytes;
        }

        /** The body length in bytes this size produces. */
        public int bytes() {
            return bytes;
        }
    }

    static {
        // Fail-closed invariant: the whole point of OVER_WINDOW is to cross the flow-control window.
        // If a future edit shrinks it to at or under the window this errors at class load, so the
        // blind spot cannot be silently reintroduced by editing a single number.
        if (Size.OVER_WINDOW.bytes() <= FLOW_CONTROL_WINDOW_BYTES) {
            throw new ExceptionInInitializerError(
                "Http2FlowControlBodies.Size.OVER_WINDOW (" + Size.OVER_WINDOW.bytes() + " bytes) must exceed the "
                    + FLOW_CONTROL_WINDOW_BYTES + "-byte HTTP/2 flow-control window; shrinking it restores the "
                    + "sub-window blind spot that hid #2641/#2667/#2669/#2683 — see this class's javadoc.");
        }
    }

    private Http2FlowControlBodies() {
    }

    /**
     * A deterministic body of exactly {@code size} bytes whose filler is stamped throughout with
     * {@code marker}, so a mis-routed or truncated body cannot equal the expected one anywhere along
     * its length. Give each concurrently-multiplexed stream a distinct marker so a body delivered on
     * the wrong stream fails an equality assertion.
     *
     * @param size   the named body size (use {@link Size#OVER_WINDOW} for any real HTTP/2 assertion)
     * @param marker a short label stamped throughout the body; must be non-empty
     * @return a String of exactly {@link Size#bytes()} characters
     */
    public static String body(Size size, String marker) {
        if (marker == null || marker.isEmpty()) {
            throw new IllegalArgumentException("marker must be non-empty so the body is distinguishable");
        }
        int length = size.bytes();
        String unit = "[" + marker + "]";
        StringBuilder builder = new StringBuilder(length + unit.length());
        while (builder.length() < length) {
            builder.append(unit);
        }
        return builder.substring(0, length);
    }
}
