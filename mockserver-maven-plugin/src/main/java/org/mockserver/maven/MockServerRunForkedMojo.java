package org.mockserver.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author jamesdbloom
 */
@Mojo(name = "runForked", requiresProject = false, threadSafe = false)
public class MockServerRunForkedMojo extends AbstractMojo {

    /**
     * The port to run MockServer on
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
     * The port to stop MockServer
     */
    @Parameter(property = "mockserver.stopPort", defaultValue = "8081")
    private int stopPort;

    /**
     * Key to provide when stopping MockServer
     */
    @Parameter(property = "mockserver.stopKey", defaultValue = "STOP_KEY")
    private String stopKey;

    /**
     * Skip plugin execution completely.
     */
    @Parameter(property = "mockserver.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Get a list of artifacts used by this plugin
     */
    @Parameter(defaultValue = "${plugin.artifacts}", required = true, readonly = true)
    protected List<Artifact> pluginArtifacts;

    /**
     * Used to look up Artifacts in the remote repository.
     */
//    @Parameter(defaultValue = "${component.org.apache.maven.artifact.factory.ArtifactFactory}", required = true, readonly = true)
    @Component
    protected ArtifactFactory factory;

    /**
     * Used to look up Artifacts in the remote repository.
     */
//    @Parameter(defaultValue = "${component.org.apache.maven.artifact.resolver.ArtifactResolver}", required = true, readonly = true)
    @Component
    protected ArtifactResolver artifactResolver;

    /**
     * List of Remote Repositories used by the resolver
     */
    @Parameter(defaultValue = "${project.remoteArtifactRepositories}", required = true, readonly = true)
    protected List<ArtifactRepository> remoteRepositories;

    /**
     * Location of the local repository.
     */
    @Parameter(defaultValue = "${localRepository}", required = true, readonly = true)
    protected ArtifactRepository localRepository;

    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping plugin execution");
        } else {
            getLog().info("Starting MockServer on port " + port);
            ProcessBuilder processBuilder = new ProcessBuilder(
                    getJavaBin(),
                    "-Dmockserver.logLevel=" + logLevel,
                    "-Dmockserver.stopPort=" + stopPort,
                    "-Dmockserver.stopKey=" + stopKey,
//                    "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5006",
                    "-jar", jarWithDependencies(), "" + port
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

    protected String jarWithDependencies() {
        Artifact jarWithDependencies = factory.createArtifactWithClassifier("org.mock-server", "mockserver-jetty", "1.11-SNAPSHOT", "jar", "jar-with-dependencies");
        artifactResolver.resolve(new ArtifactResolutionRequest().setArtifact(jarWithDependencies));
        getLog().debug("Running MockServer using " + jarWithDependencies.getFile().getAbsolutePath());
        return jarWithDependencies.getFile().getAbsolutePath();
    }
}
