package org.mockserver.socket;

import javax.net.ssl.SSLSocket;
import java.net.Socket;

import static org.mockserver.socket.KeyStoreFactory.keyStoreFactory;

/**
 * @author jamesdbloom
 */
public class SSLSocketFactory {

    public static SSLSocketFactory sslSocketFactory() {
        return new SSLSocketFactory();
    }

    public synchronized SSLSocket wrapSocket(Socket socket) throws Exception {
        // ssl socket factory
        javax.net.ssl.SSLSocketFactory sslSocketFactory = keyStoreFactory().sslContext().getSocketFactory();

        // ssl socket
        SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(socket, socket.getInetAddress().getHostAddress(), socket.getPort(), true);
        sslSocket.setUseClientMode(true);
        sslSocket.startHandshake();
        return sslSocket;
    }
}
