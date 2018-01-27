package org.mockserver;

public final class Version {

    private static final String VERSION = "${project.version}";
    private static final String GROUPID = "${project.groupId}";
    private static final String ARTIFACTID = "${project.artifactId}";

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

    public static String getGroupId() {
        return getValue(GROUPID, "");
    }

    public static String getArtifactId() {
        return getValue(ARTIFACTID, "");
    }

}