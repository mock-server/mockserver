package org.mockserver.integration.proxy;

import org.apache.catalina.Context;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.junit.After;
import org.junit.Before;
import org.mockserver.socket.SSLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class ServerRunner {

    private final static Logger logger = LoggerFactory.getLogger(ServerRunner.class);

    private static Tomcat tomcat;

    @Before
    public void startServer(int serverPort, int serverSecurePort) throws Exception {
        String servletContext = "";

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
        logger.trace("Loading key store from file [" + new File(SSLFactory.KEY_STORE_FILENAME).getAbsoluteFile() + "]");
        httpsConnector.setAttribute("keystoreFile", new File(SSLFactory.KEY_STORE_FILENAME).getAbsoluteFile());
        httpsConnector.setAttribute("clientAuth", "false");
        httpsConnector.setAttribute("sslProtocol", "TLSv1");
        httpsConnector.setAttribute("SSLEnabled", true);

        Service service = tomcat.getService();
        service.addConnector(httpsConnector);

        Connector defaultConnector = tomcat.getConnector();
        defaultConnector.setRedirectPort(serverSecurePort);

        // add servlet
        Context ctx = tomcat.addContext("/" + servletContext, new File(".").getAbsolutePath());
        tomcat.addServlet("/" + servletContext, "mockServerServlet", new DefaultServlet() {
            private static final long serialVersionUID = -8189383563517967790L;

            public void service(ServletRequest servletRequest, ServletResponse servletResponse) throws ServletException, IOException {
                HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
                HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;
                String uri = httpServletRequest.getRequestURI();
                if ("/test_headers_only".equals(uri)) {
                    httpServletResponse.setStatus(304);
                    httpServletResponse.setHeader("X-Test", "test_headers_only");
                } else if ("/test_headers_and_body".equals(uri)) {
                    httpServletResponse.setStatus(200);
                    httpServletResponse.setHeader("X-Test", "test_headers_and_body");
                    httpServletResponse.getOutputStream().print("an_example_body");
                } else {
                    httpServletResponse.setStatus(404);
                }
            }
        });
        ctx.addServletMapping("/*", "mockServerServlet");

        // start server
        tomcat.start();
    }

    @After
    public void stopServer() throws Exception {
        tomcat.stop();
        tomcat.getServer().await();
    }
}
