package org.mockserver.jetty.runner;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.jetty.proxy.connect.ConnectHandler;
import org.mockserver.jetty.server.MockServerRunner;
import org.mockserver.server.MockServerServlet;
import org.mockserver.socket.PortFactory;

import javax.servlet.http.HttpServlet;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.*;

/**
 * @author jamesdbloom
 */
public class AbstractRunnerTest {

    private final int port = PortFactory.findFreePort();
    private final int stopPort = PortFactory.findFreePort();
    private AbstractRunner runner;

    @Before
    public void createRunner() {
        runner = new AbstractRunner() {
            protected HttpServlet getServlet() {
                return new MockServerServlet();
            }

            @Override
            protected int stopPort(Integer port, Integer securePort) {
                return stopPort;
            }
        };
    }

    @Test
    public void shouldStartOnNonSecurePortOnly() throws InterruptedException, ExecutionException, UnknownHostException {
        try {
            // when
            runner.start(port, null);

            // then
            assertEquals("http://" + InetAddress.getLocalHost().getHostAddress() + ":" + port + "/", runner.server.getURI().toString());
        } finally {
            runner.stop();
        }
    }

    @Test
    public void shouldStartOnSecurePortOnly() throws InterruptedException, ExecutionException, UnknownHostException {
        try {
            // when
            runner.start(null, port);

            // then
            assertNotNull(runner.server.getBean(ConnectHandler.class));
            assertEquals("https://" + InetAddress.getLocalHost().getHostAddress() + ":" + port + "/", runner.server.getURI().toString());
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
            runner.start(port, null);
            runner.start(port + 1, null);
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
            runner.start(port, null);
            runner.stop();
        } finally {
            runner.stop();
        }
    }

    @Test
    public void shouldStopRemoteServerAndWaitForResponse() throws InterruptedException, ExecutionException, UnknownHostException {
        try {
            // when
            runner.start(port, null);

            // then
            assertTrue(new MockServerRunner().stop(InetAddress.getLocalHost().getHostAddress(), stopPort, 30));
        } finally {
            try {
                runner.stop();
            } catch (RuntimeException re) {
                // do nothing only here in case stop above fails
            }
        }
    }

    @Test
    public void shouldStopRemoteServerAndNotWaitForResponse() throws InterruptedException, ExecutionException, UnknownHostException {
        try {
            // when
            runner.start(port, null);

            // then
            assertTrue(new MockServerRunner().stop(InetAddress.getLocalHost().getHostAddress(), stopPort, 0));
        } finally {
            try {
                runner.stop();
            } catch (RuntimeException re) {
                // do nothing only here in case stop above fails
            }
        }
    }

    @Test
    public void shouldIndicateIfCanNotStopRemoteServer() throws InterruptedException, ExecutionException, UnknownHostException {
        // when
        runner.start(port, null);
        new MockServerRunner().stop(InetAddress.getLocalHost().getHostAddress(), stopPort, 5);

        // then
        assertFalse(new MockServerRunner().stop(InetAddress.getLocalHost().getHostAddress(), stopPort, 3));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldValidatePortArgument() throws UnknownHostException {
        new MockServerRunner().stop(InetAddress.getLocalHost().getHostAddress(), -1, 5);
    }
}
