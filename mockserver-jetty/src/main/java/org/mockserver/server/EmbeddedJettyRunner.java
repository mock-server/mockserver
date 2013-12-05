package org.mockserver.server;

import ch.qos.logback.classic.Level;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jamesdbloom
 */
public class EmbeddedJettyRunner {
    private static Logger logger = LoggerFactory.getLogger(EmbeddedJettyRunner.class);

    private final Server server;

    public static void main(String[] args) throws Exception {
        int port = args.length == 1 ? Integer.parseInt(args[0]) : 8080;
        new EmbeddedJettyRunner(port);
        logger.info("Started mock server listening on " + port);
        System.out.println("Started mock server listening on " + port);
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
            System.exit(1);
        }
    }

    public static void overrideLogLevel(String level) {
        ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.toLevel(level));
    }

    public EmbeddedJettyRunner stop() throws Exception {
        server.stop();
        return this;
    }
}
