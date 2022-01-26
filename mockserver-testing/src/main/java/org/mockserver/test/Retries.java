package org.mockserver.test;

import java.util.concurrent.TimeUnit;

public class Retries {

    public static void tryWaitForSuccess(Runnable runnable, int maxAttempts, long retryInterval, TimeUnit retryIntervalUnits) {
        int attempts = 0;
        Error lastThrown = new AssertionError("fail");
        while (attempts++ < maxAttempts) {
            try {
                runnable.run();
                return;
            } catch (Error throwable) {
                // ignore and try again if allowed
                lastThrown = throwable;
            }
            if (attempts < maxAttempts) {
                try {
                    retryIntervalUnits.sleep(retryInterval);
                } catch (InterruptedException ie) {
                    throw new RuntimeException(ie.getMessage(), ie);
                }
            }
        }
        throw lastThrown;
    }

    public static void tryWaitForSuccess(Runnable runnable) {
        tryWaitForSuccess(runnable, 50, 100, TimeUnit.MILLISECONDS);
    }

}