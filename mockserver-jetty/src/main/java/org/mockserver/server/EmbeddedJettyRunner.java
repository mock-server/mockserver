package org.mockserver.server;

import ch.qos.logback.classic.Level;
import com.google.common.util.concurrent.SettableFuture;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;

/**
 * @author jamesdbloom
 */
public class EmbeddedJettyRunner {
    private static Logger logger = LoggerFactory.getLogger(EmbeddedJettyRunner.class);

    private Server server;

    public static void main(String[] args) throws Exception {
        int port = args.length == 1 ? Integer.parseInt(args[0]) : 8080;
        new EmbeddedJettyRunner().start(port);
        logger.info("Started mock server listening on " + port);
        System.out.println("Started mock server listening on " + port);
    }

    public static void overrideLogLevel(String level) {
        ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.toLevel(level));
    }

    public Future start(final int port) {
        final SettableFuture<String> future = SettableFuture.create();
        new Thread(new Runnable() {
            @Override
            public void run() {
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

                try {
                    server.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    future.set(server.getState());
                }
            }
        }).start();
        return future;
    }

    public boolean isRunning() {
        return server != null && server.isRunning();
    }

    public EmbeddedJettyRunner stop() throws Exception {
        server.stop();
        return this;
    }
}
