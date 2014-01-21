package org.mockserver.integration.proxy;

import org.junit.Test;

import static org.junit.Assert.assertSame;

/**
 * @author jamesdbloom
 */
public class SSLContextFactoryTest {

    @Test
    public void shouldAlwaysReturnSameSSLContext() {
        assertSame(SSLContextFactory.createSSLContextFactory(), new SSLContextFactory().createSSLContextFactory());
    }
}
