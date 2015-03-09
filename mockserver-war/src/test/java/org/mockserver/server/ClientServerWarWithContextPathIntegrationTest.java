package org.mockserver.server;

import org.apache.catalina.Context;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.echo.EchoServer;
import org.mockserver.socket.PortFactory;
import org.mockserver.socket.SSLFactory;

import java.io.File;

/**
 * @author jamesdbloom
 */
public class ClientServerWarWithContextPathIntegrationTest extends AbstractClientServerSharedClassloadersAndTestClasspathIntegrationTest {

    private final static int SERVER_HTTP_PORT = PortFactory.findFreePort();
    private final static int SERVER_HTTPS_PORT = PortFactory.findFreePort();
    private final static int TEST_SERVER_HTTP_PORT = PortFactory.findFreePort();
    private static Tomcat tomcat;
    private static EchoServer echoServer;

    @BeforeClass
    public static void startServer() throws Exception {
        servletContext = "mockserver";

        tomcat = new Tomcat();
        tomcat.setBaseDir(new File(".").getCanonicalPath() + File.separatorChar + "tomcat" + (servletContext.length() > 0 ? "_" + servletContext : ""));

        // add http port
        tomcat.setPort(SERVER_HTTP_PORT);

        // add https connector
        SSLFactory.getInstance().buildKeyStore();
        Connector httpsConnector = new Connector();
        httpsConnector.setPort(SERVER_HTTPS_PORT);
        httpsConnector.setSecure(true);
        httpsConnector.setAttribute("keyAlias", SSLFactory.KEY_STORE_CERT_ALIAS);
        httpsConnector.setAttribute("keystorePass", SSLFactory.keyStorePassword);
        httpsConnector.setAttribute("keystoreFile", new File(SSLFactory.KEY_STORE_FILENAME).getAbsoluteFile());
        httpsConnector.setAttribute("sslProtocol", "TLS");
        httpsConnector.setAttribute("clientAuth", false);
        httpsConnector.setAttribute("SSLEnabled", true);

        Service service = tomcat.getService();
        service.addConnector(httpsConnector);

        Connector defaultConnector = tomcat.getConnector();
        defaultConnector.setRedirectPort(SERVER_HTTPS_PORT);

        // add servlet
        Context ctx = tomcat.addContext("/" + servletContext, new File(".").getAbsolutePath());
        tomcat.addServlet("/" + servletContext, "mockServerServlet", new MockServerServlet());
        ctx.addServletMapping("/*", "mockServerServlet");

        // start server
        tomcat.start();

        // start test server
        echoServer = new EchoServer(TEST_SERVER_HTTP_PORT);

        // start client
        mockServerClient = new MockServerClient("localhost", SERVER_HTTP_PORT, servletContext);
    }

    @AfterClass
    public static void stopServer() throws Exception {
        // stop mock server
        tomcat.stop();
        tomcat.getServer().await();

        // stop test server
        echoServer.stop();
    }

    @Override
    public int getMockServerPort() {
        return SERVER_HTTP_PORT;
    }

    @Override
    public int getMockServerSecurePort() {
        return SERVER_HTTPS_PORT;
    }

    @Override
    public int getTestServerPort() {
        return TEST_SERVER_HTTP_PORT;
    }
}
