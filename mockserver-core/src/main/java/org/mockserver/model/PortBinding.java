package org.mockserver.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class PortBinding extends ObjectWithJsonToString {

    private List<Integer> ports = new ArrayList<Integer>();

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
}
