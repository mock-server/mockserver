package org.mockserver.server;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jamesdbloom
 */
public class EmbeddedJettyRunner {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private final Server server;

    public static void main(String[] args) throws Exception {
        int port = args.length == 1 ? Integer.parseInt(args[0]) : 8080;
        new EmbeddedJettyRunner(port);
    }

    public EmbeddedJettyRunner(int port) {
        server = new Server(port);
        ServletHandler handler = new ServletHandler();
        server.setHandler(handler);

        handler.addServletWithMapping(MockServerServlet.class.getName(), "/");

        try {
            server.start();
        } catch (Exception e) {
            logger.error("Failed to start embedded jetty server", e);
            throw new RuntimeException("Failed to start embedded jetty server", e);
        }
    }

    public EmbeddedJettyRunner stop() throws Exception {
        server.stop();
        return this;
    }
}
