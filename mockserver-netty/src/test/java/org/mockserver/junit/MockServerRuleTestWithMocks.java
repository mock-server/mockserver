package org.mockserver.junit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.socket.PortFactory;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.theInstance;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class MockServerRuleTestWithMocks {

    @Mock
    private Statement mockStatement;

    @Mock
    private ClientAndServer mockClientAndServer;

    private MockServerClient mockServerClient;

    @Mock
    private MockServerRule.ClientAndServerFactory clientAndServerFactory;

    private int httpPort;
    private int httpsPort;

    @InjectMocks
    private MockServerRule mockServerRuleDynamicPorts;
    @InjectMocks
    private MockServerRule mockServerRuleHttpPortOnly;
    @InjectMocks
    private MockServerRule mockServerRuleBothPorts;
    @InjectMocks
    private MockServerRule mockServerRulePerSuite;
    @InjectMocks
    private MockServerRule mockServerRulePerSuiteDuplicate;

    @Before
    public void setupFixture() {
        httpPort = PortFactory.findFreePort();
        httpsPort = PortFactory.findFreePort();

        mockServerRuleDynamicPorts = new MockServerRule(this);
        mockServerRuleHttpPortOnly = new MockServerRule(httpPort, null, this, false);
        mockServerRuleBothPorts = new MockServerRule(httpPort, httpsPort, this, false);
        mockServerRulePerSuite = new MockServerRule(httpPort, httpsPort, this, true);
        mockServerRulePerSuiteDuplicate = new MockServerRule(httpPort, httpsPort, this, true);

        initMocks(this);

        when(clientAndServerFactory.newClientAndServer()).thenReturn(mockClientAndServer);
    }

    @Test
    public void shouldStartAndStopMockServerWithDynamicPort() throws Throwable {
        // when
        mockServerRuleDynamicPorts.apply(mockStatement, Description.EMPTY).evaluate();

        // then
        assertThat((ClientAndServer) mockServerClient, is(mockClientAndServer));
        verify(mockStatement).evaluate();
        verify(mockClientAndServer).stop();
    }

    @Test
    public void shouldStartAndStopMockServerWithHTTPPortOnly() throws Throwable {
        // when
        mockServerRuleHttpPortOnly.apply(mockStatement, Description.EMPTY).evaluate();

        // then
        assertThat((ClientAndServer) mockServerClient, is(theInstance(mockClientAndServer)));
        assertThat(mockServerRuleHttpPortOnly.getHttpPort(), is(httpPort));
        assertThat(mockServerRuleHttpPortOnly.getHttpsPort(), is(nullValue()));
        verify(mockStatement).evaluate();
        verify(mockClientAndServer).stop();
    }

    @Test
    public void shouldStartAndStopMockServerWithHTTPAndHTTPSPort() throws Throwable {
        // when
        mockServerRuleBothPorts.apply(mockStatement, Description.EMPTY).evaluate();

        // then
        assertThat((ClientAndServer) mockServerClient, is(theInstance(mockClientAndServer)));
        assertThat(mockServerRuleBothPorts.getHttpPort(), is(httpPort));
        assertThat(mockServerRuleBothPorts.getHttpsPort(), is(httpsPort));
        verify(mockStatement).evaluate();
        verify(mockClientAndServer).stop();
    }

    @Test
    public void shouldStartAndStopMockServerOncePerSuite() throws Throwable {
        // when
        mockServerRulePerSuite.apply(mockStatement, Description.EMPTY).evaluate();

        // then
        assertThat((ClientAndServer) mockServerClient, is(mockClientAndServer));
        assertThat(mockServerRulePerSuite.getHttpPort(), is(httpPort));
        assertThat(mockServerRulePerSuite.getHttpsPort(), is(httpsPort));
        verify(mockStatement).evaluate();
        verify(clientAndServerFactory, times(1)).newClientAndServer();
        verify(mockClientAndServer, times(0)).stop();

        reset(mockStatement, clientAndServerFactory);

        // when
        mockServerRulePerSuiteDuplicate.apply(mockStatement, Description.EMPTY).evaluate();

        // then
        assertThat((ClientAndServer) mockServerClient, is(mockClientAndServer));
        assertThat(mockServerRulePerSuiteDuplicate.getHttpPort(), is(httpPort));
        assertThat(mockServerRulePerSuiteDuplicate.getHttpsPort(), is(httpsPort));
        verify(mockStatement).evaluate();
        verify(clientAndServerFactory, times(0)).newClientAndServer();
        verify(mockClientAndServer, times(0)).stop();
    }
}
