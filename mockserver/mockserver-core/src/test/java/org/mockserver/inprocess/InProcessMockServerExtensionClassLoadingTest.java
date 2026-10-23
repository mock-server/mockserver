package org.mockserver.inprocess;

import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.extension.Extension;
import org.mockserver.mock.Expectation;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * With no extension configured an in-process MockServer loads no {@code org.mockserver.extension} class, while it
 * starts, serves a control-plane request, a path under {@code /mockserver/extensions/} and a data-plane request, and
 * closes. Runs in a fresh JVM that logs every class it loads, since this JVM has loaded the extension classes for
 * other tests; a second run with an extension configured shows the log does see them.
 */
public class InProcessMockServerExtensionClassLoadingTest {

    private static final Pattern LOADED_CLASS = Pattern.compile("\\[class,load\\s*] (\\S+) source:");

    @Test
    public void shouldLoadNoExtensionClassWhenNoExtensionIsConfigured() throws Exception {
        ProbeRun run = runProbe();

        assertThat(run.output, run.completed, is(true));
        assertThat("the probe must have built an in-process server: " + run.output, run.loadedClasses, hasItem(InProcessMockServer.class.getName()));
        assertThat(extensionClasses(run.loadedClasses), is(empty()));
    }

    @Test
    public void shouldSeeTheExtensionClassesLoadWhenAnExtensionIsConfigured() throws Exception {
        ProbeRun run = runProbe(Probe.ConfiguredExtension.class.getName());

        assertThat(run.output, run.completed, is(true));
        assertThat(extensionClasses(run.loadedClasses), hasItem("org.mockserver.extension.ExtensionRegistry"));
    }

    private static List<String> extensionClasses(List<String> loadedClasses) {
        List<String> extensionClasses = new ArrayList<>();
        for (String loadedClass : loadedClasses) {
            if (loadedClass.startsWith("org.mockserver.extension.")) {
                extensionClasses.add(loadedClass);
            }
        }
        return extensionClasses;
    }

    private static ProbeRun runProbe(String... arguments) throws Exception {
        List<String> command = new ArrayList<>(Arrays.asList(
            System.getProperty("java.home") + File.separator + "bin" + File.separator + "java",
            "-Xmx256m",
            "-Xlog:class+load=info:stdout",
            "-Dmockserver.logLevel=ERROR",
            "-cp", System.getProperty("java.class.path"),
            Probe.class.getName()
        ));
        command.addAll(Arrays.asList(arguments));
        // output goes to a file, not a pipe, so waitFor's bound holds even if the probe hangs
        Path outputFile = Files.createTempFile("in-process-extension-class-loading-probe", ".log");
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(outputFile.toFile());
            processBuilder.environment().remove("JAVA_TOOL_OPTIONS");
            Process process = processBuilder.start();
            boolean exited = process.waitFor(120, TimeUnit.SECONDS);
            if (!exited) {
                process.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
            }
            String text = new String(Files.readAllBytes(outputFile), StandardCharsets.UTF_8);
            List<String> loadedClasses = new ArrayList<>();
            Matcher matcher = LOADED_CLASS.matcher(text);
            while (matcher.find()) {
                loadedClasses.add(matcher.group(1));
            }
            boolean completed = exited && process.exitValue() == 0 && text.contains(Probe.CLOSED);
            return new ProbeRun(completed, loadedClasses, completed ? "" : text.substring(Math.max(0, text.length() - 4000)));
        } finally {
            Files.deleteIfExists(outputFile);
        }
    }

    private static final class ProbeRun {
        private final boolean completed;
        private final List<String> loadedClasses;
        private final String output;

        private ProbeRun(boolean completed, List<String> loadedClasses, String output) {
            this.completed = completed;
            this.loadedClasses = loadedClasses;
            this.output = output;
        }
    }

    public static class Probe {
        static final String CLOSED = "IN-PROCESS-EXTENSION-PROBE-CLOSED";

        public static class ConfiguredExtension implements Extension {
            @Override
            public String name() {
                return "class-loading-probe";
            }
        }

        public static void main(String[] arguments) {
            Configuration configuration = configuration();
            if (arguments.length > 0) {
                configuration.extensions(arguments[0]);
            }
            try (InProcessMockServer server = new InProcessMockServer(configuration)) {
                server.upsert(new Expectation(request().withPath("/probe")).thenRespond(response().withBody("ok")));
                expect(server, request().withMethod("PUT").withPath("/mockserver/retrieve").withQueryStringParameter("type", "ACTIVE_EXPECTATIONS"), 200);
                expect(server, request().withMethod("GET").withPath("/mockserver/extensions/class-loading-probe/x"), 404);
                expect(server, request().withMethod("GET").withPath("/probe"), 200);
                expect(server, request().withMethod("GET").withPath("/not-matched"), 404);
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
            System.out.println(CLOSED);
            System.exit(0);
        }

        private static void expect(InProcessMockServer server, org.mockserver.model.HttpRequest request, int expectedStatus) {
            int status = server.handle(request).getStatusCode();
            if (status != expectedStatus) {
                throw new IllegalStateException(request.getMethod() + " " + request.getPath() + " returned " + status + " not " + expectedStatus);
            }
        }
    }
}
