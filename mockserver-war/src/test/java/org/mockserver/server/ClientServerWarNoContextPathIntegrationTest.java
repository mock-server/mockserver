package org.mockserver.server;

import org.apache.catalina.Context;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.integration.server.AbstractClientServerIntegrationTest;
import org.mockserver.socket.PortFactory;
import org.mockserver.socket.SSLFactory;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class ClientServerWarNoContextPathIntegrationTest extends AbstractClientServerIntegrationTest {

    private final static int serverPort = PortFactory.findFreePort();
    private final static int serverSecurePort = PortFactory.findFreePort();
    private static Tomcat tomcat;

    @BeforeClass
    public static void startServer() throws Exception {
        servletContext = "";

        tomcat = new Tomcat();
        tomcat.setBaseDir(new File(".").getCanonicalPath() + File.separatorChar + "tomcat" + (servletContext.length() > 0 ? "_" + servletContext : ""));

        // add http port
        tomcat.setPort(serverPort);

        // add https connector
        SSLFactory.buildKeyStore();
        Connector httpsConnector = new Connector();
        httpsConnector.setPort(serverSecurePort);
        httpsConnector.setSecure(true);
        httpsConnector.setAttribute("keyAlias", SSLFactory.KEY_STORE_ALIAS);
        httpsConnector.setAttribute("keystorePass", SSLFactory.KEY_STORE_PASSWORD);
        httpsConnector.setAttribute("keystoreFile", new File(SSLFactory.KEY_STORE_FILENAME).getAbsoluteFile());
        httpsConnector.setAttribute("sslProtocol", "TLS");
        httpsConnector.setAttribute("clientAuth", false);
        httpsConnector.setAttribute("SSLEnabled", true);

        Service service = tomcat.getService();
        service.addConnector(httpsConnector);

        Connector defaultConnector = tomcat.getConnector();
        defaultConnector.setRedirectPort(serverSecurePort);

        // add servlet
        Context ctx = tomcat.addContext("/" + servletContext, new File(".").getAbsolutePath());
        tomcat.addServlet("/" + servletContext, "mockServerServlet", new MockServerServlet());
        ctx.addServletMapping("/*", "mockServerServlet");

        // start server
        tomcat.start();

        // start client
        mockServerClient = new MockServerClient("localhost", serverPort, servletContext);
    }

    @AfterClass
    public static void stopServer() throws Exception {
        tomcat.stop();
        tomcat.getServer().await();
    }

    @Override
    public int getPort() {
        return serverPort;
    }

    @Override
    public int getSecurePort() {
        return serverSecurePort;
    }
}
