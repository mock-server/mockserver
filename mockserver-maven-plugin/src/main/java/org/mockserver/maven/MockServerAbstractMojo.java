package org.mockserver.maven;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.mockserver.initialize.ExpectationInitializer;

import java.io.File;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

/**
 * @author jamesdbloom
 *
 * @plexus.component role="org.codehaus.plexus.component.configurator.ComponentConfigurator"
 *                   role-hint="include-project-dependencies"
 * @plexus.requirement role="org.codehaus.plexus.component.configurator.converters.lookup.ConverterLookup"
 *                     role-hint="default"
 * @requiresDependencyCollection
 * @requiresDependencyResolution
 */
public abstract class MockServerAbstractMojo extends AbstractMojo {

    /**
     * The port to run MockServer on
     */
    @Parameter(property = "mockserver.serverPort", defaultValue = "-1")
    protected int serverPort = -1;
    /**
     * The port to run the proxy on
     */
    @Parameter(property = "mockserver.proxyPort", defaultValue = "-1")
    protected int proxyPort = -1;
    /**
     * Timeout to wait before stopping MockServer, to run MockServer indefinitely do not set a value
     */
    @Parameter(property = "mockserver.timeout")
    protected int timeout;
    /**
     * Logging level
     */
    @Parameter(property = "mockserver.logLevel", defaultValue = "INFO")
    protected String logLevel;
    /**
     * Skip the plugin execution completely
     */
    @Parameter(property = "mockserver.skip", defaultValue = "false")
    protected boolean skip;
    /**
     * If true the console of the forked JVM will be piped to the Maven console
     */
    @Parameter(property = "mockserver.pipeLogToConsole", defaultValue = "false")
    protected boolean pipeLogToConsole;
    /**
     * To enable the creation of default expectations that are generic across all tests or mocking scenarios a class can be specified
     * to initialize expectations in the MockServer, this class must implement org.mockserver.initialize.ExpectationInitializer interface,
     * the initializeExpectations(MockServerClient mockServerClient) method will be called once the MockServer has been started (but ONLY
     * if serverPort has been set), however it should be noted that it is generally better practice to create all expectations locally in
     * each test (or test class) for clarity, simplicity and to avoid brittle tests
     */
    @Parameter(property = "mockserver.initializationClass")
    protected String initializationClass;

    /**
     * The main classpath location of the project using this plugin
     */
    @Parameter(property = "project.compileClasspathElements", required = true, readonly = true)
    protected List<String> compileClasspath;

    /**
     * The test classpath location of the project using this plugin
     */
    @Parameter(property = "project.testClasspathElements", required = true, readonly = true)
    protected List<String> testClasspath;

    /**
     * The plugin dependencies
     */
    @Parameter(property = "pluginDescriptor.plugin.dependencies", required = true, readonly = true)
    protected List<Dependency> dependencies;

    /**
     * The plugin dependencies
     */
    @Parameter(property = "pluginDescriptor.artifacts", required = true, readonly = true)
    protected List<Artifact> pluginArtifacts;

    /**
     * Holds reference to jetty across plugin execution
     */
    @VisibleForTesting
    protected static InstanceHolder embeddedJettyHolder;

    protected InstanceHolder getEmbeddedJettyHolder() {
        if (embeddedJettyHolder == null) {
            // create on demand to avoid log creation for skipped plugins
            embeddedJettyHolder = new InstanceHolder();
        }
        return embeddedJettyHolder;
    }

    protected ExpectationInitializer createInitializer() {
        try {
            ClassLoader contextClassLoader = setupClasspath();
            if (contextClassLoader != null && StringUtils.isNotEmpty(initializationClass)) {
                Constructor<?> initializerClassConstructor = contextClassLoader.loadClass(initializationClass).getDeclaredConstructor();
                Object expectationInitializer = initializerClassConstructor.newInstance();
                if (expectationInitializer instanceof ExpectationInitializer) {
                    return (ExpectationInitializer) expectationInitializer;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    private ClassLoader setupClasspath() throws MalformedURLException {
        if (compileClasspath != null) {
            URL[] urls = new URL[compileClasspath.size() + testClasspath.size()];
            for (int i = 0; i < compileClasspath.size(); i++) {
                urls[i] = new File(compileClasspath.get(i)).toURI().toURL();
            }
            for (int i = compileClasspath.size(); i < compileClasspath.size() + testClasspath.size(); i++) {
                urls[i] = new File(testClasspath.get(i - compileClasspath.size())).toURI().toURL();
            }

            ClassLoader contextClassLoader = URLClassLoader.newInstance(urls, Thread.currentThread().getContextClassLoader());
            Thread.currentThread().setContextClassLoader(contextClassLoader);
            return contextClassLoader;
        }
        return null;
    }

    /**
     * Logs the plugin classpath.
     */
    protected void logPluginClasspath() {
        if (getLog().isDebugEnabled()) {
            StringBuilder sb = new StringBuilder();
            for (Artifact artifact : pluginArtifacts) {
                sb.append(artifact.getFile());
            }
            getLog().debug("Plugin classpath:\n" + sb.toString());
        }
    }
}
