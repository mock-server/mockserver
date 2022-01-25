package org.mockserver.junit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.socket.PortFactory;

import java.util.Arrays;

import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

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

    @InjectMocks
    private MockServerRule mockServerRuleDynamicPorts;
    @InjectMocks
    private MockServerRule mockServerRuleSinglePort;
    @InjectMocks
    private MockServerRule mockServerRuleMultiplePorts;
    @InjectMocks
    private MockServerRule mockServerRulePerSuite;
    @InjectMocks
    private MockServerRule mockServerRulePerSuiteDuplicate;

    @Before
    public void setupFixture() {
        httpPort = PortFactory.findFreePort();

        MockServerRule.perTestSuiteClientAndServer = null;
        mockServerRuleDynamicPorts = new MockServerRule(this);
        mockServerRuleSinglePort = new MockServerRule(this, false, httpPort);
        mockServerRuleMultiplePorts = new MockServerRule(this, false, httpPort, httpPort + 1);
        mockServerRulePerSuite = new MockServerRule(this, true, httpPort);
        mockServerRulePerSuiteDuplicate = new MockServerRule(this, true, httpPort);

        openMocks(this);

        when(mockClientAndServer.getPort()).thenReturn(httpPort);
        when(mockClientAndServer.getLocalPorts()).thenReturn(Arrays.asList(httpPort + 1, httpPort + 2));
        when(clientAndServerFactory.newClientAndServer()).thenReturn(mockClientAndServer);
    }

    @After
    public void cleanupFixture() {
        MockServerRule.perTestSuiteClientAndServer = null;
    }

    @Test
    public void shouldStartAndStopMockServerWithDynamicPort() throws Throwable {
        // when
        mockServerRuleDynamicPorts.apply(mockStatement, Description.EMPTY).evaluate();

        // then
        assertThat(mockServerClient, instanceOf(ClientAndServer.class));
        assertThat(mockServerRuleDynamicPorts.getPorts(), is(new Integer[]{httpPort + 1, httpPort + 2}));
        verify(mockStatement).evaluate();
        verify(clientAndServerFactory, times(1)).newClientAndServer();
        verify(mockClientAndServer, times(0)).stop();
    }

    @Test
    public void shouldStartAndStopMockServerWithSinglePort() throws Throwable {
        // when
        mockServerRuleSinglePort.apply(mockStatement, Description.EMPTY).evaluate();

        // then
        assertThat(mockServerClient, sameInstance(mockClientAndServer));
        assertThat(mockServerRuleSinglePort.getPort(), is(httpPort));
        assertThat(mockServerRuleSinglePort.getPorts(), is(new Integer[]{httpPort + 1, httpPort + 2}));
        verify(mockStatement).evaluate();
        verify(mockClientAndServer).stop();
    }

    @Test
    public void shouldStartAndStopMockServerWithMultiplePorts() throws Throwable {
        // when
        mockServerRuleMultiplePorts.apply(mockStatement, Description.EMPTY).evaluate();

        // then
        assertThat(mockServerClient, sameInstance(mockClientAndServer));
        assertThat(mockServerRuleMultiplePorts.getPort(), is(httpPort));
        assertThat(mockServerRuleMultiplePorts.getPorts(), is(new Integer[]{httpPort + 1, httpPort + 2}));
        verify(mockStatement).evaluate();
        verify(mockClientAndServer).stop();
    }

    @Test
    public void shouldStartAndStopMockServerOncePerSuite() throws Throwable {
        // when
        mockServerRulePerSuite.apply(mockStatement, Description.EMPTY).evaluate();

        // then
        MockServerClient firstMockServerClient = mockServerClient;
        assertThat(mockServerClient, instanceOf(ClientAndServer.class));
        assertThat(mockServerRulePerSuite.getPort(), is(httpPort));
        assertThat(mockServerRulePerSuite.getPorts(), is(new Integer[]{httpPort + 1, httpPort + 2}));
        verify(mockStatement).evaluate();
        verify(clientAndServerFactory, times(1)).newClientAndServer();
        verify(mockClientAndServer, times(0)).stop();

        reset(mockStatement, clientAndServerFactory);

        // when
        mockServerRulePerSuiteDuplicate.apply(mockStatement, Description.EMPTY).evaluate();

        // then
        assertThat(mockServerClient, sameInstance(firstMockServerClient));
        assertThat(mockServerRulePerSuiteDuplicate.getPort(), is(httpPort));
        assertThat(mockServerRulePerSuiteDuplicate.getPorts(), is(new Integer[]{httpPort + 1, httpPort + 2}));
        verify(mockStatement).evaluate();
        verify(clientAndServerFactory, times(0)).newClientAndServer();
        verify(mockClientAndServer, times(0)).stop();
    }
}
