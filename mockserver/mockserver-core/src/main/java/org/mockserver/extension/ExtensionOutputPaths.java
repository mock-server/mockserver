package org.mockserver.extension;

import org.mockserver.configuration.Configuration;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Refuses start-up when an enabled output file would be written where extension code is loaded from, so a server
 * can never write a file onto its own extension classpath (an {@code /extensions} volume is meant to be read-only).
 * <p>
 * A directory classpath entry loads any file beneath it, so nothing may be written anywhere inside it. A JAR is
 * picked up by a classpath wildcard over its directory, which is not recursive, so nothing may be written directly
 * into that directory.
 */
final class ExtensionOutputPaths {

    private ExtensionOutputPaths() {
    }

    static void refuseOutputsInsideExtensionCode(Configuration configuration, List<LoadedExtension> extensions) {
        for (LoadedExtension extension : extensions) {
            Path location = codeLocation(extension.extension().getClass());
            if (location != null) {
                String refusal = outputInside(configuration, location);
                if (refusal != null) {
                    throw new ExtensionException(refusal + ", which holds the code of extension " + extension.name()
                        + "; the directory extensions load from must not be written to, so move that output elsewhere");
                }
            }
        }
    }

    /**
     * @return a description of the first enabled output written inside the code at {@code location}, or {@code null}
     */
    static String outputInside(Configuration configuration, Path location) {
        Path codeLocation = location.toAbsolutePath().normalize();
        boolean directory = Files.isDirectory(codeLocation);
        Path jarDirectory = directory ? null : codeLocation.getParent();
        for (Output output : enabledOutputs(configuration)) {
            Path written;
            try {
                written = Paths.get(output.path()).toAbsolutePath().normalize();
            } catch (InvalidPathException e) {
                continue;
            }
            Path writtenInto = output.isDirectory() ? written : written.getParent();
            if (writtenInto == null) {
                continue;
            }
            boolean inside = directory ? writtenInto.startsWith(codeLocation) : writtenInto.equals(jarDirectory);
            if (inside) {
                return output.property() + " " + output.path() + " would be written into " + (directory ? codeLocation : jarDirectory);
            }
        }
        return null;
    }

    private record Output(String property, String path, boolean isDirectory) {
    }

    private static List<Output> enabledOutputs(Configuration configuration) {
        List<Output> outputs = new ArrayList<>();
        if (Boolean.TRUE.equals(configuration.persistExpectations()) && isNotBlank(configuration.persistedExpectationsPath())) {
            outputs.add(new Output("persistedExpectationsPath", configuration.persistedExpectationsPath(), false));
        }
        if (Boolean.TRUE.equals(configuration.persistRecordedExpectations()) && isNotBlank(configuration.persistedRecordedExpectationsPath())) {
            outputs.add(new Output("persistedRecordedExpectationsPath", configuration.persistedRecordedExpectationsPath(), false));
        }
        if (Boolean.TRUE.equals(configuration.persistRecordedRequestsToDisk()) && isNotBlank(configuration.persistedRecordedRequestsPath())) {
            outputs.add(new Output("persistedRecordedRequestsPath", configuration.persistedRecordedRequestsPath(), false));
        }
        if (isNotBlank(configuration.auditLogFile())) {
            outputs.add(new Output("auditLogFile", configuration.auditLogFile(), false));
        }
        if (Boolean.TRUE.equals(configuration.outputMemoryUsageCsv()) && isNotBlank(configuration.memoryUsageCsvDirectory())) {
            outputs.add(new Output("memoryUsageCsvDirectory", configuration.memoryUsageCsvDirectory(), true));
        }
        return outputs;
    }

    private static Path codeLocation(Class<?> type) {
        try {
            CodeSource codeSource = type.getProtectionDomain().getCodeSource();
            URL location = codeSource != null ? codeSource.getLocation() : null;
            if (location != null && "file".equals(location.getProtocol())) {
                return Paths.get(location.toURI());
            }
        } catch (Exception ignore) {
            // a location that is not a local file cannot be written to by these outputs
        }
        return null;
    }
}
