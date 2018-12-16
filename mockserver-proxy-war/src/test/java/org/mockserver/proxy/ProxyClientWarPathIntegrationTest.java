package org.mockserver.proxy;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockserver.client.MockServerClient;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.integration.proxy.AbstractClientProxyIntegrationTest;
import org.mockserver.socket.PortFactory;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class ProxyClientWarPathIntegrationTest extends AbstractClientProxyIntegrationTest {

    private final static int PROXY_PORT = PortFactory.findFreePort();
    private static EchoServer echoServer;
    private static Tomcat tomcat;
    private static MockServerClient mockServerClient;

    @BeforeClass
    public static void setupFixture() throws Exception {
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
    public static void stopFixture() throws Exception {
        // stop client
        if (mockServerClient != null) {
            mockServerClient.stop();
        }

        // stop test server
        if (echoServer != null) {
            echoServer.stop();
        }

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
}
