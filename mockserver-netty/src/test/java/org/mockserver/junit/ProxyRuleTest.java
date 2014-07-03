package org.mockserver.junit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.integration.ClientAndProxy;
import org.mockserver.socket.PortFactory;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

/**
 * @author jamesdbloom
 */
public class ProxyRuleTest {
    @Rule
    public ProxyRule proxyRule = new ProxyRule(this);

    private ProxyClient proxyClient;

    @Test
    public void shouldSetTestMockServeField() {
        assertThat(proxyClient, is(not(nullValue())));
    }

    @Test
    public void shouldStartAndStopProxyWithDynamicPort() throws Throwable {
        // given
        ProxyRule proxyRule = spy(new ProxyRule(this));
        ClientAndProxy mockClientAndProxy = mock(ClientAndProxy.class);
        Statement mockStatement = mock(Statement.class);
        when(proxyRule.newClientAndProxy()).thenReturn(mockClientAndProxy);

        // when
        proxyRule.apply(mockStatement, Description.EMPTY).evaluate();

        // then
        assertThat((ClientAndProxy) proxyClient, is(mockClientAndProxy));
        verify(mockStatement).evaluate();
        verify(mockClientAndProxy).stop();
    }

    @Test
    public void shouldStartAndStopProxyWithHTTPPortOnly() throws Throwable {
        // given
        int httpPort = PortFactory.findFreePort();
        ProxyRule proxyRule = spy(new ProxyRule(httpPort, this));
        ClientAndProxy mockClientAndProxy = mock(ClientAndProxy.class);
        Statement mockStatement = mock(Statement.class);
        when(proxyRule.newClientAndProxy()).thenReturn(mockClientAndProxy);

        // when
        proxyRule.apply(mockStatement, Description.EMPTY).evaluate();

        // then
        assertThat((ClientAndProxy) proxyClient, is(mockClientAndProxy));
        assertThat(proxyRule.getHttpPort(), is(httpPort));
        verify(mockStatement).evaluate();
        verify(mockClientAndProxy).stop();
    }

    @Test
    public void shouldStartAndStopProxyWithHTTPAndHTTPSPort() throws Throwable {
        // given
        int httpPort = PortFactory.findFreePort();
        int httpsPort = PortFactory.findFreePort();
        ProxyRule proxyRule = spy(new ProxyRule(httpPort, httpsPort, this));
        ClientAndProxy mockClientAndProxy = mock(ClientAndProxy.class);
        Statement mockStatement = mock(Statement.class);
        when(proxyRule.newClientAndProxy()).thenReturn(mockClientAndProxy);

        // when
        proxyRule.apply(mockStatement, Description.EMPTY).evaluate();

        // then
        assertThat((ClientAndProxy) proxyClient, is(mockClientAndProxy));
        assertThat(proxyRule.getHttpPort(), is(httpPort));
        verify(mockStatement).evaluate();
        verify(mockClientAndProxy).stop();
    }
}
