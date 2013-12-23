package org.mockserver.runner;

import ch.qos.logback.classic.Level;
import com.google.common.util.concurrent.SettableFuture;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.ShutdownThread;
import org.mockserver.proxy.ProxyRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/**
 * @author jamesdbloom
 */
public abstract class AbstractRunner {
    private static final Logger logger = LoggerFactory.getLogger(ProxyRunner.class);
    private static final String STOP_KEY = "STOP_KEY";
    private Server server;

    /**
     * Override the debug WARN logging level
     *
     * @param level the log level, which can be ALL, DEBUG, INFO, WARN, ERROR, OFF
     */
    public static void overrideLogLevel(String level) {
        Logger rootLogger = LoggerFactory.getLogger("org.mockserver");
        if (rootLogger instanceof ch.qos.logback.classic.Logger) {
            ((ch.qos.logback.classic.Logger) rootLogger).setLevel(Level.toLevel(level));
        }
    }

    /**
     * Stop a forked or remote MockServer instance
     *
     * @param ipAddress IP address as string of remote MockServer (i.e. "127.0.0.1")
     * @param stopPort  the stopPort for the MockServer to stop (default is HTTP port + 1)
     * @param stopKey   the stopKey for the MockServer to step (default is "STOP_KEY")
     * @param stopWait  the period to wait for MockServer to confirm it has stopped, in seconds.  A value of <= 0 means do not wait for confirmation MockServer has stopped.
     */
    public static void stopRemote(String ipAddress, int stopPort, String stopKey, int stopWait) {
        if (stopPort <= 0)
            throw new IllegalArgumentException("Please specify a valid stopPort");
        if (stopKey == null)
            throw new IllegalArgumentException("Please specify a valid stopKey");

        try {
            Socket s = new Socket(InetAddress.getByName(ipAddress), stopPort);
            s.setSoLinger(false, 0);

            OutputStream out = s.getOutputStream();
            out.write((stopKey + "\r\nstop\r\n").getBytes());
            out.flush();

            if (stopWait > 0) {
                s.setSoTimeout(stopWait * 1000);
                s.getInputStream();

                logger.info("Waiting %d seconds for MockServer to stop%n", stopWait);
                LineNumberReader lin = new LineNumberReader(new InputStreamReader(s.getInputStream()));
                String response;
                boolean stopped = false;
                while (!stopped && ((response = lin.readLine()) != null)) {
                    if ("Stopped".equals(response)) {
                        stopped = true;
                        logger.info("MockServer has stopped");
                    }
                }
            } else {
                logger.info("MockServer stop http has been sent");
            }
            s.close();
        } catch (ConnectException e) {
            logger.info("MockServer is not running");
        } catch (Exception e) {
            logger.error("Exception stopping MockServer", e);
        }
    }

