package org.mockserver.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.repository.RepositorySystem;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.MockServerClient;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author jamesdbloom
 */
public class MockServerRunForkedMojoTest {

    private final String jarWithDependenciesPath = "";
    private String javaBinaryPath;
    @Mock
    private RepositorySystem mockRepositorySystem;
    @Mock
    private ProcessBuildFactory mockProcessBuildFactory;
    @Mock
    private MockServerClient mockServerClient;
    @InjectMocks
    private MockServerRunForkedMojo mockServerRunForkedMojo;
    private ProcessBuilder processBuilder;
    @Mock
    private Artifact mockArtifact;

    @Before
    public void setupMocks() {
        processBuilder = new ProcessBuilder("echo", "");
        mockServerRunForkedMojo = new MockServerRunForkedMojo();
        javaBinaryPath = mockServerRunForkedMojo.getJavaBin();

        openMocks(this);

        when(mockRepositorySystem.createArtifactWithClassifier("org.mock-server", "mockserver-netty-no-dependencies", mockServerRunForkedMojo.getVersion(), "jar", "")).thenReturn(mockArtifact);
        when(mockServerClient.hasStarted(anyInt(), anyLong(), any(TimeUnit.class))).thenReturn(true);
    }

    @Test
    public void shouldRunMockServerForkedLocalPortSpecified() {
        // given
        mockServerRunForkedMojo.serverPort = "1,2";
        mockServerRunForkedMojo.logLevel = "LEVEL";
        mockServerRunForkedMojo.pipeLogToConsole = true;
        mockServerRunForkedMojo.jvmOptions = "-Dfoo=bar";
        when(mockProcessBuildFactory.create(anyList())).thenReturn(processBuilder);


        // when
        mockServerRunForkedMojo.execute();

        // then
        verify(mockRepositorySystem).createArtifactWithClassifier("org.mock-server", "mockserver-netty-no-dependencies", mockServerRunForkedMojo.getVersion(), "jar", null);
        verify(mockProcessBuildFactory).create(Arrays.asList(
                javaBinaryPath,
                "-Dfile.encoding=UTF-8",
                "-Dfoo=bar",
                "-cp", jarWithDependenciesPath, "org.mockserver.cli.Main",
                "-serverPort", "1,2",
                "-logLevel", "LEVEL"
        ));
        verify(mockServerClient).hasStarted(anyInt(), anyLong(), any(TimeUnit.class));
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectError());
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectOutput());
    }

    @Test
    public void shouldRunMockServerForkedPortForwarding() {
        // given
        mockServerRunForkedMojo.serverPort = "1,2";
        mockServerRunForkedMojo.proxyRemotePort = 3;
        mockServerRunForkedMojo.proxyRemoteHost = "remoteHost";
        mockServerRunForkedMojo.logLevel = "LEVEL";
        mockServerRunForkedMojo.pipeLogToConsole = true;
        mockServerRunForkedMojo.jvmOptions = "-Dfoo=bar";
        when(mockProcessBuildFactory.create(anyList())).thenReturn(processBuilder);


        // when
        mockServerRunForkedMojo.execute();

        // then
        verify(mockRepositorySystem).createArtifactWithClassifier("org.mock-server", "mockserver-netty-no-dependencies", mockServerRunForkedMojo.getVersion(), "jar", null);
        verify(mockProcessBuildFactory).create(Arrays.asList(
                javaBinaryPath,
                "-Dfile.encoding=UTF-8",
                "-Dfoo=bar",
                "-cp", jarWithDependenciesPath, "org.mockserver.cli.Main",
                "-serverPort", "1,2",
                "-proxyRemotePort", "3",
                "-proxyRemoteHost", "remoteHost",
                "-logLevel", "LEVEL"
        ));
        verify(mockServerClient).hasStarted(anyInt(), anyLong(), any(TimeUnit.class));
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectError());
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectOutput());
    }

    @Test
    public void shouldRunMockServerWithInitializer() {
        // given
        ExampleInitializationClass.mockServerClient = null;
        mockServerRunForkedMojo.serverPort = "1,2";
        mockServerRunForkedMojo.pipeLogToConsole = true;
        mockServerRunForkedMojo.initializationClass = "org.mockserver.maven.ExampleInitializationClass";
        String classLocation = "org/mockserver/maven/ExampleInitializationClass.class";
        mockServerRunForkedMojo.compileClasspath = Collections.singletonList(ExampleInitializationClass.class.getClassLoader().getResource(classLocation).getFile().replaceAll(classLocation, ""));
        mockServerRunForkedMojo.testClasspath = Collections.emptyList();
        mockServerRunForkedMojo.jvmOptions = "-Dfoo=bar";
        when(mockProcessBuildFactory.create(anyList())).thenReturn(processBuilder);

        // when
        mockServerRunForkedMojo.execute();

        // then
        verify(mockProcessBuildFactory).create(Arrays.asList(
                javaBinaryPath,
                "-Dfile.encoding=UTF-8",
                "-Dfoo=bar",
                "-cp", jarWithDependenciesPath, "org.mockserver.cli.Main",
                "-serverPort", "1,2",
                "-logLevel", "INFO"
        ));
        verify(mockServerClient).hasStarted(anyInt(), anyLong(), any(TimeUnit.class));
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectError());
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectOutput());
        assertNotNull(ExampleInitializationClass.mockServerClient);
    }

    @Test
    public void shouldHandleProcessException() {
        // given
        when(mockProcessBuildFactory.create(anyList())).thenReturn(new ProcessBuilder("TEST FAIL"));

        // when
        try {
            mockServerRunForkedMojo.execute();
        } catch (Throwable t) {
            // then
            fail();
        }
    }

    @Test
    public void shouldRunMockServerForkedAndNotPipeToConsole() {
        // given
        mockServerRunForkedMojo.serverPort = "1,2";
        mockServerRunForkedMojo.pipeLogToConsole = false;
        when(mockProcessBuildFactory.create(anyList())).thenReturn(processBuilder);

        // when
        mockServerRunForkedMojo.execute();

        // then
        verify(mockRepositorySystem).createArtifactWithClassifier("org.mock-server", "mockserver-netty-no-dependencies", mockServerRunForkedMojo.getVersion(), "jar", null);
        verify(mockServerClient).hasStarted(anyInt(), anyLong(), any(TimeUnit.class));
        assertFalse(processBuilder.redirectErrorStream());
    }

    @Test
    public void shouldHandleIncorrectInitializationClassName() {
        // given
        ExampleInitializationClass.mockServerClient = null;
        mockServerRunForkedMojo.serverPort = "1,2";
        mockServerRunForkedMojo.pipeLogToConsole = true;
        mockServerRunForkedMojo.initializationClass = "org.mockserver.maven.InvalidClassName";
        when(mockProcessBuildFactory.create(anyList())).thenReturn(processBuilder);

        // when
        mockServerRunForkedMojo.execute();

        verify(mockServerClient).hasStarted(anyInt(), anyLong(), any(TimeUnit.class));
        assertNull(ExampleInitializationClass.mockServerClient);
    }

    @Test
    public void shouldSkipStoppingMockServer() {
        // given
        mockServerRunForkedMojo.skip = true;

        // when
        mockServerRunForkedMojo.execute();

        // then
        verify(mockProcessBuildFactory, times(0)).create(anyList());
    }
}
