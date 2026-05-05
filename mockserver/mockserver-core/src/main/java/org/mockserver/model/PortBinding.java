package org.mockserver.model;

import org.mockserver.version.Version;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class PortBinding extends ObjectWithJsonToString {

    private static final String VERSION = Version.getVersion();
    private static final String ARTIFACT_ID = Version.getArtifactId();
    private static final String GROUP_ID = Version.getGroupId();

    private List<Integer> ports = new ArrayList<>();
    private final String version = VERSION;
    private final String artifactId = ARTIFACT_ID;
    private final String groupId = GROUP_ID;

    public static PortBinding portBinding(Integer... ports) {
        return portBinding(Arrays.asList(ports));
    }

    public static PortBinding portBinding(List<Integer> ports) {
        return new PortBinding().setPorts(ports);
    }

    public List<Integer> getPorts() {
        return ports;
    }

    public PortBinding setPorts(List<Integer> ports) {
        this.ports = ports;
        return this;
    }

    public String getVersion() {
        return version;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getGroupId() {
        return groupId;
    }
}
