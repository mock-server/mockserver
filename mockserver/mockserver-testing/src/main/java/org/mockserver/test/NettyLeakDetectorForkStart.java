package org.mockserver.test;

import io.netty.util.ResourceLeakDetectorFactory;

/**
 * Makes a test fork that is configured with a custom Netty leak detector load it at fork start, through
 * Netty's own factory, so {@link FailOnLeakResourceLeakDetector} writes its installed marker even when no test
 * in the fork allocates a buffer. Without this, a fork whose tests never touch a {@code ByteBuf} wrote no
 * marker and the build's leak gate reported that it had checked nothing.
 * <p>
 * A fork without {@code -Dio.netty.customResourceLeakDetector} still writes no marker, so the gate still
 * fails when tests ran without the detector.
 */
public final class NettyLeakDetectorForkStart {

    static final String CUSTOM_DETECTOR_PROPERTY = "io.netty.customResourceLeakDetector";

    private NettyLeakDetectorForkStart() {
    }

    public static void installIfConfigured() {
        String detector = System.getProperty(CUSTOM_DETECTOR_PROPERTY);
        if (detector == null || detector.isEmpty()) {
            return;
        }
        try {
            NettyFactory.load();
        } catch (LinkageError | RuntimeException e) {
            System.err.println("WARNING: could not load Netty leak detector " + detector + " at fork start: " + e);
        }
    }

    // a separate class, so a module whose test classpath lacks Netty never loads ResourceLeakDetectorFactory
    private static final class NettyFactory {
        static void load() {
            ResourceLeakDetectorFactory.instance().newResourceLeakDetector(NettyLeakDetectorForkStart.class);
        }
    }
}
