package org.mockserver.extension;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThrows;
import static org.mockserver.configuration.Configuration.configuration;

public class ExtensionOutputPathsTest {

    static final AtomicInteger STARTS = new AtomicInteger();

    public static class Counting implements Extension {
        @Override
        public String name() {
            return "output-paths-counting";
        }

        @Override
        public void start(ExtensionContext context) {
            STARTS.incrementAndGet();
        }
    }

    @Before
    public void resetStarts() {
        STARTS.set(0);
    }

    @Test
    public void shouldRefuseStartWithoutStartingAnythingWhenAnOutputIsWrittenIntoTheExtensionsClassDirectory() throws URISyntaxException {
        Path classes = Paths.get(Counting.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Configuration configuration = noOutputs().extensions(Counting.class)
            .persistExpectations(true)
            .persistedExpectationsPath(classes.resolve("org/acme/persisted.json").toString());

        ExtensionException refused = assertThrows(ExtensionException.class, () -> ExtensionRegistry.start(configuration, new MockServerLogger()));

        assertThat(refused.getMessage(), containsString("persistedExpectationsPath"));
        assertThat(refused.getMessage(), containsString("output-paths-counting"));
        assertThat(STARTS.get(), is(0));
    }

    @Test
    public void shouldStartWhenNoEnabledOutputIsWrittenIntoExtensionCode() throws URISyntaxException {
        Path classes = Paths.get(Counting.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Configuration configuration = noOutputs().extensions(Counting.class)
            .persistExpectations(false)
            .persistedExpectationsPath(classes.resolve("persisted.json").toString());

        ExtensionRegistry registry = ExtensionRegistry.start(configuration, new MockServerLogger());
        registry.stop();

        assertThat(STARTS.get(), is(1));
    }

    @Test
    public void shouldRefuseEachEnabledOutputAnywhereInsideAClassDirectory() throws IOException {
        Path classes = Files.createTempDirectory("extension-classes");
        Path nested = classes.resolve("com/acme");

        assertThat(ExtensionOutputPaths.outputInside(noOutputs().persistExpectations(true).persistedExpectationsPath(nested.resolve("e.json").toString()), classes),
            containsString("persistedExpectationsPath"));
        assertThat(ExtensionOutputPaths.outputInside(noOutputs().persistRecordedExpectations(true).persistedRecordedExpectationsPath(nested.resolve("x.json").toString()), classes),
            containsString("persistedRecordedExpectationsPath"));
        assertThat(ExtensionOutputPaths.outputInside(noOutputs().persistRecordedRequestsToDisk(true).persistedRecordedRequestsPath(nested.resolve("r.ndjson").toString()), classes),
            containsString("persistedRecordedRequestsPath"));
        assertThat(ExtensionOutputPaths.outputInside(noOutputs().auditLogFile(classes.resolve("audit.ndjson").toString()), classes),
            containsString("auditLogFile"));
        assertThat(ExtensionOutputPaths.outputInside(noOutputs().outputMemoryUsageCsv(true).memoryUsageCsvDirectory(classes.toString()), classes),
            containsString("memoryUsageCsvDirectory"));
        assertThat(ExtensionOutputPaths.outputInside(noOutputs().auditLogFile(classes.resolve("../outside/audit.ndjson").toString()), classes), nullValue());
    }

    @Test
    public void shouldRefuseOnlyOutputsWrittenDirectlyIntoAJarsDirectory() throws IOException {
        Path extensions = Files.createTempDirectory("extensions");
        Path jar = Files.createFile(extensions.resolve("acme.jar"));

        assertThat(ExtensionOutputPaths.outputInside(noOutputs().persistExpectations(true).persistedExpectationsPath(extensions.resolve("e.json").toString()), jar),
            containsString("would be written into " + extensions.toAbsolutePath().normalize()));
        assertThat(ExtensionOutputPaths.outputInside(noOutputs().persistExpectations(true).persistedExpectationsPath(extensions.resolve("sub/../e.jar").toString()), jar),
            containsString("persistedExpectationsPath"));
        assertThat(ExtensionOutputPaths.outputInside(noOutputs().outputMemoryUsageCsv(true).memoryUsageCsvDirectory(extensions.toString()), jar),
            containsString("memoryUsageCsvDirectory"));
        // a classpath wildcard over the JAR's directory is not recursive
        assertThat(ExtensionOutputPaths.outputInside(noOutputs().persistExpectations(true).persistedExpectationsPath(extensions.resolve("sub/e.json").toString()), jar), nullValue());
        assertThat(ExtensionOutputPaths.outputInside(noOutputs().outputMemoryUsageCsv(true).memoryUsageCsvDirectory(extensions.resolve("csv").toString()), jar), nullValue());
    }

    @Test
    public void shouldIgnoreOutputsThatAreNotEnabled() throws IOException {
        Path classes = Files.createTempDirectory("extension-classes");
        String inside = classes.resolve("out").toString();

        Configuration disabled = noOutputs()
            .persistedExpectationsPath(inside)
            .persistedRecordedRequestsPath(inside)
            .persistedRecordedExpectationsPath(inside)
            .memoryUsageCsvDirectory(inside);

        assertThat(ExtensionOutputPaths.outputInside(disabled, classes), nullValue());
    }

    private static Configuration noOutputs() {
        return configuration()
            .persistExpectations(false)
            .persistRecordedRequestsToDisk(false)
            .persistRecordedExpectations(false)
            .auditLogFile("")
            .outputMemoryUsageCsv(false);
    }
}
