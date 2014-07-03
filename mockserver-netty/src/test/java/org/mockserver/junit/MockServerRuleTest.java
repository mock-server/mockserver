package org.mockserver.junit;

import org.hamcrest.core.Is;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.socket.PortFactory;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

/**
 * @author jamesdbloom
 */
public class MockServerRuleTest {

@Rule
public MockServerRule mockServerRule = new MockServerRule(this);

private MockServerClient mockServerClient;

    @Test
    public void shouldSetTestMockServeField() {
        assertThat(mockServerClient, is(not(nullValue())));
    }

    @Test
    public void shouldStartAndStopMockServerWithDynamicPort() throws Throwable {
        // given
        MockServerRule mockServerRule = spy(new MockServerRule(this));
        ClientAndServer mockClientAndServer = mock(ClientAndServer.class);
        Statement mockStatement = mock(Statement.class);
        when(mockServerRule.newClientAndServer()).thenReturn(mockClientAndServer);

        // when
        mockServerRule.apply(mockStatement, Description.EMPTY).evaluate();

        // then
        assertThat((ClientAndServer)mockServerClient, is(mockClientAndServer));
        verify(mockStatement).evaluate();
        verify(mockClientAndServer).stop();
    }

    @Test
    public void shouldStartAndStopMockServerWithHTTPPortOnly() throws Throwable {
        // given
        int httpPort = PortFactory.findFreePort();
        MockServerRule mockServerRule = spy(new MockServerRule(this));
        ClientAndServer mockClientAndServer = mock(ClientAndServer.class);
        Statement mockStatement = mock(Statement.class);
        when(mockServerRule.newClientAndServer()).thenReturn(mockClientAndServer);

        // when
        mockServerRule.apply(mockStatement, Description.EMPTY).evaluate();

        // then
        assertThat((ClientAndServer)mockServerClient, is(mockClientAndServer));
        verify(mockStatement).evaluate();
        verify(mockClientAndServer).stop();
    }

    @Test
    public void shouldStartAndStopMockServerWithHTTPAndHTTPSPort() throws Throwable {
        // given
        int httpPort = PortFactory.findFreePort();
        int httpsPort = PortFactory.findFreePort();
        MockServerRule mockServerRule = spy(new MockServerRule(this));
        ClientAndServer mockClientAndServer = mock(ClientAndServer.class);
        Statement mockStatement = mock(Statement.class);
        when(mockServerRule.newClientAndServer()).thenReturn(mockClientAndServer);

        // when
        mockServerRule.apply(mockStatement, Description.EMPTY).evaluate();

        // then
        assertThat((ClientAndServer)mockServerClient, is(mockClientAndServer));
        verify(mockStatement).evaluate();
        verify(mockClientAndServer).stop();
    }
}
