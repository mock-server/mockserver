package org.mockserver.junit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.integration.ClientAndProxy;
import org.mockserver.socket.PortFactory;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class ProxyRuleTestWithMocks {

    @Mock
    private Statement mockStatement;

    @Mock
    private ClientAndProxy mockClientAndProxy;

    private ProxyClient mockServerClient;

    @Mock
    private ProxyRule.ClientAndProxyFactory clientAndProxyFactory;

    private int httpPort;
    private int httpsPort;

    @InjectMocks
    private ProxyRule mockServerRuleDynamicPorts;
    @InjectMocks
    private ProxyRule mockServerRuleHttpPortOnly;
    @InjectMocks
    private ProxyRule mockServerRuleBothPorts;
    @InjectMocks
    private ProxyRule mockServerRulePerSuite;
    @InjectMocks
    private ProxyRule mockServerRulePerSuiteDuplicate;

    @Before
    public void setupFixture() {
        httpPort = PortFactory.findFreePort();
        httpsPort = PortFactory.findFreePort();

        mockServerRuleDynamicPorts = new ProxyRule(this);
        mockServerRuleHttpPortOnly = new ProxyRule(httpPort, null, this, false);
        mockServerRuleBothPorts = new ProxyRule(httpPort, httpsPort, this, false);
        mockServerRulePerSuite = new ProxyRule(httpPort, httpsPort, this, true);
        mockServerRulePerSuiteDuplicate = new ProxyRule(httpPort, httpsPort, this, true);

        initMocks(this);

        when(clientAndProxyFactory.newClientAndProxy()).thenReturn(mockClientAndProxy);
    }

    @Test
    public void shouldStartAndStopProxyWithDynamicPort() throws Throwable {
        // when
        mockServerRuleDynamicPorts.apply(mockStatement, Description.EMPTY).evaluate();

        // then
        assertThat((ClientAndProxy) mockServerClient, is(mockClientAndProxy));
        verify(mockStatement).evaluate();
        verify(mockClientAndProxy).stop();
    }

    @Test
    public void shouldStartAndStopProxyWithHTTPPortOnly() throws Throwable {
        // when
        mockServerRuleHttpPortOnly.apply(mockStatement, Description.EMPTY).evaluate();

        // then
        assertThat((ClientAndProxy) mockServerClient, sameInstance(mockClientAndProxy));
        assertThat(mockServerRuleHttpPortOnly.getHttpPort(), is(httpPort));
        assertThat(mockServerRuleHttpPortOnly.getHttpsPort(), is(nullValue()));
        verify(mockStatement).evaluate();
        verify(mockClientAndProxy).stop();
    }

    @Test
    public void shouldStartAndStopProxyWithHTTPAndHTTPSPort() throws Throwable {
        // when
        mockServerRuleBothPorts.apply(mockStatement, Description.EMPTY).evaluate();

        // then
        assertThat((ClientAndProxy) mockServerClient, sameInstance(mockClientAndProxy));
        assertThat(mockServerRuleBothPorts.getHttpPort(), is(httpPort));
        assertThat(mockServerRuleBothPorts.getHttpsPort(), is(httpsPort));
        verify(mockStatement).evaluate();
        verify(mockClientAndProxy).stop();
    }

    @Test
    public void shouldStartAndStopProxyOncePerSuite() throws Throwable {
        // when
        mockServerRulePerSuite.apply(mockStatement, Description.EMPTY).evaluate();

        // then
        assertThat((ClientAndProxy) mockServerClient, is(mockClientAndProxy));
        assertThat(mockServerRulePerSuite.getHttpPort(), is(httpPort));
        assertThat(mockServerRulePerSuite.getHttpsPort(), is(httpsPort));
        verify(mockStatement).evaluate();
        verify(clientAndProxyFactory, times(1)).newClientAndProxy();
        verify(mockClientAndProxy, times(0)).stop();

        reset(mockStatement, clientAndProxyFactory);

        // when
        mockServerRulePerSuiteDuplicate.apply(mockStatement, Description.EMPTY).evaluate();

        // then
        assertThat((ClientAndProxy) mockServerClient, is(mockClientAndProxy));
        assertThat(mockServerRulePerSuiteDuplicate.getHttpPort(), is(httpPort));
        assertThat(mockServerRulePerSuiteDuplicate.getHttpsPort(), is(httpsPort));
        verify(mockStatement).evaluate();
        verify(clientAndProxyFactory, times(0)).newClientAndProxy();
        verify(mockClientAndProxy, times(0)).stop();
    }
}
