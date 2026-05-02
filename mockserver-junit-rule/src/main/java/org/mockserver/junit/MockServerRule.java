package org.mockserver.junit;

import com.google.common.annotations.VisibleForTesting;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.socket.PortFactory;

import java.lang.reflect.Field;
import java.util.List;

@SuppressWarnings("FieldMayBeFinal")
public class MockServerRule implements TestRule {

    @VisibleForTesting
    static ClientAndServer perTestSuiteClientAndServer;
    private final Object target;
    private final Integer[] ports;
    private final boolean perTestSuite;
    private ClientAndServerFactory clientAndServerFactory;
    private ClientAndServer clientAndServer;

    /**
     * Start the MockServer prior to test execution and stop the MockServer after the tests have completed.
     * This constructor dynamically allocates a free port for the MockServer to use.
     * <p>
     * If the test class contains a MockServerClient field it is set with a client configured for the created MockServer.
     *
     * @param target an instance of the test being executed
     */
    public MockServerRule(Object target) {
        this(target, PortFactory.findFreePort());
    }

    /**
     * Start the MockServer prior to test execution and stop the MockServer after the tests have completed.
     * This constructor dynamically allocates a free port for the MockServer to use.
     * <p>
     * If the test class contains a MockServerClient field it is set with a client configured for the created MockServer.
     *
     * @param target       an instance of the test being executed
     * @param perTestSuite indicates how many instances of MockServer are created
     *                     if true a single MockServer is created per JVM
     *                     if false one instance per test class is created
     */
    public MockServerRule(Object target, boolean perTestSuite) {
        this(target, perTestSuite, PortFactory.findFreePort());
    }

    /**
     * Start the proxy prior to test execution and stop the proxy after the tests have completed.
     * This constructor dynamically create a MockServer that accepts HTTP(s) requests on the specified port
     * <p>
     * If the test class contains a MockServerClient field it is set with a client configured for the created MockServer.
     *
     * @param perTestSuite indicates how many instances of MockServer are created
     *                     if true a single MockServer is created per JVM
     *                     if false one instance per test class is created
     * @param ports  the HTTP(S) port for the proxy
     */
    public MockServerRule(Object target, Integer... ports) {
        this(target, true, ports);
    }

    /**
     * Start the proxy prior to test execution and stop the proxy after the tests have completed.
     * This constructor dynamically create a proxy that accepts HTTP(s) requests on the specified port
     *
     * @param target       an instance of the test being executed
     * @param perTestSuite indicates how many instances of MockServer are created
     *                     if true a single MockServer is created per JVM
     *                     if false one instance per test class is created
     * @param ports        the HTTP(S) port for the proxy
     */
    public MockServerRule(Object target, boolean perTestSuite, Integer... ports) {
        this.ports = ports;
        this.target = target;
        this.perTestSuite = perTestSuite;
        this.clientAndServerFactory = new ClientAndServerFactory(ports);
    }

    public Integer getPort() {
        Integer port = null;
        if (clientAndServer != null) {
            port = clientAndServer.getPort();
        } else if (ports.length > 0) {
            port = ports[0];
        }
        return port;
    }

    public Integer[] getPorts() {
        if (clientAndServer != null) {
            List<Integer> ports = clientAndServer.getLocalPorts();
            return ports.toArray(new Integer[0]);
        }
        return ports;
    }

    public Statement apply(Statement base, Description description) {
        return statement(base);
    }

    private Statement statement(final Statement base) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                if (perTestSuite) {
                    synchronized (MockServerRule.class) {
                        if (perTestSuiteClientAndServer == null) {
                            perTestSuiteClientAndServer = clientAndServerFactory.newClientAndServer();
                        } else {
                            perTestSuiteClientAndServer.reset();
                        }
                    }
                    clientAndServer = perTestSuiteClientAndServer;
                    setMockServerClient(target, perTestSuiteClientAndServer);
                    base.evaluate();
                } else {
                    clientAndServer = clientAndServerFactory.newClientAndServer();
                    setMockServerClient(target, clientAndServer);
                    try {
                        base.evaluate();
                    } finally {
                        clientAndServer.stop();
                    }
                }

            }
        };
    }

    private void setMockServerClient(Object target, ClientAndServer clientAndServer) {
        for (Class<?> clazz = target instanceof Class ? (Class<?>) target : target.getClass(); !clazz.equals(Object.class); clazz = clazz.getSuperclass()) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.getType().equals(MockServerClient.class)) {
                    field.setAccessible(true);
                    try {
                        field.set(target, clientAndServer);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException("Error setting MockServerClient field on " + target.getClass().getName(), e);
                    }
                }
            }
        }
    }

    public MockServerClient getClient() {
        return clientAndServer;
    }

    @VisibleForTesting
    static class ClientAndServerFactory {
        private final Integer[] port;

        public ClientAndServerFactory(Integer... port) {
            this.port = port;
        }

        public ClientAndServer newClientAndServer() {
            return ClientAndServer.startClientAndServer(port);
        }
    }
}
