package org.mockserver.integration.proxy;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.After;
import org.junit.Before;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class ServerRunner {

    private Server server;

    @Before
    public void startServer(int httpPort, int httpsPort, SslContextFactory sslContextFactory) throws Exception {
        server = new Server();
        addServerConnector(server, httpPort, null);
        addServerConnector(server, httpsPort, sslContextFactory);
        server.setHandler(new AbstractHandler() {
            public void handle(String target, Request request, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException, ServletException {
                String uri = httpServletRequest.getRequestURI();
                if ("/test_headers_only".equals(uri)) {
                    request.setHandled(true);
                    httpServletResponse.setStatus(304);
                    httpServletResponse.setHeader("X-Test", "test_headers_only");
                } else if ("/test_headers_and_body".equals(uri)) {
                    request.setHandled(true);
                    httpServletResponse.setStatus(200);
                    httpServletResponse.setHeader("X-Test", "test_headers_and_body");
                    httpServletResponse.getOutputStream().print("an_example_body");
                }
            }
        });
        server.start();
    }

    private void addServerConnector(Server server, int port, SslContextFactory sslContextFactory) throws Exception {
        ServerConnector serverConnector = new ServerConnector(server);
        if (sslContextFactory != null) {
            serverConnector = new ServerConnector(server, sslContextFactory);
        }
        serverConnector.setPort(port);
        server.addConnector(serverConnector);
    }

    @After
    public void stopServer() throws Exception {
        server.stop();
    }
}