    /**
     * Start the MockServer instance in the port provided using -Dmockserver.stopPort and -Dmockserver.stopKey for the stopPort and stopKey respectively.
     * If -Dmockserver.stopPort is not provided the default value used will be the port parameter + 1.
     * If -Dmockserver.stopKey is not provided the default value used will be "STOP_KEY"
     *
     * @param port the port the listens to incoming HTTP requests
     * @return A Future that returns the state of the MockServer once it is stopped, this Future can be used to block execution until the MockServer is stopped.
     */
    public Future start(final Integer port, final Integer securePort) {
        if (port == null && securePort == null) throw new IllegalStateException("You must specify a port or a secure port");
        if (isRunning()) throw new IllegalStateException("Server already running");
        final String startedMessage = "Started " + this.getClass().getSimpleName().replace("Runner", "") + " listening on:" + (port != null ? " standard port " + port : "") + (securePort != null ? " secure port " + securePort : "");

        final SettableFuture<String> future = SettableFuture.create();
        new Thread(new Runnable() {
            @Override
            public void run() {
                server = new Server();

                // add connectors
                List<ServerConnector> serverConnectors = new ArrayList<>();
                if (port != null) {
                    serverConnectors.add(createHTTPConnector(server, port, securePort));
                }
                try {
                    if (securePort != null) {
                        serverConnectors.add(createHTTPSConnector(server, securePort));
                    }
                } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
                    logger.error("Exception while loading SSL certificate", e);
                }
                server.setConnectors(serverConnectors.toArray(new Connector[serverConnectors.size()]));

                // add handler
                ServletHandler handler = new ServletHandler();
                handler.addServletWithMapping(getServletName(), "/");
                server.setHandler(handler);

                // start server
                try {
                    runStopThread(
                            Integer.parseInt(System.getProperty("mockserver.stopPort", "" + (Math.max((port != null ? port : 0), (securePort != null ? securePort : 0)) + 1))),
                            System.getProperty("mockserver.stopKey", STOP_KEY)
                    );
                    ShutdownThread.register(server);
                    server.start();
                } catch (Exception e) {
                    logger.error("Failed to start embedded jetty server", e);
                    System.exit(1);
                }

                logger.info(startedMessage);
                System.out.println(startedMessage);

                try {
                    server.join();
                } catch (InterruptedException ie) {
                    logger.error("InterruptedException while waiting for jetty server", ie);
                } finally {
                    future.set(server.getState());
                }
            }
        }).start();
        return future;
    }

    protected abstract String getServletName();

    private ServerConnector createHTTPConnector(Server server, Integer port, Integer securePort) {
        // HTTP Configuration
        HttpConfiguration http_config = new HttpConfiguration();
        if (securePort != null) {
            http_config.setSecurePort(securePort);
        }
        http_config.setSecureScheme("https");
        http_config.setOutputBufferSize(32768);

        // HTTP connector
        ServerConnector http = new ServerConnector(server, new HttpConnectionFactory(http_config));
        http.setPort(port);
        http.setIdleTimeout(30000);
        return http;
    }

    private ServerConnector createHTTPSConnector(Server server, Integer securePort) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        // keytool -genkey -keyalg RSA -alias selfsigned -keystore keystore.jks -storepass changeit -validity 360 -keysize 2048

        // SSL Context Factory for HTTPS and SPDY
        SslContextFactory sslContextFactory = new SslContextFactory();
        KeyStore keystore = KeyStore.getInstance("JKS");
        keystore.load(this.getClass().getClassLoader().getResourceAsStream("keystore.jks"), "changeit".toCharArray());
        sslContextFactory.setKeyStorePassword("changeit");
        sslContextFactory.setKeyManagerPassword("changeit");
        sslContextFactory.setKeyStore(keystore);

        // HTTPS Configuration
        HttpConfiguration https_config = new HttpConfiguration();
        https_config.setSecurePort(securePort);
        https_config.setSecureScheme("https");
        https_config.setOutputBufferSize(32768);
        https_config.addCustomizer(new SecureRequestCustomizer());

        // HTTPS connector
        ServerConnector https = new ServerConnector(server, new SslConnectionFactory(sslContextFactory, "http/1.1"), new HttpConnectionFactory(https_config));
        https.setPort(securePort);
        https.setIdleTimeout(500000);
        return https;
    }

    private void runStopThread(final int stopPort, final String stopKey) {
        ShutdownMonitor shutdownMonitor = ShutdownMonitor.getInstance();
        if (!shutdownMonitor.isAlive()) {
            logger.info("Listening on stopPort " + stopPort + " for stop requests with stopKey [" + stopKey + "]");
            System.out.println("Listening on stopPort " + stopPort + " for stop requests with stopKey [" + stopKey + "]");

            shutdownMonitor.setPort(stopPort);
            shutdownMonitor.setKey(stopKey);
            shutdownMonitor.setExitVm(false);
            shutdownMonitor.start();
        }
    }

    /**
     * Is this instance running?
     */
    public boolean isRunning() {
        return server != null && server.isRunning();
    }

    /**
     * Stop this MockServer instance
     */
    public AbstractRunner stop() {
        if (!isRunning()) throw new IllegalStateException("Server is not running");
        try {
            server.stop();
        } catch (Exception e) {
            logger.error("Failed to stop embedded jetty server gracefully, stopping JVM", e);
            System.exit(1);
        }
        return this;
    }
}
