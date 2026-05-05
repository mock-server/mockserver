package org.mockserver.maven;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author jamesdbloom
 */
public class MockServerStartMojoTest {

    @Mock
    private InstanceHolder mockInstanceHolder;
    @InjectMocks
    private MockServerStartMojo mockServerStartMojo;

    @Before
    public void setupMocks() {
        openMocks(this);
        MockServerAbstractMojo.instanceHolder = mockInstanceHolder;
    }

    @Test
    public void shouldStartMockServer() {
        // given
        mockServerStartMojo.serverPort = "1,2";
        mockServerStartMojo.logLevel = "WARN";
        mockServerStartMojo.initializationClass = "org.mockserver.maven.ExampleInitializationClass";
        mockServerStartMojo.initializationJson = "org/mockserver/maven/initializerJson.json";

        // when
        mockServerStartMojo.execute();

        // then
        verify(mockInstanceHolder).start(eq(new Integer[]{1, 2}), eq(-1), eq(""), eq("WARN"), any(ExampleInitializationClass.class), eq(""));
    }

    @Test
    public void shouldStartMockServerWithRemote() {
        // given
        mockServerStartMojo.serverPort = "1,2";
        mockServerStartMojo.proxyRemotePort = 3;
        mockServerStartMojo.proxyRemoteHost = "remoteHost";
        mockServerStartMojo.logLevel = "WARN";
        mockServerStartMojo.initializationClass = "org.mockserver.maven.ExampleInitializationClass";
        mockServerStartMojo.initializationJson = "org/mockserver/maven/initializerJson.json";

        // when
        mockServerStartMojo.execute();

        // then
        verify(mockInstanceHolder).start(eq(new Integer[]{1, 2}), eq(3), eq("remoteHost"), eq("WARN"), any(ExampleInitializationClass.class), eq(""));
    }

    @Test
    public void shouldSkipStartingMockServer() {
        // given
        mockServerStartMojo.skip = true;

        // when
        mockServerStartMojo.execute();

        // then
        verifyNoMoreInteractions(mockInstanceHolder);
    }
}
