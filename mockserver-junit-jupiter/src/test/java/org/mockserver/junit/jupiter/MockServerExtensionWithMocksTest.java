package org.mockserver.junit.jupiter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.mockito.Mock;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.test.TestLoggerExtension;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

@Disabled
@ExtendWith({
    MockServerExtension.class,
    TestLoggerExtension.class,
})
class MockServerExtensionWithMocksTest {
    private MockServerExtension mockServerExtension;

    @Mock
    private ClientAndServer clientAndServer;

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
        initMocks(this);
        mockServerExtension = new MockServerExtension(clientAndServer);
    }

    @Test
    void identifiesSupportedParameter() {
        // given
        when(mockParameterContext.getParameter()).thenReturn(mockParameter);
        doReturn(MockServerClient.class).when(mockParameter).getType();

        // when
        boolean result = mockServerExtension.supportsParameter(mockParameterContext, null);

        // then
        assertThat(result, is(true));
    }

    @Test
    void doesNotSupportRandomParameterType() {
        // given
        when(mockParameterContext.getParameter()).thenReturn(mockParameter);
        doReturn(Object.class).when(mockParameter).getType();

        // when
        boolean result = mockServerExtension.supportsParameter(mockParameterContext, null);

        // then
        assertThat(result, is(false));
    }

    @Test
    void beforeAdd_startsServerWithRandomPort() {
        // given
        when(mockExtensionContext.getParent()).thenReturn(Optional.empty());

        // when
        mockServerExtension.beforeAll(mockExtensionContext);

        // then
        verify(clientAndServer).startClientAndServer(anyList());
    }

    @Test
    void beforeAdd_startsServerWithSpecifiedPorts() {
        // given
        List<Integer> ports = mockServerCreation();

        // when
        mockServerExtension.beforeAll(mockExtensionContext);

        // then
        verify(clientAndServer).startClientAndServer(ports);
    }

    @Test
    void beforeAdd_startsPerTestSuiteServer() {
        // given
        List<Integer> ports = mockServerCreation();
        ClientAndServer mockServerClient = mock(ClientAndServer.class);
        when(clientAndServer.startClientAndServer(anyList())).thenReturn(mockServerClient);

        // when
        mockServerExtension.beforeAll(mockExtensionContext);

        // then
        verify(clientAndServer).startClientAndServer(ports);
    }

    @Test
    void resolveParameter_returnsClientInstance() {
        // given
        ClientAndServer mockServerClient = mock(ClientAndServer.class);
        when(mockExtensionContext.getParent()).thenReturn(Optional.empty());
        when(clientAndServer.startClientAndServer(anyList())).thenReturn(mockServerClient);

        // when
        mockServerExtension.beforeAll(mockExtensionContext);

        // then
        Object result = mockServerExtension.resolveParameter(null, null);
        assertThat(result, is(equalTo(mockServerClient)));
    }

    @Test
    void afterAll_stopsClientInstance() {
        // given
        ClientAndServer mockServerClient = mock(ClientAndServer.class);
        when(mockExtensionContext.getParent()).thenReturn(Optional.empty());
        when(clientAndServer.startClientAndServer(anyList())).thenReturn(mockServerClient);
        when(mockServerClient.isRunning()).thenReturn(true);

        // when
        mockServerExtension.beforeAll(mockExtensionContext);
        mockServerExtension.afterAll(null);

        // then
        verify(mockServerClient).stop();
    }


    private List<Integer> mockServerCreation() {
        List<Integer> ports = Arrays.asList(5555, 4444);
        Optional<AnnotatedElement> element = Optional.of(mockAnnotatedElement);
        MockServerSettings mockServerSettings = mock(MockServerSettings.class);
        when(mockServerSettings.ports()).thenReturn(ports.stream().mapToInt(Integer::intValue).toArray());
        when(mockServerSettings.perTestSuite()).thenReturn(true);
        when(mockAnnotatedElement.getDeclaredAnnotation(MockServerSettings.class)).thenReturn(mockServerSettings);
        when(mockExtensionContext.getElement()).thenReturn(element);
        when(mockExtensionContext.getParent()).thenReturn(Optional.empty());
        return ports;
    }
}