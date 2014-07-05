package org.mockserver.junit;

import com.google.common.annotations.VisibleForTesting;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.integration.ClientAndProxy;
import org.mockserver.socket.PortFactory;

import java.lang.reflect.Field;

public class ProxyRule implements TestRule {

    private static ClientAndProxy perTestSuiteClientAndProxy;
    private final Object target;
    private final Integer httpPort;
    private final Integer httpsPort;
    private final boolean perTestSuite;
    private ClientAndProxyFactory clientAndProxyFactory;

    /**
     * Start the proxy prior to test execution and stop the proxy after the tests have completed.
     * This constructor dynamically allocates a free port for the proxy to use.
     * Note: The getHttpPort getter can be used to retrieve the dynamically allocated port.
     *
     * @param target an instance of the test being executed
     */
    public ProxyRule(Object target) {
        this(PortFactory.findFreePort(), target);
    }

    /**
     * Start the proxy prior to test execution and stop the proxy after the tests have completed.
     * This constructor dynamically allocates a free port for the proxy to use.
     *
     * @param target an instance of the test being executed
     */
    public ProxyRule(Object target, boolean perTestSuite) {
        this(PortFactory.findFreePort(), target, perTestSuite);
    }

    /**
     * Start the proxy prior to test execution and stop the proxy after the tests have completed.
     * This constructor dynamically create a proxy that accepts HTTP requests on the specified port
     *
     * @param httpPort the HTTP port for the proxy
     * @param target an instance of the test being executed
     */
    public ProxyRule(Integer httpPort, Object target) {
        this(httpPort, null, target, false);
    }

    /**
     * Start the proxy prior to test execution and stop the proxy after the tests have completed.
     * This constructor dynamically create a proxy that accepts HTTP requests on the specified port
     *
     * @param httpPort the HTTP port for the proxy
     * @param target an instance of the test being executed
     */
    public ProxyRule(Integer httpPort, Object target, boolean perTestSuite) {
        this(httpPort, null, target, perTestSuite);
    }

    /**
     * Start the proxy prior to test execution and stop the proxy after the tests have completed.
     * This constructor dynamically create a proxy that accepts HTTP and HTTPS requests on the specified ports
     *
     * @param httpPort the HTTP port for the proxy
     * @param httpsPort the HTTPS port for the proxy
     * @param target an instance of the test being executed
     */
    public ProxyRule(Integer httpPort, Integer httpsPort, Object target, boolean perTestSuite) {
        this.httpPort = httpPort;
        this.httpsPort = httpsPort;
        this.target = target;
        this.perTestSuite = perTestSuite;
        this.clientAndProxyFactory = new ClientAndProxyFactory(httpPort, httpsPort);
    }

    public Integer getHttpPort() {
        return httpPort;
    }

    public Integer getHttpsPort() {
        return httpsPort;
    }

    public Statement apply(Statement base, Description description) {
        return statement(base);
    }

    private Statement statement(final Statement base) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                ClientAndProxy clientAndProxy;
                if (perTestSuite) {
                    if (perTestSuiteClientAndProxy == null) {
                        perTestSuiteClientAndProxy = clientAndProxyFactory.newClientAndProxy();
                        Runtime.getRuntime().addShutdownHook(new Thread() {
                            @Override
                            public void run() {
                                perTestSuiteClientAndProxy.stop();
                            }
                        });
                    }
                    clientAndProxy = perTestSuiteClientAndProxy;
                } else {
                    clientAndProxy = clientAndProxyFactory.newClientAndProxy();
                }
                setProxyClient(target, clientAndProxy);
                try {
                    base.evaluate();
                } finally {
                    if (!perTestSuite) {
                        clientAndProxy.stop();
                    }
                }
            }
        };
    }

    private void setProxyClient(Object target, ClientAndProxy clientAndProxy) {
        for (Field field : target.getClass().getDeclaredFields()) {
            if (field.getType().equals(ProxyClient.class)) {
                field.setAccessible(true);
                try {
                    field.set(target, clientAndProxy);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Error setting ProxyClient field on " + target.getClass().getName(), e);
                }
            }
        }
    }

    @VisibleForTesting
    class ClientAndProxyFactory {
        private final Integer httpPort;
        private final Integer httpsPort;

        public ClientAndProxyFactory(Integer httpPort, Integer httpsPort) {
            this.httpPort = httpPort;
            this.httpsPort = httpsPort;
        }

        public ClientAndProxy newClientAndProxy() {
            System.out.println("httpPort = " + httpPort);
            if (httpsPort == null) {
                return ClientAndProxy.startClientAndProxy(httpPort);
            } else {
                return ClientAndProxy.startClientAndProxy(httpPort, httpsPort);
            }
        }
    }
}