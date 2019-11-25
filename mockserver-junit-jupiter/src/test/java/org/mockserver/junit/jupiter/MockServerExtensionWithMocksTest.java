package org.mockserver.junit.jupiter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MockServerExtensionWithMocksTest {
    private MockServerExtension subject;
    @Mock
    private ClientAndServer mockClientAndServerFactory;

    @Mock
    private ParameterContext mockParameterContext;

    @Mock
    private Parameter mockParameter;

    @Mock
    private ExtensionContext mockExtensionContext;

    @Mock
    private AnnotatedElement mockAnnotatedElement;

    @BeforeEach
    void setup() {
        subject = new MockServerExtension(mockClientAndServerFactory);
    }

    @Test
    void identifiesSupportedParameter() throws Exception {
        doReturn(mockParameter).when(mockParameterContext).getParameter();
        doReturn(MockServerClient.class).when(mockParameter).getType();
        boolean result = subject.supportsParameter(mockParameterContext, null);
        assertThat(result, is(true));
    }

    @Test
    void doesNotsupportsRandomParameterType() throws Exception {
        doReturn(mockParameter).when(mockParameterContext).getParameter();
        doReturn(Object.class).when(mockParameter).getType();
        boolean result = subject.supportsParameter(mockParameterContext, null);
        assertThat(result, is(false));
    }

    @Test
    void beforeAdd_startsServerWithRandomPort() throws Exception {
        Optional<AnnotatedElement> element = Optional.of(mockAnnotatedElement);
        MockServerSettings mockServerSettings = mock(MockServerSettings.class);
        doReturn(Optional.empty()).when(mockExtensionContext).getParent();
        subject.beforeAll(mockExtensionContext);
        verify(mockClientAndServerFactory).startClientAndServer(anyList());
    }

    @Test
    void beforeAdd_startsServerWithSpecifiedPorts() throws Exception {
        List<Integer> ports = mockServerCreation();
        subject.beforeAll(mockExtensionContext);
        verify(mockClientAndServerFactory).startClientAndServer(ports);
    }

    @Test
    void beforeAdd_startsPerTestSuiteServer() throws Exception {
        List<Integer> ports = mockServerCreation();
        ClientAndServer mockServerClient = mock(ClientAndServer.class);
        doReturn(mockServerClient).when(mockClientAndServerFactory).startClientAndServer(anyList());
        subject.beforeAll(mockExtensionContext);
        verify(mockClientAndServerFactory).startClientAndServer(ports);
    }

    @Test
    void resolveParameter_returnsClientInstance() throws Exception {
        Optional<AnnotatedElement> element = Optional.of(mockAnnotatedElement);
        MockServerSettings mockServerSettings = mock(MockServerSettings.class);
        ClientAndServer mockServerClient = mock(ClientAndServer.class);
        doReturn(Optional.empty()).when(mockExtensionContext).getParent();
        doReturn(mockServerClient).when(mockClientAndServerFactory).startClientAndServer(anyList());
        subject.beforeAll(mockExtensionContext);
        Object result = subject.resolveParameter(null, null);
        assertThat(result, is(equalTo(mockServerClient)));
    }

    @Test
    void afterAll_stopsClientInstance() throws Exception {
        Optional<AnnotatedElement> element = Optional.of(mockAnnotatedElement);
        MockServerSettings mockServerSettings = mock(MockServerSettings.class);
        ClientAndServer mockServerClient = mock(ClientAndServer.class);
        doReturn(Optional.empty()).when(mockExtensionContext).getParent();
        doReturn(mockServerClient).when(mockClientAndServerFactory).startClientAndServer(anyList());
        doReturn(true).when(mockServerClient).isRunning();
        subject.beforeAll(mockExtensionContext);
        subject.afterAll(null);
        verify(mockServerClient).stop();
    }


    private List<Integer> mockServerCreation() {
        List<Integer> ports = Arrays.asList(5555, 4444);
        Optional<AnnotatedElement> element = Optional.of(mockAnnotatedElement);
        MockServerSettings mockServerSettings = mock(MockServerSettings.class);
        doReturn(ports.stream().mapToInt(Integer::intValue).toArray()).when(mockServerSettings).ports();
        doReturn(true).when(mockServerSettings).perTestSuite();
        doReturn(mockServerSettings).when(mockAnnotatedElement).getDeclaredAnnotation(MockServerSettings.class);
        doReturn(element).when(mockExtensionContext).getElement();
        doReturn(Optional.empty()).when(mockExtensionContext).getParent();
        return ports;
    }
}