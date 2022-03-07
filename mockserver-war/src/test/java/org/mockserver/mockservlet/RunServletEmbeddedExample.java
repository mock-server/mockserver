package org.mockserver.mockservlet;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.socket.PortFactory;
import org.mockserver.socket.tls.KeyStoreFactory;

import java.io.File;
import java.io.IOException;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.mockserver.configuration.Configuration.configuration;

public class RunServletEmbeddedExample {

    private static final int SERVER_HTTP_PORT = PortFactory.findFreePort();
    private static final int SERVER_HTTPS_PORT = PortFactory.findFreePort();

    @SuppressWarnings("ConstantConditions")
    public static void main(String[] args) throws IOException, LifecycleException, InterruptedException {

        System.out.println("SERVER_HTTP_PORT = " + SERVER_HTTP_PORT);
        System.out.println("SERVER_HTTPS_PORT = " + SERVER_HTTPS_PORT);

        ConfigurationProperties.initializationJsonPath("/Users/jamesbloom/git/mockserver/mockserver/mockserverInitialization.json");
        ConfigurationProperties.watchInitializationJson(true);

        Tomcat tomcat = new Tomcat();
        String servletContext = "";
        tomcat.setBaseDir(new File(".").getCanonicalPath() + File.separatorChar + "tomcat" + (servletContext.length() > 0 ? "_" + servletContext : ""));

        // add http port
        tomcat.setPort(SERVER_HTTP_PORT);
        Connector defaultConnector = tomcat.getConnector();
        defaultConnector.setRedirectPort(SERVER_HTTPS_PORT);

        // add https connector
        KeyStoreFactory keyStoreFactory = new KeyStoreFactory(configuration(), new MockServerLogger());
        keyStoreFactory.loadOrCreateKeyStore();
        Connector httpsConnector = new Connector();
        httpsConnector.setPort(SERVER_HTTPS_PORT);
        httpsConnector.setSecure(true);
        httpsConnector.setProperty("keyAlias", KeyStoreFactory.KEY_STORE_CERT_ALIAS);
        httpsConnector.setProperty("keystorePass", KeyStoreFactory.KEY_STORE_PASSWORD);
        httpsConnector.setProperty("keystoreFile", new File(keyStoreFactory.keyStoreFileName).getAbsoluteFile().toString());
        httpsConnector.setProperty("sslProtocol", "TLS");
        httpsConnector.setProperty("clientAuth", "false");
        httpsConnector.setProperty("SSLEnabled", "true");

        Service service = tomcat.getService();
        service.addConnector(httpsConnector);

        // add servlet
        Context ctx = tomcat.addContext("/" + servletContext, new File(".").getAbsolutePath());
        tomcat.addServlet("/" + servletContext, "mockServerServlet", new MockServerServlet());
        ctx.addServletMappingDecoded("/*", "mockServerServlet");
        ctx.addApplicationListener(MockServerServlet.class.getName());

        // start server
        tomcat.start();

        MINUTES.sleep(10);
    }

}
