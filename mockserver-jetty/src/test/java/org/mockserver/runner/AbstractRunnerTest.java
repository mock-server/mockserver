package org.mockserver.runner;

import org.eclipse.jetty.servlet.DefaultServlet;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.proxy.connect.ConnectHandler;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * @author jamesdbloom
 */
public class AbstractRunnerTest {

    private AbstractRunner runner;

    @Before
    public void createRunner() {
        runner = new AbstractRunner() {
            protected String getServletName() {
                return DefaultServlet.class.getName();
            }
        };
    }

    @Test
    public void shouldStartOnNonSecurePortOnly() throws InterruptedException, ExecutionException, UnknownHostException {
        try {
            // when
            try {
                runner.start(9090, null).get(2, TimeUnit.SECONDS);
            } catch (TimeoutException te) {
                // ignore as expected
            }

            // then
            assertEquals("http://" + InetAddress.getLocalHost().getHostAddress() + ":9090/", runner.server.getURI().toString());
        } finally {
            runner.stop();
        }
    }

    @Test
    public void shouldStartOnSecurePortOnly() throws InterruptedException, ExecutionException, UnknownHostException {
        try {
            // when
            try {
                runner.start(null, 9090).get(2, TimeUnit.SECONDS);
            } catch (TimeoutException te) {
                // ignore as expected
            }

            // then
            assertNotNull(runner.server.getBean(ConnectHandler.class));
            assertEquals("https://" + InetAddress.getLocalHost().getHostAddress() + ":9090/", runner.server.getURI().toString());
        } finally {
            runner.stop();
        }
    }

    @Test(expected = IllegalStateException.class)
    public void shouldNotStartForBothPortsNull() {
        // when
        runner.start(null, null);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldNotStartTwice() throws InterruptedException, ExecutionException, UnknownHostException {
        try {
            // when
            try {
                runner.start(9090, null).get(2, TimeUnit.SECONDS);
            } catch (TimeoutException te) {
                // ignore as expected
            }
            try {
                runner.start(9091, null).get(2, TimeUnit.SECONDS);
            } catch (TimeoutException te) {
                // ignore as expected
            }
        } finally {
            runner.stop();
        }
    }

    @Test(expected = IllegalStateException.class)
    public void shouldNotStopIfNotStarted() {
        runner.stop();
    }

    @Test(expected = IllegalStateException.class)
    public void shouldNotStopTwice() throws InterruptedException, ExecutionException, UnknownHostException {
        try {
            // when
            try {
                runner.start(9090, null).get(2, TimeUnit.SECONDS);
            } catch (TimeoutException te) {
                // ignore as expected
            }
            runner.stop();
        } finally {
            runner.stop();
        }
    }

    @Test
    public void shouldStopRemoteServer() throws InterruptedException, ExecutionException, UnknownHostException {
        // when
        try {
            runner.start(9090, null).get(2, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            // ignore as expected
        }

        // then
        assertTrue(AbstractRunner.stopRemote("127.0.0.1", 9090 + 1, "STOP_KEY", 2));
    }

    @Test
    public void shouldIndicateIfCanNotStopRemoteServer() throws InterruptedException, ExecutionException, UnknownHostException {
        // when
        try {
            runner.start(9090, null).get(2, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            // ignore as expected
        }
        AbstractRunner.stopRemote("127.0.0.1", 9090 + 1, "STOP_KEY", 2);

        // then
        assertFalse(AbstractRunner.stopRemote("127.0.0.1", 9090 + 1, "STOP_KEY", 2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldValidatePortArgument() {
        AbstractRunner.stopRemote("127.0.0.1", -1, "STOP_KEY", 2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldValidateStopKeyArgument() {
        AbstractRunner.stopRemote("127.0.0.1", 9090, null, 2);
    }
}
