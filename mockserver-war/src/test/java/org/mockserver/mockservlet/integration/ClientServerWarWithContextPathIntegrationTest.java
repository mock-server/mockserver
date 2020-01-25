package org.mockserver.mockservlet.integration;

import org.apache.catalina.Context;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.client.MockServerClient;
import org.mockserver.testing.integration.mock.AbstractBasicMockingIntegrationTest;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mockservlet.MockServerServlet;
import org.mockserver.socket.PortFactory;
import org.mockserver.socket.tls.KeyStoreFactory;

import java.io.File;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;

import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
public class ClientServerWarWithContextPathIntegrationTest extends AbstractBasicMockingIntegrationTest {

    private static final int SERVER_HTTP_PORT = PortFactory.findFreePort();
    private static final int SERVER_HTTPS_PORT = PortFactory.findFreePort();
    private static Tomcat tomcat;

    @BeforeClass
    public static void startServer() throws Exception {
        servletContext = "mockserver";

        tomcat = new Tomcat();
        tomcat.setBaseDir(new File(".").getCanonicalPath() + File.separatorChar + "tomcat" + (servletContext.length() > 0 ? "_" + servletContext : ""));

        // add http port
        tomcat.setPort(SERVER_HTTP_PORT);
        Connector defaultConnector = tomcat.getConnector();
        defaultConnector.setRedirectPort(SERVER_HTTPS_PORT);

        // add https connector
        new KeyStoreFactory(new MockServerLogger()).loadOrCreateKeyStore("jks");
        Connector httpsConnector = new Connector();
        httpsConnector.setPort(SERVER_HTTPS_PORT);
        httpsConnector.setSecure(true);
        httpsConnector.setAttribute("keyAlias", KeyStoreFactory.KEY_STORE_CERT_ALIAS);
        httpsConnector.setAttribute("keystorePass", KeyStoreFactory.KEY_STORE_PASSWORD);
        httpsConnector.setAttribute("keystoreFile", new File(KeyStoreFactory.KEY_STORE_FILE_NAME).getAbsoluteFile());
        httpsConnector.setAttribute("sslProtocol", "TLS");
        httpsConnector.setAttribute("clientAuth", false);
        httpsConnector.setAttribute("SSLEnabled", true);

        Service service = tomcat.getService();
        service.addConnector(httpsConnector);

        // add servlet
        Context ctx = tomcat.addContext("/" + servletContext, new File(".").getAbsolutePath());
        tomcat.addServlet("/" + servletContext, "mockServerServlet", new MockServerServlet());
        ctx.addServletMappingDecoded("/*", "mockServerServlet");
        ctx.addApplicationListener(MockServerServlet.class.getName());

        // start server
        tomcat.start();

        // start client
        mockServerClient = new MockServerClient("localhost", SERVER_HTTP_PORT, servletContext);
    }

    @AfterClass
    public static void stopServer() throws Exception {
        // stop client
        stopQuietly(mockServerClient);

        // stop mock server
        if (tomcat != null) {
            tomcat.stop();
            tomcat.getServer().await();
        }

        // wait for server to shutdown
        TimeUnit.MILLISECONDS.sleep(500);
    }

    @Override
    public int getServerPort() {
        return SERVER_HTTP_PORT;
    }

    @Override
    public int getServerSecurePort() {
        return SERVER_HTTPS_PORT;
    }

}
