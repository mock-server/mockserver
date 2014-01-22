package org.mockserver.tomcat.runner;

import ch.qos.logback.classic.Level;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.commons.io.Charsets;
import org.mockserver.socket.SSLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public abstract class AbstractRunner<T extends AbstractRunner<T>> {
    static {
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.net.preferIPv6Addresses", "false");
    }

    private static final Logger logger = LoggerFactory.getLogger(AbstractRunner.class);
    ShutdownThread shutdownThread;

    /**
     * Override the debug WARN logging level
     *
     * @param level the log level, which can be ALL, DEBUG, INFO, WARN, ERROR, OFF
     */
    public void overrideLogLevel(String level) {
        Logger rootLogger = LoggerFactory.getLogger("org.mockserver");
        if (rootLogger instanceof ch.qos.logback.classic.Logger) {
            ((ch.qos.logback.classic.Logger) rootLogger).setLevel(Level.toLevel(level));
        }
    }

    Tomcat tomcat;

    public void stopServer() throws Exception {
        tomcat.stop();
        tomcat.getServer().await();
        serverStopped();
    }

    /**
     * Start the instance using the ports provided
     *
     * @param port the http port to use
     * @param securePort the secure https port to use
     */
    @SuppressWarnings("unchecked")
    public T start(final Integer port, final Integer securePort) {
        if (port == null && securePort == null) throw new IllegalStateException("You must specify a port or a secure port");
        if (isRunning()) throw new IllegalStateException("Server already running");
        final String startedMessage = "Started " + this.getClass().getSimpleName().replace("Runner", "") + " listening on:" + (port != null ? " standard port " + port : "") + (securePort != null ? " secure port " + securePort : "");

        try {
            String servletContext = "";

            tomcat = new Tomcat();
            tomcat.setBaseDir(new File(".").getCanonicalPath() + File.separatorChar + "tomcat" + (servletContext.length() > 0 ? "_" + servletContext : ""));

            // add http port
            tomcat.setPort(port != null ? port : securePort);

            if (securePort != null) {
                // add https connector
                SSLFactory.buildKeyStore();
                Connector httpsConnector = new Connector();
                httpsConnector.setPort(securePort);
                httpsConnector.setSecure(true);
                httpsConnector.setAttribute("keyAlias", SSLFactory.KEY_STORE_ALIAS);
                httpsConnector.setAttribute("keystorePass", SSLFactory.KEY_STORE_PASSWORD);
                logger.trace("Loading key store from file [" + new File(SSLFactory.KEY_STORE_FILENAME).getAbsoluteFile() + "]");
                httpsConnector.setAttribute("keystoreFile", new File(SSLFactory.KEY_STORE_FILENAME).getAbsoluteFile());
                httpsConnector.setAttribute("clientAuth", "false");
                httpsConnector.setAttribute("sslProtocol", "TLS");
                httpsConnector.setAttribute("SSLEnabled", true);

                Service service = tomcat.getService();
                service.addConnector(httpsConnector);

                Connector defaultConnector = tomcat.getConnector();
                defaultConnector.setRedirectPort(securePort);
            }

            // add servlet
            Context ctx = tomcat.addContext("/" + servletContext, new File(".").getAbsolutePath());
            tomcat.addServlet("/" + servletContext, "mockServerServlet", getServlet());
            ctx.addServletMapping("/*", "mockServerServlet");

            // start server
            tomcat.start();

            // create and start shutdown thread
            shutdownThread = new ShutdownThread(stopPort(port, securePort));
            shutdownThread.start();
            serverStarted(port, securePort);

            logger.info(startedMessage);
            System.out.println(startedMessage);

            join();
        } catch (Throwable t) {
            logger.error("Exception while starting server", t);
        }

        return (T) this;
    }

    protected abstract HttpServlet getServlet();

    protected abstract int stopPort(final Integer port, final Integer securePort);

    protected void serverStarted(final Integer port, final Integer securePort) {
        // allow subclasses to run post start logic
    }

    protected void serverStopped() {
        // allow subclasses to run post start logic
    }

    public void join() throws InterruptedException {
        tomcat.getServer().await();
    }

    public void join(long millis) throws InterruptedException {
        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    join();
                } catch (InterruptedException e) {
                    throw new RuntimeException("InterruptedException while attempting to join server ThreadPool", e);
                }
            }
        });
        thread.start();
        thread.join(millis);
    }

    /**
     * Is this instance running?
     */
    public boolean isRunning() {
        return tomcat != null && tomcat.getServer().getState().isAvailable() && tomcat.getServer().getState().getLifecycleEvent().equals(LifecycleState.STARTED.getLifecycleEvent());
    }

    /**
     * Stop this MockServer instance
     */
    public AbstractRunner<T> stop() {
        if (!isRunning()) throw new IllegalStateException(this.getClass().getSimpleName().replace("Runner", "") + " is not running");
        try {
            serverStopped();
            shutdownThread.stopListening();
            tomcat.stop();
            tomcat.getServer().await();
        } catch (Exception e) {
            throw new RuntimeException("Failed to stop embedded jetty server gracefully, stopping JVM", e);
        }
        return this;
    }

    /**
     * Stop a forked or remote MockServer instance
     *
     * @param ipAddress IP address as string of remote MockServer (i.e. "127.0.0.1")
     * @param stopPort  the stopPort for the MockServer to stop (default is HTTP port + 1)
     * @param stopWait  the period to wait for MockServer to confirm it has stopped, in seconds.  A value of <= 0 means do not wait for confirmation MockServer has stopped.
     */
    public boolean stop(String ipAddress, int stopPort, int stopWait) {
        if (stopPort <= 0)
            throw new IllegalArgumentException("Please specify a valid stopPort");

        boolean stopped = false;
        try {
            Socket socket = null;
            try {
                socket = new Socket(InetAddress.getByName(ipAddress), stopPort);

                if (socket.isConnected() && socket.isBound()) {
                    OutputStream out = socket.getOutputStream();
                    out.write("stop".getBytes(Charsets.UTF_8));
                    socket.shutdownOutput();

                    if (stopWait > 0) {
                        socket.setSoTimeout(stopWait * 1000);

                        logger.info("Waiting " + stopWait + " seconds for MockServer to stop");

                        if (new BufferedReader(new InputStreamReader(socket.getInputStream())).readLine().contains("stopped")) {
                            logger.info("MockServer has stopped");
                            stopped = true;
                        }
                    } else {
                        stopped = true;
                    }
                }
            } finally {
                if (socket != null) {
                    socket.close();
                }
            }
            TimeUnit.MILLISECONDS.sleep(100);
        } catch (Throwable t) {
            logger.error("Exception stopping MockServer", t);
            stopped = false;
        }
        return stopped;
    }

    class ShutdownThread extends Thread {
        private final int port;
        private ServerSocket serverSocket;

        public ShutdownThread(int port) {
            this.port = port;
            setDaemon(true);
            setName("ShutdownThread");
        }

        @Override
        public void run() {
            try {
                try {
                    serverSocket = new ServerSocket(port);
                    logger.info("Waiting to receive MockServer stop request on port [" + port + "]");

                    while (serverSocket.isBound() && !serverSocket.isClosed()) {
                        Socket socket = null;
                        try {
                            socket = serverSocket.accept();

                            if (new BufferedReader(new InputStreamReader(socket.getInputStream())).readLine().contains("stop")) {
                                // shutdown server
                                AbstractRunner.this.stop();

                                // inform client
                                OutputStream out = socket.getOutputStream();
                                out.write("stopped".getBytes(Charsets.UTF_8));
                            }
                        } finally {
                            if (socket != null) {
                                socket.close();
                            }
                        }
                    }
                } finally {
                    serverSocket.close();
                }
            } catch (Exception e) {
                logger.trace("Exception while creating " + this.getClass().getSimpleName().replace("Runner", "") + " shutdown thread", e);
            }
        }

        public void stopListening() {
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException ioe) {
                    logger.error("Exception in shutdown thread", ioe);
                }
            }
        }

    }
}
