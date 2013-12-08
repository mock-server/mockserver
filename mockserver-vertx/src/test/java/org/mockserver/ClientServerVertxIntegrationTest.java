package org.mockserver;

import com.google.common.util.concurrent.SettableFuture;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.mockserver.integration.AbstractClientServerIntegrationTest;
import org.mockserver.model.HttpRequest;
import org.vertx.java.platform.impl.cli.Starter;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class ClientServerVertxIntegrationTest extends AbstractClientServerIntegrationTest {

    public SettableFuture<String> settableFuture;

    private Thread vertxServer = new Thread(new Runnable() {
        public void run() {
            Starter.main(new String[]{"run", "org.mockserver.server.MockServerVertical"});
        }
    });

    @Before
    public void startServer() throws InterruptedException {
        vertxServer.start();
        vertxServer.join(TimeUnit.SECONDS.toMillis(2));
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
