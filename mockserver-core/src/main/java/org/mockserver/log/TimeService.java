package org.mockserver.log;

public class TimeService {

    public static final long FIXED_TIME_FOR_TESTS = System.currentTimeMillis();
    public static boolean fixedTime = false;

    public static long currentTimeMillis() {
        if (!fixedTime) {
            return System.currentTimeMillis();
        } else {
            return FIXED_TIME_FOR_TESTS;
        }
    }

}
