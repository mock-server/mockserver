package org.mockserver.maven;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.mockserver.client.initialize.ExpectationInitializer;
import org.mockserver.configuration.IntegerStringListParser;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.action.http.HttpResponseClassCallbackActionHandler;
import org.slf4j.event.Level;

import java.io.File;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.file.FileReader.readFileFromClassPathOrPath;

/**
 * @author jamesdbloom
 * @plexus.component role="org.codehaus.plexus.component.configurator.ComponentConfigurator"
 * role-hint="include-project-dependencies"
 * @plexus.requirement role="org.codehaus.plexus.component.configurator.converters.lookup.ConverterLookup"
 * role-hint="default"
 * @requiresDependencyCollection test
 * @requiresDependencyResolution test
 */
@SuppressWarnings("deprecation")
public abstract class MockServerAbstractMojo extends AbstractMojo {

    private static final MockServerLogger MOCK_SERVER_LOGGER = new MockServerLogger();
    /**
     * Holds reference to jetty across plugin execution
     */
    @VisibleForTesting
    protected static InstanceHolder instanceHolder;

    /**
     * The HTTP, HTTPS, SOCKS and HTTP CONNECT port for the MockServer
     * for both mocking and proxying requests. Port unification is used
     * to support all protocols for proxying and mocking on the same port.
     */
    @Parameter(property = "mockserver.serverPort")
    protected String serverPort = "";

    /**
     * Optionally enables port forwarding mode. When specified all
     * requests received will be forwarded to the specified port,
     * unless they match an expectation.
     */
    @Parameter(property = "mockserver.proxyRemotePort", defaultValue = "-1")
    protected Integer proxyRemotePort = -1;

    /**
     * Specified the host to forward all proxy requests to when port
     * forwarding mode has been enabled using the proxyRemotePort option.
     * This setting is ignored unless proxyRemotePort has been specified.
     * If no value is provided for proxyRemoteHost when proxyRemotePort
     * has been specified, proxyRemoteHost will default to \"localhost\".
     */
    @Parameter(property = "mockserver.proxyRemoteHost")
    protected String proxyRemoteHost = "";

    /**
     * Timeout to wait before stopping MockServer, to run MockServer indefinitely do not set a value
     */
    @Parameter(property = "mockserver.timeout")
    protected Integer timeout;

    /**
     * Optionally specify log level as TRACE, DEBUG, INFO, WARN, ERROR or
     * OFF. If not specified default is INFO.
     */
    @Parameter(property = "mockserver.logLevel", defaultValue = "INFO")
    protected String logLevel = "INFO";

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
     * to initialize expectations in the MockServer, this class must implement org.mockserver.initialize.PluginExpectationInitializer interface,
     * the initializeExpectations(MockServerClient mockServerClient) method will be called once the MockServer has been started (but ONLY
     * if serverPort has been set), however it should be noted that it is generally better practice to create all expectations locally in
     * each test (or test class) for clarity, simplicity and to avoid brittle tests
     */
    @Parameter(property = "mockserver.initializationClass")
    protected String initializationClass;

    /**
     * To enable the creation of default expectations that are generic across all tests or mocking scenarios a json filed can be specified
     * to initialize expectations in the MockServer. It should be noted that it is generally better practice to create all expectations
     * locally in each test (or test class) for clarity, simplicity and to avoid brittle tests
     */
    @Parameter(property = "mockserver.initializationJson")
    protected String initializationJson;

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

    private String compileResourcePath;
    private String testResourcePath;

    /**
     * The plugin dependencies
     */
    @Parameter(property = "pluginDescriptor.plugin.dependencies", required = true, readonly = true)
    protected List<Dependency> dependencies;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    MavenSession session;

    private Integer[] serverPorts;

    Integer[] getServerPorts() {
        if (serverPorts == null && StringUtils.isNotEmpty(serverPort)) {
            List<Integer> ports = new ArrayList<>();
            for (String port : Splitter.on(',').split(serverPort)) {
                ports.add(Integer.parseInt(port));
            }
            serverPorts = ports.toArray(new Integer[0]);
        }
        return serverPorts;
    }

    protected InstanceHolder getLocalMockServerInstance() {
        if (instanceHolder == null) {
            // create on demand to avoid log creation for skipped plugins
            instanceHolder = new InstanceHolder();
        }
        return instanceHolder;
    }

    protected ExpectationInitializer createInitializerClass() {
        try {
            ClassLoader contextClassLoader = setupClasspath();
            HttpResponseClassCallbackActionHandler.setContextClassLoader(contextClassLoader);
            if (contextClassLoader == null) {
                contextClassLoader = this.getClass().getClassLoader();
            }
            if (isNotBlank(initializationClass) && contextClassLoader != null) {
                Class<?> loadedClass = contextClassLoader.loadClass(initializationClass);
                if (loadedClass != null) {
                    Constructor<?> initializerClassConstructor = loadedClass.getDeclaredConstructor();
                    Object pluginExpectationInitializer = initializerClassConstructor.newInstance();
                    if (pluginExpectationInitializer instanceof ExpectationInitializer) {
                        return (ExpectationInitializer) pluginExpectationInitializer;
                    }
                }
            }
        } catch (Throwable throwable) {
            MOCK_SERVER_LOGGER.logEvent(
                    new LogEntry()
                            .setType(LogEntry.LogMessageType.EXCEPTION)
                            .setLogLevel(Level.ERROR)
                            .setMessageFormat("Exception loading class expectation initializer")
                            .setThrowable(throwable)
            );
        }
        return null;
    }

    protected String createInitializerJson() {
        try {
            if (isNotBlank(initializationJson) && compileResourcePath != null) {
                try {
                    return readFileFromClassPathOrPath(compileResourcePath + "/" + initializationJson);
                } catch (RuntimeException exception) {
                    return readFileFromClassPathOrPath(testResourcePath + "/" + initializationJson);
                }
            }
        } catch (Throwable throwable) {
            MOCK_SERVER_LOGGER.logEvent(
                    new LogEntry()
                            .setType(LogEntry.LogMessageType.EXCEPTION)
                            .setLogLevel(Level.ERROR)
                            .setMessageFormat("Exception loading json expectation initializer")
                            .setThrowable(throwable)
            );
        }
        return "";
    }

    private ClassLoader setupClasspath() throws MalformedURLException {
        if (compileClasspath != null && testClasspath != null) {
            URL[] urls = new URL[compileClasspath.size() + testClasspath.size()];
            for (int i = 0; i < compileClasspath.size(); i++) {
                urls[i] = new File(compileClasspath.get(i)).toURI().toURL();
            }
            for (int i = compileClasspath.size(); i < compileClasspath.size() + testClasspath.size(); i++) {
                String testClasspathEntry = testClasspath.get(i - compileClasspath.size());
                urls[i] = new File(testClasspathEntry).toURI().toURL();
                if (testClasspathEntry.matches(".*[\\\\|/]target[\\\\|/]classes")) {
                    compileResourcePath = testClasspathEntry;
                } else if (testClasspathEntry.matches(".*[\\\\|/]target[\\\\|/]test-classes")) {
                    testResourcePath = testClasspathEntry;
                }
            }

            ClassLoader contextClassLoader = URLClassLoader.newInstance(urls, Thread.currentThread().getContextClassLoader());
            Thread.currentThread().setContextClassLoader(contextClassLoader);
            return contextClassLoader;
        }
        return null;
    }

    public static void mockServerPort(Integer... port) {
        System.setProperty("mockserver.mockServerPort", new IntegerStringListParser().toString(port));
    }

}
