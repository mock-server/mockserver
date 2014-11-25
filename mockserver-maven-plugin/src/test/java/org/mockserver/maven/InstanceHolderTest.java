package org.mockserver.maven;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.mockserver.MockServer;
import org.mockserver.mockserver.MockServerBuilder;
import org.mockserver.proxy.http.HttpProxy;
import org.mockserver.proxy.http.HttpProxyBuilder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.Is.isA;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class InstanceHolderTest {

    @Mock
    private HttpProxyBuilder mockProxyBuilder;
    @Mock
    private MockServerBuilder mockMockServerBuilder;

    @Mock
    private HttpProxy mockProxy;
    @Mock
    private MockServer mockMockServer;

    @Mock
    private MockServerClient mockMockServerClient;
    @Mock
    private ProxyClient mockProxyClient;

    @InjectMocks
    private InstanceHolder instanceHolder;

    @Before
    public void setupMock() {
        instanceHolder = new InstanceHolder();

        initMocks(this);

        when(mockMockServerBuilder.withHTTPPort(anyInt())).thenReturn(mockMockServerBuilder);
        when(mockMockServerBuilder.withHTTPSPort(anyInt())).thenReturn(mockMockServerBuilder);
        when(mockProxyBuilder.withHTTPPort(anyInt())).thenReturn(mockProxyBuilder);
        when(mockProxyBuilder.withHTTPSPort(anyInt())).thenReturn(mockProxyBuilder);

        when(mockProxy.isRunning()).thenReturn(false);
        when(mockMockServer.isRunning()).thenReturn(false);
    }

    @After
    public void shutdownProxyAndMockServer() {
        instanceHolder.stop();
    }

    @Test
    public void shouldStartServerAndProxyOnBothPorts() {
        // when
        instanceHolder.start(1, 2, 3, 4, null);

        // then
        verify(mockMockServerBuilder).withHTTPPort(1);
        verify(mockMockServerBuilder).withHTTPSPort(2);
        verify(mockProxyBuilder).withHTTPPort(3);
        verify(mockProxyBuilder).withHTTPSPort(4);
    }

    @Test
    public void shouldStartOnlyServerOnBothPorts() {
        // when
        instanceHolder.start(1, 2, -1, -1, null);

        // then
        verify(mockMockServerBuilder).withHTTPPort(1);
        verify(mockMockServerBuilder).withHTTPSPort(2);
        verifyNoMoreInteractions(mockProxyBuilder);
    }

    @Test
    public void shouldStartOnlyServerOnHttpPort() {
        // when
        ExampleInitializationClass.mockServerClient = null;
        instanceHolder.start(1, -1, -1, -1, new ExampleInitializationClass());

        // then
        verify(mockMockServerBuilder).withHTTPPort(1);
        verify(mockMockServerBuilder).withHTTPSPort(-1);
        verifyNoMoreInteractions(mockProxyBuilder);
        assertNotNull(ExampleInitializationClass.mockServerClient);
    }

    @Test
    public void shouldStartOnlyServerOnHttpsPort() {
        // when
        ExampleInitializationClass.mockServerClient = null;
        instanceHolder.start(-1, 1, -1, -1, new ExampleInitializationClass());

        // then
        verify(mockMockServerBuilder).withHTTPPort(-1);
        verify(mockMockServerBuilder).withHTTPSPort(1);
        verifyNoMoreInteractions(mockProxyBuilder);
        assertNull(ExampleInitializationClass.mockServerClient);
    }

    @Test
    public void shouldStartOnlyProxyOnBothPorts() {
        // when
        ExampleInitializationClass.mockServerClient = null;
        instanceHolder.start(-1, -1, 3, 4, new ExampleInitializationClass());

        // then
        verifyNoMoreInteractions(mockMockServerBuilder);
        verify(mockProxyBuilder).withHTTPPort(3);
        verify(mockProxyBuilder).withHTTPSPort(4);
        assertNull(ExampleInitializationClass.mockServerClient);
    }

    @Test
    public void shouldRunInitializationClass() {
        // given
        ExampleInitializationClass.mockServerClient = null;

        // when
        instanceHolder.start(1, 2, -1, -1, new ExampleInitializationClass());

        // then
        assertNotNull(ExampleInitializationClass.mockServerClient);
    }

    @Test
    public void shouldNotStartServerOrProxy() {
        // when
        ExampleInitializationClass.mockServerClient = null;
        instanceHolder.start(-1, -1, -1, -1, new ExampleInitializationClass());

        // then
        verifyNoMoreInteractions(mockMockServerBuilder);
        verifyNoMoreInteractions(mockProxyBuilder);
        assertNull(ExampleInitializationClass.mockServerClient);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldThrowExceptionIfServerRunning() {
        // given
        when(mockMockServer.isRunning()).thenReturn(true);

        // when
        instanceHolder.start(1, 2, 3, 4, null);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldThrowExceptionIfProxyRunning() {
        // given
        when(mockProxy.isRunning()).thenReturn(true);

        // when
        instanceHolder.start(1, 2, 3, 4, null);
    }

    @Test
    public void shouldStopMockServer() {
        // given
        when(mockMockServer.isRunning()).thenReturn(true);
        when(mockProxy.isRunning()).thenReturn(true);

        // when
        instanceHolder.stop();

        // then
        verify(mockMockServer).stop();
        verify(mockProxy).stop();
    }

    @Test
    public void shouldStopMockServerAndProxyRemotely() {
        // given
        InstanceHolder.mockServerClients.put(1, mockMockServerClient);
        InstanceHolder.proxyClients.put(2, mockProxyClient);

        // when
        instanceHolder.stop(1, 2);

        // then
        verify(mockMockServerClient).stop();
        verify(mockProxyClient).stop();

        // and - no new clients added
        assertThat(InstanceHolder.mockServerClients.size(), is(1));
        assertThat(InstanceHolder.proxyClients.size(), is(1));
    }

    @Test
    public void shouldStopMockServerOnlyRemotely() {
        // given
        InstanceHolder.mockServerClients.put(1, mockMockServerClient);
        InstanceHolder.proxyClients.put(2, mockProxyClient);

        // when
        instanceHolder.stop(1, -1);

        // then
        verify(mockMockServerClient).stop();
        verify(mockProxyClient, times(0)).stop();

        // and - no new clients added
        assertThat(InstanceHolder.mockServerClients.size(), is(1));
        assertThat(InstanceHolder.proxyClients.size(), is(1));
    }

    @Test
    public void shouldStopProxyOnlyRemotely() {
        // given
        InstanceHolder.mockServerClients.put(1, mockMockServerClient);
        InstanceHolder.proxyClients.put(2, mockProxyClient);

        // when
        instanceHolder.stop(-1, 2);

        // then
        verify(mockMockServerClient, times(0)).stop();
        verify(mockProxyClient).stop();

        // and - no new clients added
        assertThat(InstanceHolder.mockServerClients.size(), is(1));
        assertThat(InstanceHolder.proxyClients.size(), is(1));
    }

    @Test
    public void shouldStopMockServerAndProxyWhenNoClientExist() {
        // given
        InstanceHolder.mockServerClients.put(1, mockMockServerClient);
        InstanceHolder.proxyClients.put(2, mockProxyClient);

        // when
        instanceHolder.stop(1, 2);

        // then
        assertThat(InstanceHolder.mockServerClients.get(1), isA(MockServerClient.class));
        assertThat(InstanceHolder.proxyClients.get(2), isA(ProxyClient.class));
    }
}
