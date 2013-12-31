package org.mockserver.runner;

import ch.qos.logback.classic.Level;
import com.google.common.util.concurrent.SettableFuture;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.ShutdownThread;
import org.mockserver.proxy.connect.ConnectHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import static org.mockserver.configuration.SystemProperties.*;

/**
 * @author jamesdbloom
 */
public abstract class AbstractRunner {
    private static final Logger logger = LoggerFactory.getLogger(AbstractRunner.class);
    Server server;

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
                } catch (GeneralSecurityException | IOException e) {
                    logger.error("Exception while loading SSL certificate", e);
                }
                server.setConnectors(serverConnectors.toArray(new Connector[serverConnectors.size()]));

                // add handler
                ServletHandler servletHandler = new ServletHandler();
                servletHandler.addServletWithMapping(new ServletHolder(getServlet()), "/");
                if (securePort != null) {
                    server.setHandler(new ConnectHandler(servletHandler, securePort));
                } else {
                    server.setHandler(servletHandler);
                }

                // start server
                try {
                    runStopThread(stopPort(port, securePort), stopKey());
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

    protected abstract HttpServlet getServlet();

    protected void extendHTTPConfig(HttpConfiguration https_config) {
        // allow subclasses to extend http configuration
    }

    private ServerConnector createHTTPConnector(Server server, Integer port, Integer securePort) {
        // HTTP Configuration
        HttpConfiguration http_config = new HttpConfiguration();
        if (securePort != null) {
            http_config.setSecurePort(securePort);
        }
        http_config.setOutputBufferSize(bufferSize());
        http_config.setRequestHeaderSize(bufferSize());
        http_config.setResponseHeaderSize(bufferSize());
        extendHTTPConfig(http_config);

        // HTTP connector
        ServerConnector http = new ServerConnector(server, new HttpConnectionFactory(http_config));
        http.setPort(port);
        http.setIdleTimeout(maxTimeout());
        return http;
    }

    private ServerConnector createHTTPSConnector(Server server, Integer securePort) throws GeneralSecurityException, IOException {
        // SSL Context Factory for HTTPS
        KeyStore keystore = CertificateBuilder.generateCertificate(
                "certAlias",
                "changeit".toCharArray(),
                CertificateBuilder.KeyAlgorithmName.RSA,
                "CN=www.mockserver.com, O=MockServer, L=London, S=England, C=UK"
        );
//        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
//        keystore.load(this.getClass().getClassLoader().getResourceAsStream("keystore.jks"), "changeit".toCharArray());
        SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setKeyStore(keystore);
        sslContextFactory.setKeyStorePassword("changeit");
        sslContextFactory.checkKeyStore();

        // HTTPS Configuration
        HttpConfiguration https_config = new HttpConfiguration();
        https_config.setSecurePort(securePort);
        https_config.setOutputBufferSize(bufferSize());
        https_config.setRequestHeaderSize(bufferSize());
        https_config.setResponseHeaderSize(bufferSize());
        https_config.addCustomizer(new SecureRequestCustomizer());
        extendHTTPConfig(https_config);

        // HTTPS connector
        ServerConnector https = new ServerConnector(server, new SslConnectionFactory(sslContextFactory, "http/1.1"), new HttpConnectionFactory(https_config));
        https.setPort(securePort);
        https.setIdleTimeout(maxTimeout());
        return https;
    }

    private void runStopThread(final int stopPort, final String stopKey) {
        // ensure only single stop thread start for both proxy and server
        synchronized (AbstractRunner.class) {
            try {
                ShutdownMonitor shutdownMonitor = ShutdownMonitor.getInstance();
                Method isAliveMethod = ShutdownMonitor.class.getDeclaredMethod("isAlive");
                isAliveMethod.setAccessible(true);
                if (!(Boolean) isAliveMethod.invoke(shutdownMonitor)) {
                    logger.info("Listening on stopPort " + stopPort + " for stop requests with stopKey [" + stopKey + "]");
                    System.out.println("Listening on stopPort " + stopPort + " for stop requests with stopKey [" + stopKey + "]");

                    shutdownMonitor.setPort(stopPort);
                    shutdownMonitor.setKey(stopKey);
                    shutdownMonitor.setExitVm(false);
                    Method startMethod = ShutdownMonitor.class.getDeclaredMethod("start");
                    startMethod.setAccessible(true);
                    startMethod.invoke(shutdownMonitor);
                }
            } catch (Exception e) {
                logger.warn("Exception while using reflection to call protected methods on ShutdownMonitor", e);
            }
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

    /**
     * Stop a forked or remote MockServer instance
     *
     * @param ipAddress IP address as string of remote MockServer (i.e. "127.0.0.1")
     * @param stopPort  the stopPort for the MockServer to stop (default is HTTP port + 1)
     * @param stopKey   the stopKey for the MockServer to step (default is "STOP_KEY")
     * @param stopWait  the period to wait for MockServer to confirm it has stopped, in seconds.  A value of <= 0 means do not wait for confirmation MockServer has stopped.
     */
    public boolean stop(String ipAddress, int stopPort, String stopKey, int stopWait) {
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

            boolean stopped = true;
            if (stopWait > 0) {
                stopped = false;
                s.setSoTimeout(stopWait * 1000);
                s.getInputStream();

                logger.info("Waiting %d seconds for MockServer to stop%n", stopWait);
                LineNumberReader lin = new LineNumberReader(new InputStreamReader(s.getInputStream()));
                String response;

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
            return stopped;
        } catch (ConnectException e) {
            logger.info("MockServer is not running");
            return false;
        } catch (Exception e) {
            logger.error("Exception stopping MockServer", e);
            return false;
        }
    }
}
