package org.mockserver.springtest;

import org.mockserver.configuration.Configuration;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.mock.Expectation;
import org.mockserver.time.TimeService;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.RepeatableContainers;
import org.springframework.test.context.TestContextAnnotationUtils;

import java.io.File;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reads {@link MockServerInstance} declarations and holds the servers they start, one per distinct declaration.
 * Each server is reference-counted by the Spring contexts using it and stops when the last of them closes; a JVM
 * shutdown hook stops any left over (for example after a context failed to refresh).
 */
final class NamedMockServers {

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Set<String> RESERVED_PROPERTIES = Set.of("mockServerPort", "mockServerDnsPort");
    private static final Map<Definition, Instance> INSTANCES = new HashMap<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(NamedMockServers::stopAll, "MockServerInstance shutdown"));
    }

    private NamedMockServers() {
    }

    static List<Definition> definitionsFor(Class<?> testClass) {
        List<Definition> definitions = new ArrayList<>();
        Set<String> names = new HashSet<>();
        Set<String> springProperties = new HashSet<>();
        for (MockServerInstance instance : declaredInstances(testClass)) {
            Definition definition = new Definition(instance);
            if (!VALID_NAME.matcher(definition.name).matches()) {
                throw new IllegalStateException("@MockServerInstance name \"" + definition.name + "\" on " + testClass.getName() + " must contain only letters, digits, '-' and '_'");
            }
            if (!names.add(definition.name)) {
                throw new IllegalStateException("@MockServerInstance name \"" + definition.name + "\" is declared more than once on " + testClass.getName() + " and its superclasses");
            }
            for (String property : definition.properties) {
                if (!property.startsWith("mockserver.") || !property.contains("=")) {
                    throw new IllegalStateException("@MockServerInstance \"" + definition.name + "\" property \"" + property + "\" on " + testClass.getName() + " must be mockserver.<property>=<value>");
                }
            }
            for (String property : definition.springPropertyNames()) {
                if (RESERVED_PROPERTIES.contains(property)) {
                    throw new IllegalStateException("@MockServerInstance \"" + definition.name + "\" on " + testClass.getName() + " cannot set \"" + property + "\", which @MockServerTest sets");
                }
                if (!springProperties.add(property)) {
                    throw new IllegalStateException("property \"" + property + "\" is set by more than one @MockServerInstance on " + testClass.getName());
                }
            }
            definitions.add(definition);
        }
        return definitions;
    }

    // walks the whole superclass chain, unlike getMergedRepeatableAnnotations, so a name repeated anywhere is caught
    private static List<MockServerInstance> declaredInstances(Class<?> testClass) {
        List<MockServerInstance> instances = MergedAnnotations
            .from(testClass, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY, RepeatableContainers.standardRepeatables())
            .stream(MockServerInstance.class)
            .map(MergedAnnotation::synthesize)
            .collect(Collectors.toList());
        if (instances.isEmpty() && TestContextAnnotationUtils.searchEnclosingClass(testClass)) {
            return declaredInstances(testClass.getEnclosingClass());
        }
        return instances;
    }

    /**
     * Starts the server for a definition if it is not running, and records one more Spring context using it.
     */
    static ClientAndServer acquire(Definition definition) {
        synchronized (INSTANCES) {
            Instance instance = INSTANCES.computeIfAbsent(definition, ignored -> new Instance());
            if (instance.server == null || !instance.server.isRunning()) {
                ClientAndServer server = ClientAndServer.startClientAndServer(definition.configuration(), 0);
                instance.server = server;
                instance.initialExpectations = server.retrieveActiveExpectations(null);
            }
            instance.references++;
            return instance.server;
        }
    }

    /**
     * Records that a Spring context no longer uses the definition, and stops its server when none do.
     */
    static void release(Definition definition) {
        ClientAndServer toStop = null;
        synchronized (INSTANCES) {
            Instance instance = INSTANCES.get(definition);
            if (instance != null && --instance.references <= 0) {
                INSTANCES.remove(definition);
                toStop = instance.server;
            }
        }
        if (toStop != null) {
            toStop.stop();
        }
    }

    static ClientAndServer running(Definition definition) {
        synchronized (INSTANCES) {
            Instance instance = INSTANCES.get(definition);
            return instance != null && instance.server != null && instance.server.isRunning() ? instance.server : null;
        }
    }

    static int references(Definition definition) {
        synchronized (INSTANCES) {
            Instance instance = INSTANCES.get(definition);
            return instance == null ? 0 : instance.references;
        }
    }

    /**
     * Resets the server, then restores the expectations it loaded at startup, so an initialization file applies to
     * every test rather than only the first. Each time-to-live restarts from the reset.
     */
    static void resetIfRunning(Definition definition) {
        ClientAndServer server;
        Expectation[] initialExpectations;
        synchronized (INSTANCES) {
            Instance instance = INSTANCES.get(definition);
            if (instance == null || instance.server == null || !instance.server.isRunning()) {
                return;
            }
            server = instance.server;
            initialExpectations = instance.initialExpectations;
        }
        server.reset();
        if (initialExpectations != null && initialExpectations.length > 0) {
            long now = TimeService.currentTimeMillis();
            for (Expectation expectation : initialExpectations) {
                TimeToLive timeToLive = expectation.getTimeToLive();
                if (timeToLive != null && !timeToLive.isUnlimited()) {
                    timeToLive.setEndDate(now + timeToLive.getTimeUnit().toMillis(timeToLive.getTimeToLive()));
                }
            }
            server.upsert(initialExpectations);
        }
    }

    private static void stopAll() {
        List<ClientAndServer> servers = new ArrayList<>();
        synchronized (INSTANCES) {
            INSTANCES.values().forEach(instance -> {
                if (instance.server != null) {
                    servers.add(instance.server);
                }
            });
            INSTANCES.clear();
        }
        servers.forEach(ClientAndServer::stop);
    }

    private static final class Instance {
        private ClientAndServer server;
        private Expectation[] initialExpectations;
        private int references;
    }

    static final class Definition {
        final String name;
        final List<String> portProperties;
        final List<String> baseUrlProperties;
        final String initializationJsonPath;
        final List<String> properties;

        Definition(MockServerInstance instance) {
            this(instance.name(), Arrays.asList(instance.portProperties()), Arrays.asList(instance.baseUrlProperties()), instance.initializationJsonPath(), Arrays.asList(instance.properties()));
        }

        Definition(String name, List<String> portProperties, List<String> baseUrlProperties, String initializationJsonPath, List<String> properties) {
            this.name = name;
            this.portProperties = List.copyOf(portProperties);
            this.baseUrlProperties = List.copyOf(baseUrlProperties);
            this.initializationJsonPath = initializationJsonPath;
            this.properties = List.copyOf(properties);
        }

        String portProperty() {
            return "mockserver." + name + ".port";
        }

        String baseUrlProperty() {
            return "mockserver." + name + ".baseUrl";
        }

        List<String> springPropertyNames() {
            List<String> names = new ArrayList<>();
            names.add(portProperty());
            names.add(baseUrlProperty());
            names.addAll(portProperties);
            names.addAll(baseUrlProperties);
            return names;
        }

        Configuration configuration() {
            Configuration configuration = MockServerPropertyCustomizer.buildConfiguration(properties);
            if (!initializationJsonPath.isBlank()) {
                configuration.initializationJsonPath(expandDirectory(initializationJsonPath.trim()));
            }
            return configuration;
        }

        // the initializer reads a file, a classpath resource or a glob, but not a bare directory
        static String expandDirectory(String path) {
            File file = new File(path);
            if (file.isDirectory()) {
                String directory = file.getPath().replace(File.separatorChar, '/');
                return (directory.endsWith("/") ? directory : directory + "/") + "*.json";
            }
            return path;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            Definition that = (Definition) other;
            return name.equals(that.name)
                && portProperties.equals(that.portProperties)
                && baseUrlProperties.equals(that.baseUrlProperties)
                && initializationJsonPath.equals(that.initializationJsonPath)
                && properties.equals(that.properties);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, portProperties, baseUrlProperties, initializationJsonPath, properties);
        }
    }
}
