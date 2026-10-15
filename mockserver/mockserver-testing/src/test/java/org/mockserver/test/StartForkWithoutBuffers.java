package org.mockserver.test;

/**
 * Run in a child JVM by {@link FailOnLeakResourceLeakDetectorTest}: does what surefire does at the start of every
 * test fork (loads the configured run listener) and allocates no Netty buffer.
 */
public final class StartForkWithoutBuffers {

    private StartForkWithoutBuffers() {
    }

    public static void main(String[] args) throws ClassNotFoundException {
        Class.forName(PrintOutCurrentTestRunListener.class.getName(), true, StartForkWithoutBuffers.class.getClassLoader());
    }
}
