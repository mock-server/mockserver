package org.mockserver.lifecycle;

import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.extension.Extension;
import org.mockserver.netty.MockServer;

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

/**
 * With no extension configured a server must load no {@code org.mockserver.extension} class, and so cannot have
 * asked {@code ServiceLoader} for extensions (which needs the {@code Extension} class), not even while it serves a
 * control-plane request, a path under {@code /mockserver/extensions/} and a data-plane request. Runs a server in a fresh JVM
 * that logs every class it loads, since this JVM has loaded the extension classes for other tests; a second run
 * with an extension configured shows the log does see them.
 */
public class ExtensionClassLoadingTest {

    private static final Pattern LOADED_CLASS = Pattern.compile("\\[class,load\\s*] (\\S+) source:");

    @Test
    public void shouldLoadNoExtensionClassWhenNoExtensionIsConfigured() throws Exception {
        ProbeRun run = runProbe();

        assertThat(run.output, run.completed, is(true));
        assertThat("the probe must have started a server: " + run.output, run.loadedClasses, hasItem("org.mockserver.netty.MockServer"));
        assertThat(extensionClasses(run.loadedClasses), is(empty()));
    }

    @Test
    public void shouldSeeTheExtensionClassesLoadWhenAnExtensionIsConfigured() throws Exception {
        ProbeRun run = runProbe(Probe.ConfiguredExtension.class.getName());

        assertThat(run.output, run.completed, is(true));
        assertThat(extensionClasses(run.loadedClasses), hasItem("org.mockserver.extension.ExtensionRegistry"));
        assertThat(extensionClasses(run.loadedClasses), hasItem("org.mockserver.extension.Extension"));
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
            "-Dmockserver.startupWarmup=false",
            "-cp", System.getProperty("java.class.path"),
            Probe.class.getName()
        ));
        command.addAll(Arrays.asList(arguments));
        // output goes to a file, not a pipe, so waitFor's bound holds even if the probe hangs
        Path outputFile = Files.createTempFile("extension-class-loading-probe", ".log");
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(outputFile.toFile());
            processBuilder.environment().remove("JAVA_TOOL_OPTIONS");
            Process process = processBuilder.start();
            boolean exited = process.waitFor(120, TimeUnit.SECONDS);
            if (!exited) {
                process.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
            }
            return parse(exited && process.exitValue() == 0, new String(Files.readAllBytes(outputFile), StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(outputFile);
        }
    }

    private static ProbeRun parse(boolean exitedCleanly, String text) {
        List<String> loadedClasses = new ArrayList<>();
        Matcher matcher = LOADED_CLASS.matcher(text);
        while (matcher.find()) {
            loadedClasses.add(matcher.group(1));
        }
        boolean completed = exitedCleanly && text.contains(Probe.STOPPED);
        return new ProbeRun(completed, loadedClasses, completed ? "" : text.substring(Math.max(0, text.length() - 4000)));
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
        static final String STOPPED = "EXTENSION-PROBE-STOPPED";

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
            MockServer mockServer = new MockServer(configuration);
            try {
                // a control-plane route, a path under /mockserver/extensions/ and a data-plane request
                send(mockServer.getLocalPort(), "PUT", "/mockserver/clear");
                send(mockServer.getLocalPort(), "GET", "/mockserver/extensions/class-loading-probe/x");
                send(mockServer.getLocalPort(), "GET", "/data");
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
            mockServer.stop();
            System.out.println(STOPPED);
            System.exit(0);
        }

        private static void send(int port, String method, String path) throws Exception {
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL("http://127.0.0.1:" + port + path).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(30_000);
            if (connection.getResponseCode() <= 0) {
                throw new IllegalStateException("no response to " + method + " " + path);
            }
            connection.disconnect();
        }
    }
}
