package org.jamesdbloom.mockserver.server;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;

/**
 * @author jamesdbloom
 */
public class EmbeddedJettyRunner {

    private Server server;

    public static void main(String[] args) throws Exception {
        int port = args.length == 1 ? Integer.parseInt(args[0]) : 8080;
        new EmbeddedJettyRunner().start(port);
    }

    public EmbeddedJettyRunner start(int port) throws Exception {
        server = new Server(port);
        ServletHandler handler = new ServletHandler();
        server.setHandler(handler);

        handler.addServletWithMapping(MockServerServlet.class.getName(), "/");

        server.start();
        return this;
    }

    public EmbeddedJettyRunner stop() throws Exception {
        server.stop();
        return this;
    }
}
