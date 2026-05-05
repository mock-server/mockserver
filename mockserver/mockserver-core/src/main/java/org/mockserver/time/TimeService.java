package org.mockserver.time;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;

public class TimeService {

    public static final Instant FIXED_INSTANT_FOR_TESTS = Instant.now();
    public static boolean fixedTime = false;

    public static Instant now() {
        if (!fixedTime) {
            return Instant.now();
        } else {
            return FIXED_INSTANT_FOR_TESTS;
        }
    }

    public static OffsetDateTime offsetNow() {
        Instant now = TimeService.now();
        return OffsetDateTime.ofInstant(now, Clock.systemDefaultZone().getZone().getRules().getOffset(now));
    }

}
