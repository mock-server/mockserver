package org.mockserver.maven;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.shared.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.artifact.resolve.ArtifactResolverException;
import org.mockserver.cli.Main;
import org.mockserver.configuration.ConfigurationProperties;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Run a forked instance of the MockServer
 *
 * To run from command line:
 *
 *    mvn -Dmockserver.serverPort="1080" -Dmockserver.proxyPort="1090" -Dmockserver.logLevel="TRACE" org.mock-server:mockserver-maven-plugin:5.2.2:runForked
 *
 * @author jamesdbloom
 */
@Mojo(name = "runForked", requiresProject = false, threadSafe = false)
public class MockServerRunForkedMojo extends MockServerAbstractMojo {

    /**
     * Get a list of artifacts used by this plugin
     */
    @Parameter(defaultValue = "${plugin.artifacts}", required = true, readonly = true)
    protected List<Artifact> pluginArtifacts;
    /**
     * Used to look up Artifacts in the remote repository.
     */
    @Component
    protected RepositorySystem repositorySystem;
    /**
     * Used to look up Artifacts in the remote repository.
     */
    @Component
    protected ArtifactResolver artifactResolver;
    private ProcessBuildFactory processBuildFactory = new ProcessBuildFactory();

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

    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping plugin execution");
        } else {
            getEmbeddedJettyHolder().stop(getServerPorts(), proxyPort, true);
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                throw new RuntimeException("Exception while waiting for existing mock server JVM to stop", e);
            }
            if (getLog().isInfoEnabled()) {
                getLog().info("mockserver:runForked about to start MockServer on: "
                                + (getServerPorts() != null ? " serverPort " + Arrays.toString(getServerPorts()) : "")
                                + (proxyPort != -1 ? " proxyPort " + proxyPort : "")
                );
            }
            List<String> arguments = new ArrayList<String>(Collections.singletonList(getJavaBin()));
//            arguments.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5010");
            arguments.add("-Dfile.encoding=UTF-8");
            arguments.add("-Dmockserver.logLevel=" + logLevel);
            arguments.add("-cp");
            StringBuilder classPath = new StringBuilder(resolvePathForJarWithDependencies());
            if (dependencies != null && !dependencies.isEmpty()) {
                for (Dependency dependency : dependencies) {
                    classPath.append(System.getProperty("path.separator"));
                    classPath.append(resolvePathForDependencyJar(dependency));
                }
            }
            arguments.add(classPath.toString());
            arguments.add(Main.class.getName());
            if (getServerPorts() != null) {
                arguments.add("-serverPort");
                arguments.add("" + Joiner.on(",").join(getServerPorts()));
                ConfigurationProperties.mockServerPort(getServerPorts());
            }
            if (proxyPort != -1) {
                arguments.add("-proxyPort");
                arguments.add("" + proxyPort);
                ConfigurationProperties.proxyPort(proxyPort);
            }
            getLog().info(" ");
            String message = "Running: " + Joiner.on(" ").join(arguments);
            getLog().info(StringUtils.rightPad("", message.length(), "-"));
            getLog().info(message);
            getLog().info(StringUtils.rightPad("", message.length(), "-"));
            getLog().info(" ");
            ProcessBuilder processBuilder = processBuildFactory.create(arguments);
            if (pipeLogToConsole) {
                processBuilder.redirectErrorStream(true);
            }
            try {
                processBuilder.start();
            } catch (IOException e) {
                getLog().error("Exception while starting MockServer", e);
            }
            try {
                TimeUnit.SECONDS.sleep((timeout == null ? 2 : timeout));
            } catch (InterruptedException e) {
                throw new RuntimeException("Exception while waiting for mock server JVM to start", e);
            }
            InstanceHolder.runInitializationClass(getServerPorts(), createInitializer());
        }

    }

    @VisibleForTesting
    String getJavaBin() {
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

    private String resolvePathForDependencyJar(Dependency dependency) {
        String path = "";
        try {
            Artifact dependencyArtifact = repositorySystem.createArtifactWithClassifier(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion(), dependency.getType(), dependency.getClassifier());
            artifactResolver.resolveArtifact(session.getProjectBuildingRequest(), dependencyArtifact);
            if (dependencyArtifact != null) {
                ArtifactRepository localRepository = session.getLocalRepository();
                path = localRepository.getBasedir() + "/" + localRepository.pathOf(dependencyArtifact);
            }
        } catch (ArtifactResolverException e) {
            getLog().warn("Exception while resolving file path for dependency " + dependency, e);
        }
        return path;
    }

    private String resolvePathForJarWithDependencies() {
        Dependency dependency = new Dependency();
        dependency.setGroupId("org.mock-server");
        dependency.setArtifactId("mockserver-netty");
        dependency.setVersion(getVersion());
        dependency.setType("jar");
        dependency.setClassifier("jar-with-dependencies");
        return resolvePathForDependencyJar(dependency);
    }

    @VisibleForTesting
    String getVersion() {
        String version = "5.2.2";
        try {
            java.util.Properties p = new java.util.Properties();
            InputStream is = getClass().getResourceAsStream("/META-INF/maven/org.mock-server/mockserver-maven-plugin/pom.properties");
            if (is != null) {
                p.load(is);
                version = p.getProperty("version", "5.2.2");
            }
        } catch (Exception e) {
            // ignore
        }
        getLog().info("Using org.mock-server:mockserver-netty:" + version + ":jar-with-dependencies");
        return version;
    }
}
