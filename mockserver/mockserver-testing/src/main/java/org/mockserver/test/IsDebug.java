package org.mockserver.test;

import java.util.concurrent.TimeUnit;

import static java.lang.management.ManagementFactory.getRuntimeMXBean;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

public class IsDebug {

    static {
        System.setProperty("junit.jupiter.extensions.autodetection.enabled", "true");
    }

    public static final boolean IS_DEBUG = getRuntimeMXBean().getInputArguments().toString().indexOf("-agentlib:jdwp") > 0;

    public static TimeUnit timeoutUnits() {
        if (IS_DEBUG) {
            return MINUTES;
        } else {
            return SECONDS;
        }
    }

}
