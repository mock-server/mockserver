package org.mockserver.junit;

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

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class MockServerClassRuleTestWithMocks {

    @Mock
    private Statement mockStatement;

    @Mock
    private ClientAndServer mockClientAndServer;

    private static MockServerClient mockServerClient;

    @Mock
    private MockServerRule.ClientAndServerFactory clientAndServerFactory;

    private int httpPort;

    @InjectMocks
    private MockServerRule mockServerRuleDynamicPorts;

    @Before
    public void setupFixture() {
        httpPort = PortFactory.findFreePort();

        MockServerRule.perTestSuiteClientAndServer = null;
        mockServerRuleDynamicPorts = new MockServerRule(MockServerClassRuleTestWithMocks.class);

        initMocks(this);

        when(mockClientAndServer.getPort()).thenReturn(httpPort);
        when(mockClientAndServer.getLocalPorts()).thenReturn(Arrays.asList(httpPort + 1, httpPort + 2));
        when(clientAndServerFactory.newClientAndServer()).thenReturn(mockClientAndServer);
    }

    @Test
    public void shouldStartAndStopMockServerWithDynamicPort() throws Throwable {
        // when
        mockServerRuleDynamicPorts.apply(mockStatement, Description.EMPTY).evaluate();

        // then
        assertThat(mockServerClient, instanceOf(ClientAndServer.class));
        assertThat(mockServerRuleDynamicPorts.getPorts(), is(new Integer[]{httpPort + 1, httpPort + 2}));
        verify(mockStatement).evaluate();
        verify(mockClientAndServer, times(0)).stop();
    }

}
