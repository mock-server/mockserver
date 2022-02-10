package org.mockserver.proxyservlet.integration;

import org.apache.catalina.Context;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockserver.client.MockServerClient;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.proxyservlet.ProxyServlet;
import org.mockserver.socket.PortFactory;
import org.mockserver.socket.tls.KeyStoreFactory;
import org.mockserver.testing.integration.proxy.AbstractProxyIntegrationTest;

import java.io.File;
import java.util.concurrent.TimeUnit;

import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
public class ServletProxyIntegrationTest extends AbstractProxyIntegrationTest {

    private static final int PROXY_PORT = PortFactory.findFreePort();
    private static final int PROXY_HTTPS_PORT = PortFactory.findFreePort();
    private static Tomcat tomcat;
    private static EchoServer echoServer;
    private static MockServerClient mockServerClient;

    @BeforeClass
    public static void startServer() throws Exception {
        servletContext = "";

        // start server
        echoServer = new EchoServer(false);

        // wait for server to start up
        TimeUnit.MILLISECONDS.sleep(500);

        // start proxy (in tomcat)
        tomcat = new Tomcat();
        tomcat.setBaseDir(new File(".").getCanonicalPath() + File.separatorChar + "tomcat" + (servletContext.length() > 0 ? "_" + servletContext : ""));

        // add http port
        tomcat.setPort(PROXY_PORT);
        Connector defaultConnector = tomcat.getConnector();
        defaultConnector.setRedirectPort(PROXY_HTTPS_PORT);

        // add https connector
        KeyStoreFactory keyStoreFactory = new KeyStoreFactory(new MockServerLogger());
        keyStoreFactory.loadOrCreateKeyStore();
        Connector httpsConnector = new Connector();
        httpsConnector.setPort(PROXY_HTTPS_PORT);
        httpsConnector.setSecure(true);
        httpsConnector.setAttribute("keyAlias", KeyStoreFactory.KEY_STORE_CERT_ALIAS);
        httpsConnector.setAttribute("keystorePass", KeyStoreFactory.KEY_STORE_PASSWORD);
        httpsConnector.setAttribute("keystoreFile", new File(keyStoreFactory.keyStoreFileName).getAbsoluteFile());
        httpsConnector.setAttribute("sslProtocol", "TLS");
        httpsConnector.setAttribute("clientAuth", false);
        httpsConnector.setAttribute("SSLEnabled", true);

        Service service = tomcat.getService();
        service.addConnector(httpsConnector);

        // add servlet
        Context ctx = tomcat.addContext("/" + servletContext, new File(".").getAbsolutePath());
        tomcat.addServlet("/" + servletContext, "mockServerServlet", new ProxyServlet());
        ctx.addServletMappingDecoded("/*", "mockServerServlet");
        ctx.addApplicationListener(ProxyServlet.class.getName());

        // start server
        tomcat.start();

        // start client
        mockServerClient = new MockServerClient("localhost", PROXY_PORT, servletContext);
    }

    @AfterClass
    public static void stopServer() throws Exception {
        // stop client
        stopQuietly(mockServerClient);

        // stop test server
        stopQuietly(echoServer);

        // stop mock server
        if (tomcat != null) {
            tomcat.stop();
            tomcat.getServer().await();
        }

        // wait for server to shutdown
        TimeUnit.MILLISECONDS.sleep(500);
    }

    @Before
    public void resetProxy() {
        mockServerClient.reset();
    }

    @Override
    public int getProxyPort() {
        return PROXY_PORT;
    }

    @Override
    public MockServerClient getMockServerClient() {
        return mockServerClient;
    }

    @Override
    public int getServerPort() {
        return echoServer.getPort();
    }

    @Override
    public EchoServer getEchoServer() {
        return echoServer;
    }
}
