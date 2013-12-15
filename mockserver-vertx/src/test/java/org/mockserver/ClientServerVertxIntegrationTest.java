package org.mockserver;

import com.google.common.util.concurrent.SettableFuture;
import org.junit.After;
import org.junit.Before;
import org.mockserver.integration.server.AbstractClientServerIntegrationTest;
import org.mockserver.model.HttpRequest;
import org.vertx.java.platform.impl.cli.Starter;

import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class ClientServerVertxIntegrationTest extends AbstractClientServerIntegrationTest {

    public SettableFuture<String> settableFuture;

    private final Thread vertxServer = new Thread(new Runnable() {
        public void run() {
            System.setProperty("mockserver.port", "" + getPort());
            Starter.main(new String[]{"run", "org.mockserver.server.MockServerVertical"});
        }
    });

    @Before
    public void startServer() throws InterruptedException {
        vertxServer.start();
        vertxServer.join(TimeUnit.SECONDS.toMillis(1));
    }

    @Override
    public int getPort() {
        return 8085;
    }

    @After
    public void stopServer() {
        makeRequest(
                new HttpRequest()
                        .withMethod("PUT")
                        .withPath("/stop")
        );
    }
}
