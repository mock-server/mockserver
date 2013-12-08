package org.mockserver.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;

/**
 * @author jamesdbloom
 */
@Mojo(name = "runForked", requiresProject = false, threadSafe = false)
public class MockServerRunForkedMojo extends AbstractMojo {

    /**
     * The port to run Mock Server on
     */
    @Parameter(property = "mockserver.port", defaultValue = "8080")
    private int port;

    /**
     * Logging level
     */
    @Parameter(property = "mockserver.logLevel", defaultValue = "WARN")
    private String logLevel;

    /**
     * Logging level
     */
    @Parameter(property = "mockserver.pipeLogToConsole", defaultValue = "false")
    private boolean pipeLogToConsole;

    /**
     * The port to stop Mock Server
     */
    @Parameter(property = "mockserver.stopPort", defaultValue = "8081")
    private int stopPort;

    /**
     * Key to provide when stopping Mock Server
     */
    @Parameter(property = "mockserver.stopKey", defaultValue = "STOP_KEY")
    private String stopKey;

    /**
     * Skip plugin execution completely.
     */
    @Parameter(property = "mockserver.skip", defaultValue = "false")
    private boolean skip;

    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping plugin execution");
        } else {
            getLog().info("Starting Mock Server on port " + port);

            // TODO fix the hard coded path below!!!
            ProcessBuilder processBuilder = new ProcessBuilder(
                    getJavaBin(),
                    "-Dmockserver.logLevel=" + logLevel,
                    "-Dmockserver.stopPort=" + stopPort,
                    "-Dmockserver.stopKey=" + stopKey,
//                    "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5006",
                    "-jar", "/Users/jamesdbloom/git/mockservice/mockserver-jetty/target/mockserver-jetty-1.10-SNAPSHOT-jar-with-dependencies.jar", "" + port
            );
            if (pipeLogToConsole) {
                processBuilder.redirectErrorStream(true);
                processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            }
            try {
                processBuilder.start();
            } catch (IOException e) {
                getLog().error("Exception while starting MockServer", e);
            }
        }

    }

    private String getJavaBin() {
        String javaexes[] = new String[]{"java", "java.exe"};

        File javaHomeDir = new File(System.getProperty("java.home"));
        for (String javaexe : javaexes) {
            File javabin = new File(javaHomeDir, fileSeparators("bin/" + javaexe));
            if (javabin.exists() && javabin.isFile()) {
                return javabin.getAbsolutePath();
            }
        }

        return "java";
    }

    public static String fileSeparators(String path) {
        StringBuilder ret = new StringBuilder();
        for (char c : path.toCharArray()) {
            if ((c == '/') || (c == '\\')) {
                ret.append(File.separatorChar);
            } else {
                ret.append(c);
            }
        }
        return ret.toString();
    }
}
