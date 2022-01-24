package org.mockserver.java;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.StringUtils;

public class Version {

    @VisibleForTesting
    public static String javaVersion = System.getProperty("java.version");

    public static int getVersion() {
        String version = javaVersion;
        if (version.startsWith("1.")) {
            version = StringUtils.substringAfter(version, ".");
        }
        return Integer.parseInt(StringUtils.substringBefore(version, "."));
    }

}
