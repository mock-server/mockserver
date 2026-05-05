package org.mockserver.testing.tls;

import com.google.common.annotations.VisibleForTesting;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.socket.tls.KeyStoreFactory;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.Socket;

import static org.mockserver.configuration.Configuration.configuration;

/**
 * @author jamesdbloom
 */
public class SSLSocketFactory {

    public static SSLSocketFactory sslSocketFactory() {
        return new SSLSocketFactory();
    }

    @VisibleForTesting
    public synchronized SSLSocket wrapSocket(Socket socket) throws IOException {
        // ssl socket factory
        javax.net.ssl.SSLSocketFactory sslSocketFactory = new KeyStoreFactory(configuration(), new MockServerLogger()).sslContext().getSocketFactory();

        // ssl socket
        SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(socket, socket.getInetAddress().getHostAddress(), socket.getPort(), true);
        sslSocket.setUseClientMode(true);
        sslSocket.startHandshake();
        return sslSocket;
    }

    @VisibleForTesting
    public synchronized SSLServerSocket wrapSocket() throws IOException {
        // ssl socket factory
        SSLServerSocketFactory sslSocketFactory = new KeyStoreFactory(configuration(), new MockServerLogger()).sslContext().getServerSocketFactory();

        // ssl socket
        return (SSLServerSocket) sslSocketFactory.createServerSocket(0);
    }
}
