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

    @Test
    public void shouldThrowRuntimeExceptionForInvalidMaxTimeout() {
        // given
        System.setProperty("mockserver.maxTimeout", "invalid");

        // then
        assertEquals(TimeUnit.SECONDS.toMillis(SystemProperties.DEFAULT_MAX_TIMEOUT), SystemProperties.maxTimeout());
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

    @Test
    public void shouldThrowRuntimeExceptionForInvalidBufferSize() {
        // given
        System.setProperty("mockserver.requestBufferSize", "invalid");

        // then
        assertEquals(SystemProperties.DEFAULT_BUFFER_SIZE, SystemProperties.bufferSize());
    }

    @Test
    public void shouldSetAndReadServerStopPort() {
        // given
        System.clearProperty("mockserver.mockServerHttpPort");

        // when
        assertEquals(-1, SystemProperties.mockServerHttpPort());
        SystemProperties.mockServerHttpPort(10);

        // then
        assertEquals(10, SystemProperties.mockServerHttpPort());
    }

    @Test
    public void shouldThrowRuntimeExceptionForInvalidServerStopPort() {
        // given
        System.setProperty("mockserver.mockServerHttpPort", "invalid");

        // then
        assertEquals(-1, SystemProperties.mockServerHttpPort());
    }

    @Test
    public void shouldSetAndReadProxyStopPort() {
        // given
        System.clearProperty("mockserver.proxyHttpPort");

        // when
        assertEquals(-1, SystemProperties.proxyHttpPort());
        SystemProperties.proxyHttpPort(10);

        // then
        assertEquals(10, SystemProperties.proxyHttpPort());
    }

    @Test
    public void shouldThrowRuntimeExceptionForInvalidProxyStopPort() {
        // given
        System.setProperty("mockserver.proxyHttpPort", "invalid");

        // then
        assertEquals(-1, SystemProperties.proxyHttpPort());
    }
}
