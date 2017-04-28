package org.mockserver.maven;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class ProcessBuildFactory {

    protected ProcessBuilder create(List<String> arguments) {
        return new ProcessBuilder(arguments);
    }
}
