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

import static org.mockserver.configuration.Configuration.configuration;

public class AnalyseSystemExampleTestClass {

    private static ClientAndServer proxy;

    @BeforeClass
    public static void startProxyAndServer() {
        // ensure all connection using HTTPS will use the SSL context defined by
        // MockServer to allow dynamically generated certificates to be accepted
        HttpsURLConnection.setDefaultSSLSocketFactory(new KeyStoreFactory(configuration(), new MockServerLogger()).sslContext().getSocketFactory());
        proxy = ClientAndServer.startClientAndServer(PortFactory.findFreePort());
    }

    @AfterClass
    public static void stopServerAndProxy() {
        proxy.retrieveRecordedExpectations(null, Format.JAVA);
        proxy.stop();
    }

    @Test
    public void shouldDoSomething() {
        // send requests to system being analysed
    }
}
