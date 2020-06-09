package org.mockserver;

import org.apache.commons.lang3.StringUtils;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public final class Version {

    private static final String VERSION = "${project.version}";
    private static final String GROUPID = "${project.groupId}";
    private static final String ARTIFACTID = "${project.artifactId}";
    private static String majorMinorVersion = null;

    private static String getValue(String value, String defaultValue) {
        if (!value.startsWith("$")) {
            return value;
        } else {
            return defaultValue;
        }
    }

    public static String getVersion() {
        return getValue(VERSION, System.getProperty("MOCKSERVER_VERSION", ""));
    }

    public static String getMajorMinorVersion() {
        if (majorMinorVersion == null) {
            majorMinorVersion = StringUtils.substringBeforeLast(getValue(VERSION, System.getProperty("MOCKSERVER_VERSION", "")), ".");
        }
        return majorMinorVersion;
    }

    public static boolean matchesMajorMinorVersion(String version) {
        boolean matches = true;
        if (isNotBlank(version) && isNotBlank(getMajorMinorVersion())) {
            matches = getMajorMinorVersion().equals(StringUtils.substringBeforeLast(version, "."));
        }
        return matches;
    }

    public static String getGroupId() {
        return getValue(GROUPID, "");
    }

    public static String getArtifactId() {
        return getValue(ARTIFACTID, "");
    }

}