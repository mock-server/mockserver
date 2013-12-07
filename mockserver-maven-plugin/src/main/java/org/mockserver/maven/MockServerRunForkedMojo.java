package org.mockserver.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * @author jamesdbloom
 */
@Mojo(name = "runForked", requiresProject = false, threadSafe = false)
public class MockServerRunForkedMojo extends AbstractMojo {

    /**
     * The port to run Mock Server on
     */
    @Parameter(property = "mockserver.port", defaultValue = "9090")
    private int port;

    /**
     * Logging level
     */
    @Parameter(property = "mockserver.logLevel", defaultValue = "WARN")
    private String logLevel;

    /**
     * The port to stop Mock Server
     */
    @Parameter(property = "mockserver.stopPort", defaultValue = "9091")
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


    /**
     * The Maven project.
     */
    @Component
    private MavenProject project;

    /**
     *
     */
    @Component
    private MavenSession session;

    /**
     * The forked jetty instance
     */
    private Process forkedProcess;

    /**
     * ShutdownThread
     */
    public class ShutdownThread extends Thread {
        public ShutdownThread() {
            super("Shutdown Forked Mock Server");
        }

        public void run() {
            if (forkedProcess != null) {
                forkedProcess.destroy();
            }
        }
    }

    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping plugin execution");
        } else {
            getLog().info("Starting Mock Server on port " + port);

            Runtime.getRuntime().addShutdownHook(new ShutdownThread());
            ProcessBuilder processBuilder = new ProcessBuilder(
                    getJavaBin(),
                    "-Dmockserver.logLevel=" + logLevel,
                    "-Dmockserver.stopPort=" + stopPort,
                    "-Dmockserver.stopKey=" + stopKey,
                    "-jar", "/Users/jamesdbloom/git/mockservice/mockserver-jetty/target/mockserver-jetty-1.9-SNAPSHOT-jar-with-dependencies.jar", "" + port
            );
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            try {
                forkedProcess = processBuilder.start();
            } catch (IOException e) {
                e.printStackTrace();
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

    private Set<Artifact> getExtraJars() throws Exception {

//        String groupId;
//        String artifactId;
//        VersionRange versionRange;
//        String scope;
//        String type;
//        String classifier;
//        ArtifactHandler artifactHandler;
//        Artifact artifact = new DefaultArtifact();
        project.getPluginArtifactMap();
//        mavenProject.getDistributionManagementArtifactRepository().find(artifact);

        return null;
    }
}
