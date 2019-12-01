package org.mockserver.examples.proxy;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Format;
import org.mockserver.socket.PortFactory;
import org.mockserver.socket.tls.KeyStoreFactory;

import javax.net.ssl.HttpsURLConnection;

public class AnalyseSystemExampleTestClass {

    private static ClientAndServer proxy;

    @BeforeClass
    public static void startProxyAndServer() throws Exception {
        // ensure all connection using HTTPS will use the SSL context defined by MockServer
        // this allows auto-generated self-signed certificate of the Proxy to be accepted
        HttpsURLConnection.setDefaultSSLSocketFactory(new KeyStoreFactory(new MockServerLogger()).sslContext().getSocketFactory());
        proxy = ClientAndServer.startClientAndServer(PortFactory.findFreePort());
    }

    @AfterClass
    public static void stopServerAndProxy() {
        proxy.retrieveRecordedExpectations(null, Format.JAVA);
        proxy.stop();
    }

    @Test
    public void shouldDoSomething() {

    }
}
