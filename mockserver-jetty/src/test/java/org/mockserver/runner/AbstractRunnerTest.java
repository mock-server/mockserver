package org.mockserver.runner;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.proxy.connect.ConnectHandler;
import org.mockserver.server.MockServerRunner;
import org.mockserver.server.MockServerServlet;

import javax.servlet.http.HttpServlet;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.*;
import static org.mockserver.configuration.SystemProperties.stopPort;

/**
 * @author jamesdbloom
 */
public class AbstractRunnerTest {

    private AbstractRunner runner;

    @Before
    public void createRunner() {
        runner = new AbstractRunner() {
            protected HttpServlet getServlet() {
                return new MockServerServlet();
            }
        };
    }

    @Test
    public void shouldStartOnNonSecurePortOnly() throws InterruptedException, ExecutionException, UnknownHostException {
        try {
            // when
            try {
                runner.start(2020, null).get(1, TimeUnit.SECONDS);
            } catch (TimeoutException te) {
                // ignore as expected
            }

            // then
            assertEquals("http://" + InetAddress.getLocalHost().getHostAddress() + ":2020/", runner.server.getURI().toString());
        } finally {
            runner.stop();
        }
    }

    @Test
    public void shouldStartOnSecurePortOnly() throws InterruptedException, ExecutionException, UnknownHostException {
        try {
            // when
            try {
                runner.start(null, 2020).get(1, TimeUnit.SECONDS);
            } catch (TimeoutException te) {
                // ignore as expected
            }

            // then
            assertNotNull(runner.server.getBean(ConnectHandler.class));
            assertEquals("https://" + InetAddress.getLocalHost().getHostAddress() + ":2020/", runner.server.getURI().toString());
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
                runner.start(2020, null).get(1, TimeUnit.SECONDS);
            } catch (TimeoutException te) {
                // ignore as expected
            }
            try {
                runner.start(2021, null).get(1, TimeUnit.SECONDS);
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
                runner.start(2020, null).get(1, TimeUnit.SECONDS);
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
        try {
            // when
            try {
                runner.start(2020, null).get(1, TimeUnit.SECONDS);
            } catch (TimeoutException te) {
                // ignore as expected
            }

            // then
            assertTrue(new MockServerRunner().stop("127.0.0.1", stopPort(2020, null), 30));
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
        try {
            runner.start(2020, null).get(1, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            // ignore as expected
        }
        new MockServerRunner().stop("127.0.0.1", stopPort(2020, null), 5);

        // then
        assertFalse(new MockServerRunner().stop("127.0.0.1", stopPort(2020, null), 3));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldValidatePortArgument() {
        new MockServerRunner().stop("127.0.0.1", -1, 5);
    }
}
