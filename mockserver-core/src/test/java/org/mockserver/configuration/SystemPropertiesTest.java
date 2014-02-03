package org.mockserver.configuration;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

/**
 * @author jamesdbloom
 */
public class SystemPropertiesTest {

    String propertiesBeforeTest;

    @Before
    public void backupProperties() throws IOException {
        StringWriter stringWriter = new StringWriter();
        System.getProperties().store(stringWriter, "");
        propertiesBeforeTest = stringWriter.toString();
    }

    @After
    public void restoreProperties() throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(propertiesBeforeTest));
        System.setProperties(properties);
    }

    @Test
    public void shouldSetAndReadMaxTimeout() {
        // given
        System.clearProperty("mockserver.maxTimeout");

        // when
        assertEquals(TimeUnit.SECONDS.toMillis(SystemProperties.DEFAULT_MAX_TIMEOUT), new SystemProperties().maxTimeout());
        SystemProperties.maxTimeout(100);

        // then
        assertEquals(100, SystemProperties.maxTimeout());
    }

    @Test(expected = RuntimeException.class)
    public void shouldThrowRuntimeExceptionForInvalidMaxTimeout() {
        // given
        System.setProperty("mockserver.maxTimeout", "invalid");

        // then
        SystemProperties.maxTimeout();
    }

    @Test
    public void shouldSetAndReadBufferSize() {
        // given
        System.clearProperty("mockserver.requestBufferSize");

        // when
        assertEquals(SystemProperties.DEFAULT_BUFFER_SIZE, SystemProperties.bufferSize());
        SystemProperties.bufferSize(100);

        // then
        assertEquals(100, SystemProperties.bufferSize());
    }

    @Test(expected = RuntimeException.class)
    public void shouldThrowRuntimeExceptionForInvalidBufferSize() {
        // given
        System.setProperty("mockserver.requestBufferSize", "invalid");

        // then
        SystemProperties.bufferSize();
    }

    @Test
    public void shouldSetAndReadServerStopPort() {
        // given
        System.clearProperty("mockserver.serverStopPort");

        // when
        assertEquals(3, SystemProperties.serverStopPort(1, 2));
        SystemProperties.serverStopPort(10);

        // then
        assertEquals(10, SystemProperties.serverStopPort(1, 2));
    }

    @Test
    public void shouldReturnDefaultServerStopPort() {
        assertEquals(1, SystemProperties.serverStopPort(null, null));
        assertEquals(2, SystemProperties.serverStopPort(1, null));
        assertEquals(3, SystemProperties.serverStopPort(null, 2));
    }

    @Test(expected = RuntimeException.class)
    public void shouldThrowRuntimeExceptionForInvalidServerStopPort() {
        // given
        System.setProperty("mockserver.serverStopPort", "invalid");

        // then
        SystemProperties.serverStopPort(1, 2);
    }

    @Test
    public void shouldSetAndReadProxyStopPort() {
        // given
        System.clearProperty("mockserver.proxyStopPort");

        // when
        assertEquals(3, SystemProperties.proxyStopPort(1, 2));
        SystemProperties.proxyStopPort(10);

        // then
        assertEquals(10, SystemProperties.proxyStopPort(1, 2));
    }

    @Test
    public void shouldReturnDefaultProxyStopPort() {
        assertEquals(1, SystemProperties.proxyStopPort(null, null));
        assertEquals(2, SystemProperties.proxyStopPort(1, null));
        assertEquals(3, SystemProperties.proxyStopPort(null, 2));
    }

    @Test(expected = RuntimeException.class)
    public void shouldThrowRuntimeExceptionForInvalidProxyStopPort() {
        // given
        System.setProperty("mockserver.proxyStopPort", "invalid");

        // then
        SystemProperties.proxyStopPort(1, 2);
    }

    @Test
    public void shouldSetAndReadSocksPort() {
        // given
        System.clearProperty("mockserver.socksPort");

        // when
        assertEquals(-1, SystemProperties.socksPort());
        SystemProperties.socksPort(100);

        // then
        assertEquals(100, SystemProperties.socksPort());
    }

    @Test(expected = RuntimeException.class)
    public void shouldThrowRuntimeExceptionForInvalidSocksPort() {
        // given
        System.setProperty("mockserver.socksPort", "invalid");

        // then
        SystemProperties.socksPort();
    }
}
