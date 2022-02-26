package org.mockserver.netty.integration;

import com.google.common.base.Joiner;
import org.mockserver.client.MockServerClient;
import org.mockserver.file.FileReader;
import org.mockserver.netty.integration.mock.ExtendedShadedJarMockingIntegrationTest;
import org.mockserver.version.Version;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class ShadedJarRunner {

    public static MockServerClient startServerUsingShadedJar(int mockServerPort) {
        File jarFile = new File(System.getProperty("project.basedir", ".") + "/target/mockserver-netty-" + System.getProperty("project.version", Version.getVersion()) + "-shaded.jar");
        if (!jarFile.exists()) {
            String defaultLocation = jarFile.getAbsolutePath();
            jarFile = new File(System.getProperty("project.basedir", ".") + "/mockserver-netty/target/mockserver-netty-" + System.getProperty("project.version", Version.getVersion()) + "-shaded.jar");
            if (!jarFile.exists()) {
                throw new RuntimeException("Can't find jar file in the following locations: " + Arrays.asList(defaultLocation, jarFile.getAbsolutePath()));
            }
        }
        List<String> arguments = new ArrayList<>(Collections.singletonList(getJavaBin()));
        arguments.add("-Dfile.encoding=UTF-8");
        arguments.add("-jar");
        arguments.add(jarFile.getAbsolutePath());
        arguments.add("-serverPort");
        arguments.add("" + mockServerPort);
        String message = Joiner.on(" ").join(arguments);
        ProcessBuilder processBuilder = new ProcessBuilder(arguments);
        processBuilder.inheritIO();
        File stderr = new File(ExtendedShadedJarMockingIntegrationTest.class.getSimpleName() + "_stderr.log");
        stderr.deleteOnExit();
        processBuilder.redirectError(ProcessBuilder.Redirect.to(stderr));
        File stdout = new File(ExtendedShadedJarMockingIntegrationTest.class.getSimpleName() + "_stdout.log");
        stdout.deleteOnExit();
        processBuilder.redirectOutput(ProcessBuilder.Redirect.to(stdout));
        try {
            processBuilder.start();
        } catch (IOException ioe) {
            printOutputStreams();
            throw new RuntimeException("Exception starting via shaded jar " + jarFile.getAbsolutePath(), ioe);
        }
        MockServerClient mockServerClient = new MockServerClient("localhost", mockServerPort);
        if (!mockServerClient.hasStarted()) {
            printOutputStreams();
        }
        return mockServerClient;
    }

    private static void printOutputStreams() {
        System.err.println("stderr:\n\n" + FileReader.readFileFromClassPathOrPath(ExtendedShadedJarMockingIntegrationTest.class.getSimpleName() + "_stderr.log"));
        try {
            // ensure streams don't get intermingled
            MILLISECONDS.sleep(150);
        } catch (InterruptedException ignore) {
        }
        System.out.println("stout:\n\n" + FileReader.readFileFromClassPathOrPath(ExtendedShadedJarMockingIntegrationTest.class.getSimpleName() + "_stdout.log"));
    }

    private static String getJavaBin() {
        String javaBinary = "java";

        File javaHomeDirectory = new File(System.getProperty("java.home"));
        for (String javaExecutable : new String[]{"java", "java.exe"}) {
            File javaExeLocation = new File(javaHomeDirectory, fileSeparators("bin/" + javaExecutable));
            if (javaExeLocation.exists() && javaExeLocation.isFile()) {
                javaBinary = javaExeLocation.getAbsolutePath();
                break;
            }
        }

        return javaBinary;
    }

    private static String fileSeparators(String path) {
        StringBuilder builder = new StringBuilder();
        for (char c : path.toCharArray()) {
            if ((c == '/') || (c == '\\')) {
                builder.append(File.separatorChar);
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }
}
