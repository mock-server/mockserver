package org.mockserver.uuid;

import java.util.UUID;

public class UUIDService {

    public static final String FIXED_UUID_FOR_TESTS = UUIDService.getUUID();
    public static boolean fixedUUID = false;

    public static String getUUID() {
        if (!fixedUUID) {
            return UUID.randomUUID().toString();
        } else {
            return FIXED_UUID_FOR_TESTS;
        }
    }

}
