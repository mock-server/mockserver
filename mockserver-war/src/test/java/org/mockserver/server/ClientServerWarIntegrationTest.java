package org.mockserver.server;

import org.eclipse.jetty.server.*;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.After;
import org.junit.Before;
import org.mockserver.integration.proxy.SSLContextFactory;
import org.mockserver.integration.server.AbstractClientServerIntegrationTest;
import org.mockserver.socket.PortFactory;

import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class ClientServerWarIntegrationTest extends AbstractClientServerIntegrationTest {

    private final int serverPort = PortFactory.findFreePort();
    private final int serverSecurePort = PortFactory.findFreePort();
    private Server server;

    @Before
    public void startServer() throws Exception {
        server = new Server();

        // add http connector
        ServerConnector http = new ServerConnector(server);
        http.setPort(serverPort);
        server.addConnector(http);

        // add https connector
        HttpConfiguration https_config = new HttpConfiguration();
        https_config.addCustomizer(new SecureRequestCustomizer());
        ServerConnector https = new ServerConnector(server, new SslConnectionFactory(SSLContextFactory.createSSLContextFactory(), "http/1.1"), new HttpConnectionFactory(https_config));
        https.setPort(serverSecurePort);
        server.addConnector(https);

        // add handler
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/" + getServletContext());
        server.setHandler(context);
        context.addServlet(new ServletHolder(new MockServerServlet()), "/*");

        // start server
        server.start();

        TimeUnit.SECONDS.sleep(1);
    }

    public String getServletContext() {
        return "mockserver";
    }

    @Override
    public int getPort() {
        return serverPort;
    }

    @Override
    public int getSecurePort() {
        return serverSecurePort;
    }

    @After
    public void stopServer() throws Exception {
        server.stop();
        server.join();
    }
}
