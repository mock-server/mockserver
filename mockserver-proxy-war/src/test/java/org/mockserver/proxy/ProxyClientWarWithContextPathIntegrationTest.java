package org.mockserver.proxy;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.echo.EchoServer;
import org.mockserver.integration.proxy.AbstractClientProxyIntegrationTest;
import org.mockserver.socket.PortFactory;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class ProxyClientWarWithContextPathIntegrationTest extends AbstractClientProxyIntegrationTest {

    private final static int PROXY_PORT = PortFactory.findFreePort();
    private final static int SERVER_HTTP_PORT = PortFactory.findFreePort();
    private static EchoServer echoServer;
    private static Tomcat tomcat;
    private static ProxyClient proxyClient;

    @BeforeClass
    public static void setupFixture() throws Exception {
        servletContext = "proxy";

        // start server
        echoServer = new EchoServer(SERVER_HTTP_PORT);

        // wait for server to start up
        TimeUnit.MILLISECONDS.sleep(500);

        // start proxy (in tomcat)
        tomcat = new Tomcat();
        tomcat.setBaseDir(new File(".").getCanonicalPath() + File.separatorChar + "tomcat" + (servletContext.length() > 0 ? "_" + servletContext : ""));

        // add http port
        tomcat.setPort(PROXY_PORT);

        // add servlet
        Context ctx = tomcat.addContext("/" + servletContext, new File(".").getAbsolutePath());
        tomcat.addServlet("/" + servletContext, "mockServerServlet", new ProxyServlet());
        ctx.addServletMapping("/*", "mockServerServlet");

        // start server
        tomcat.start();

        // start client
        proxyClient = new ProxyClient("localhost", PROXY_PORT);
    }

    @AfterClass
    public static void stopFixture() throws Exception {
        // stop server
        echoServer.stop();

        // stop proxy
        tomcat.stop();
        tomcat.getServer().await();

        // wait for server to shutdown
        TimeUnit.MILLISECONDS.sleep(500);
    }

    @Before
    public void resetProxy() {
        proxyClient.reset();
    }

    @Override
    public int getProxyPort() {
        return PROXY_PORT;
    }

    @Override
    public int getServerPort() {
        return SERVER_HTTP_PORT;
    }
}
