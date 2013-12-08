package org.mockserver;

import com.google.common.util.concurrent.SettableFuture;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.mockserver.integration.AbstractClientServerIntegrationTest;
import org.mockserver.model.HttpRequest;
import org.vertx.java.platform.impl.cli.Starter;

import java.util.concurrent.ExecutionException;

/**
 * @author jamesdbloom
 */
@Ignore
public class ClientServerVertxIntegrationTest extends AbstractClientServerIntegrationTest {

    public SettableFuture<String> settableFuture;

    private Thread vertxServer = new Thread(new Runnable() {
        public void run() {
            settableFuture.set("running");
            Starter.main(new String[]{"run", "org.mockserver.server.MockServerVertical"});
        }
    });

    @Before
    public void startServer() {
        settableFuture = SettableFuture.create();
        vertxServer.start();
        try {
            settableFuture.get();
        } catch (Exception e) {
            throw new RuntimeException("Error while waiting for Vert.X server thread to start");
        }
    }

    @Override
    public int getPort() {
        return 8080;
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
