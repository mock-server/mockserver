package org.mockserver.proxy;

import org.apache.catalina.Context;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.integration.proxy.AbstractClientProxyIntegrationTest;
import org.mockserver.integration.proxy.ServerRunner;
import org.mockserver.socket.PortFactory;
import org.mockserver.socket.SSLFactory;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class ClientProxyWarPathIntegrationTest extends AbstractClientProxyIntegrationTest {

    private final static int proxyPort = PortFactory.findFreePort();
    private final static int serverPort = PortFactory.findFreePort();
    private final static int serverSecurePort = PortFactory.findFreePort();
    private final static ServerRunner serverRunner = new ServerRunner();
    private static Tomcat tomcat;
    private static ProxyClient proxyClient;

    @BeforeClass
    public static void startServer() throws Exception {
        serverRunner.startServer(serverPort, serverSecurePort);
        // wait for server to start up
        Thread.sleep(TimeUnit.MILLISECONDS.toMillis(500));
    }

    @BeforeClass
    public static void startProxy() throws Exception {
        tomcat = new Tomcat();
        tomcat.setBaseDir(new File(".").getCanonicalPath() + File.separatorChar + "tomcat");

        // add http port
        tomcat.setPort(proxyPort);

        // add servlet
        Context ctx = tomcat.addContext("/", new File(".").getAbsolutePath());
        tomcat.addServlet("/", "mockServerServlet", new ProxyServlet());
        ctx.addServletMapping("/*", "mockServerServlet");

        // start server
        tomcat.start();

        // start client
        proxyClient = new ProxyClient("localhost", proxyPort);
    }

    @AfterClass
    public static void stopProxy() throws Exception {
        tomcat.stop();
        tomcat.getServer().await();
    }

    @AfterClass
    public static void stopServer() throws Exception {
        serverRunner.stopServer();
        // wait for server to shutdown
        Thread.sleep(TimeUnit.SECONDS.toMillis(1));
    }

    @Override
    public int getProxyPort() {
        return proxyPort;
    }

    @Override
    public int getServerPort() {
        return serverPort;
    }

    @Override
    public int getServerSecurePort() {
        return serverSecurePort;
    }
}
