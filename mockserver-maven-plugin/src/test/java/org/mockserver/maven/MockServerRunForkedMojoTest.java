package org.mockserver.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.repository.RepositorySystem;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class MockServerRunForkedMojoTest {

    public final String level = "LEVEL";
    public final int stopPort = 3;
    private final String jarWithDependenciesPath = "/foo";
    private final String javaBinaryPath = "java";
    @Mock
    protected RepositorySystem mockRepositorySystem;
    @Mock
    protected ArtifactResolver mockArtifactResolver;
    @Mock
    private EmbeddedJettyHolder mockEmbeddedJettyHolder;
    @InjectMocks
    @Spy
    private MockServerRunForkedMojo mockServerRunForkedMojo;
    private ProcessBuilder processBuilder;
    @Mock
    private Artifact mockArtifact;

    @Before
    public void setupMocks() {
        processBuilder = new ProcessBuilder("echo", "");
        mockServerRunForkedMojo = new MockServerRunForkedMojo();
        initMocks(this);

        when(mockServerRunForkedMojo.getJavaBin()).thenReturn(javaBinaryPath);
        when(mockRepositorySystem.createArtifactWithClassifier("org.mock-server", "mockserver-jetty", "2.3-SNAPSHOT", "jar", "jar-with-dependencies")).thenReturn(mockArtifact);
        when(mockArtifact.getFile()).thenReturn(new File(jarWithDependenciesPath));
        mockServerRunForkedMojo.logLevel = level;
        mockServerRunForkedMojo.serverStopPort = stopPort;
    }

    @Test
    public void shouldRunMockServerAndProxyForkedBothPortsSpecified() throws MojoExecutionException, ExecutionException, InterruptedException {
        // given
        mockServerRunForkedMojo.serverPort = 1;
        mockServerRunForkedMojo.serverSecurePort = 2;
        mockServerRunForkedMojo.proxyPort = 3;
        mockServerRunForkedMojo.proxySecurePort = 4;
        mockServerRunForkedMojo.pipeLogToConsole = true;
        when(mockServerRunForkedMojo.newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dmockserver.logLevel=" + level,
                "-Dmockserver.serverStopPort=" + stopPort,
                "-jar", jarWithDependenciesPath, "-serverPort", "1", "-serverSecurePort", "2", "-proxyPort", "3", "-proxySecurePort", "4"
        ))).thenReturn(processBuilder);


        // when
        mockServerRunForkedMojo.execute();

        // then
        verify(mockServerRunForkedMojo).newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dmockserver.logLevel=" + level,
                "-Dmockserver.serverStopPort=" + stopPort,
                "-jar", jarWithDependenciesPath, "-serverPort", "1", "-serverSecurePort", "2", "-proxyPort", "3", "-proxySecurePort", "4"
        ));
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectInput());
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectOutput());
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectError());
    }

    @Test
    public void shouldRunMockServerAndProxyForkedOnlyNonSecurePort() throws MojoExecutionException, ExecutionException, InterruptedException {
        // given
        mockServerRunForkedMojo.serverPort = 1;
        mockServerRunForkedMojo.serverSecurePort = -1;
        mockServerRunForkedMojo.proxyPort = 3;
        mockServerRunForkedMojo.proxySecurePort = -1;
        mockServerRunForkedMojo.pipeLogToConsole = true;
        when(mockServerRunForkedMojo.newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dmockserver.logLevel=" + level,
                "-Dmockserver.serverStopPort=" + stopPort,
                "-jar", jarWithDependenciesPath, "-serverPort", "1", "-proxyPort", "3"
        ))).thenReturn(processBuilder);


        // when
        mockServerRunForkedMojo.execute();

        // then
        verify(mockServerRunForkedMojo).newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dmockserver.logLevel=" + level,
                "-Dmockserver.serverStopPort=" + stopPort,
                "-jar", jarWithDependenciesPath, "-serverPort", "1", "-proxyPort", "3"
        ));
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectInput());
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectOutput());
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectError());
    }

    @Test
    public void shouldRunMockServerAndProxyForkedOnlySecurePort() throws MojoExecutionException, ExecutionException, InterruptedException {
        // given
        mockServerRunForkedMojo.serverPort = -1;
        mockServerRunForkedMojo.serverSecurePort = 2;
        mockServerRunForkedMojo.proxyPort = -1;
        mockServerRunForkedMojo.proxySecurePort = 4;
        mockServerRunForkedMojo.pipeLogToConsole = true;
        when(mockServerRunForkedMojo.newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dmockserver.logLevel=" + level,
                "-Dmockserver.serverStopPort=" + stopPort,
                "-jar", jarWithDependenciesPath, "-serverSecurePort", "2", "-proxySecurePort", "4"
        ))).thenReturn(processBuilder);


        // when
        mockServerRunForkedMojo.execute();

        // then
        verify(mockServerRunForkedMojo).newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dmockserver.logLevel=" + level,
                "-Dmockserver.serverStopPort=" + stopPort,
                "-jar", jarWithDependenciesPath, "-serverSecurePort", "2", "-proxySecurePort", "4"
        ));
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectInput());
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectOutput());
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectError());
    }

    @Test
    public void shouldRunMockServerOnlyForkedBothPortsSpecified() throws MojoExecutionException, ExecutionException, InterruptedException {
        // given
        mockServerRunForkedMojo.serverPort = 1;
        mockServerRunForkedMojo.serverSecurePort = 2;
        mockServerRunForkedMojo.pipeLogToConsole = true;
        when(mockServerRunForkedMojo.newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dmockserver.logLevel=" + level,
                "-Dmockserver.serverStopPort=" + stopPort,
                "-jar", jarWithDependenciesPath, "-serverPort", "1", "-serverSecurePort", "2"
        ))).thenReturn(processBuilder);


        // when
        mockServerRunForkedMojo.execute();

        // then
        verify(mockServerRunForkedMojo).newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dmockserver.logLevel=" + level,
                "-Dmockserver.serverStopPort=" + stopPort,
                "-jar", jarWithDependenciesPath, "-serverPort", "1", "-serverSecurePort", "2"
        ));
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectInput());
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectOutput());
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectError());
    }

    @Test
    public void shouldRunMockServerOnlyForkedOnlyNonSecurePort() throws MojoExecutionException, ExecutionException, InterruptedException {
        // given
        mockServerRunForkedMojo.serverPort = 1;
        mockServerRunForkedMojo.serverSecurePort = -1;
        mockServerRunForkedMojo.pipeLogToConsole = true;
        when(mockServerRunForkedMojo.newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dmockserver.logLevel=" + level,
                "-Dmockserver.serverStopPort=" + stopPort,
                "-jar", jarWithDependenciesPath, "-serverPort", "1"
        ))).thenReturn(processBuilder);


        // when
        mockServerRunForkedMojo.execute();

        // then
        verify(mockServerRunForkedMojo).newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dmockserver.logLevel=" + level,
                "-Dmockserver.serverStopPort=" + stopPort,
                "-jar", jarWithDependenciesPath, "-serverPort", "1"
        ));
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectInput());
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectOutput());
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectError());
    }

    @Test
    public void shouldRunMockServerOnlyForkedOnlySecurePort() throws MojoExecutionException, ExecutionException, InterruptedException {
        // given
        mockServerRunForkedMojo.serverPort = -1;
        mockServerRunForkedMojo.serverSecurePort = 2;
        mockServerRunForkedMojo.pipeLogToConsole = true;
        when(mockServerRunForkedMojo.newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dmockserver.logLevel=" + level,
                "-Dmockserver.serverStopPort=" + stopPort,
                "-jar", jarWithDependenciesPath, "-serverSecurePort", "2"
        ))).thenReturn(processBuilder);


        // when
        mockServerRunForkedMojo.execute();

        // then
        verify(mockServerRunForkedMojo).newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dmockserver.logLevel=" + level,
                "-Dmockserver.serverStopPort=" + stopPort,
                "-jar", jarWithDependenciesPath, "-serverSecurePort", "2"
        ));
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectInput());
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectOutput());
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectError());
    }

    @Test
    public void shouldRunProxyOnlyForkedBothPortsSpecified() throws MojoExecutionException, ExecutionException, InterruptedException {
        // given
        mockServerRunForkedMojo.proxyPort = 1;
        mockServerRunForkedMojo.proxySecurePort = 2;
        mockServerRunForkedMojo.pipeLogToConsole = true;
        when(mockServerRunForkedMojo.newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dmockserver.logLevel=" + level,
                "-Dmockserver.serverStopPort=" + stopPort,
                "-jar", jarWithDependenciesPath, "-proxyPort", "1", "-proxySecurePort", "2"
        ))).thenReturn(processBuilder);


        // when
        mockServerRunForkedMojo.execute();

        // then
        verify(mockServerRunForkedMojo).newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dmockserver.logLevel=" + level,
                "-Dmockserver.serverStopPort=" + stopPort,
                "-jar", jarWithDependenciesPath, "-proxyPort", "1", "-proxySecurePort", "2"
        ));
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectInput());
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectOutput());
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectError());
    }

    @Test
    public void shouldRunProxyOnlyForkedOnlyNonSecurePort() throws MojoExecutionException, ExecutionException, InterruptedException {
        // given
        mockServerRunForkedMojo.proxyPort = 1;
        mockServerRunForkedMojo.proxySecurePort = -1;
        mockServerRunForkedMojo.pipeLogToConsole = true;
        when(mockServerRunForkedMojo.newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dmockserver.logLevel=" + level,
                "-Dmockserver.serverStopPort=" + stopPort,
                "-jar", jarWithDependenciesPath, "-proxyPort", "1"
        ))).thenReturn(processBuilder);


        // when
        mockServerRunForkedMojo.execute();

        // then
        verify(mockServerRunForkedMojo).newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dmockserver.logLevel=" + level,
                "-Dmockserver.serverStopPort=" + stopPort,
                "-jar", jarWithDependenciesPath, "-proxyPort", "1"
        ));
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectInput());
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectOutput());
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectError());
    }

    @Test
    public void shouldRunProxyOnlyForkedOnlySecurePort() throws MojoExecutionException, ExecutionException, InterruptedException {
        // given
        mockServerRunForkedMojo.proxyPort = -1;
        mockServerRunForkedMojo.proxySecurePort = 2;
        mockServerRunForkedMojo.pipeLogToConsole = true;
        when(mockServerRunForkedMojo.newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dmockserver.logLevel=" + level,
                "-Dmockserver.serverStopPort=" + stopPort,
                "-jar", jarWithDependenciesPath, "-proxySecurePort", "2"
        ))).thenReturn(processBuilder);


        // when
        mockServerRunForkedMojo.execute();

        // then
        verify(mockServerRunForkedMojo).newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dmockserver.logLevel=" + level,
                "-Dmockserver.serverStopPort=" + stopPort,
                "-jar", jarWithDependenciesPath, "-proxySecurePort", "2"
        ));
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectInput());
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectOutput());
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectError());
    }

    @Test
    public void shouldHandleProcessException() throws IOException {
        // given
        when(mockServerRunForkedMojo.newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dmockserver.logLevel=" + level,
                "-Dmockserver.serverStopPort=" + stopPort,
                "-jar", jarWithDependenciesPath, "-serverPort", "0", "-serverSecurePort", "0"
        ))).thenReturn(new ProcessBuilder("fail"));

        // when
        try {
            mockServerRunForkedMojo.execute();
        } catch (Throwable t) {
            // then
            fail();
        }
    }

    @Test
    public void shouldRunMockServerForkedAndNotPipeToConsole() throws MojoExecutionException, ExecutionException, InterruptedException {
        // given
        mockServerRunForkedMojo.pipeLogToConsole = false;
        when(mockServerRunForkedMojo.newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dmockserver.logLevel=" + level,
                "-Dmockserver.serverStopPort=" + stopPort,
                "-jar", jarWithDependenciesPath, "-serverPort", "0", "-serverSecurePort", "0"
        ))).thenReturn(processBuilder);

        // when
        mockServerRunForkedMojo.execute();

        // then
        assertEquals(ProcessBuilder.Redirect.PIPE, processBuilder.redirectInput());
        assertEquals(ProcessBuilder.Redirect.PIPE, processBuilder.redirectOutput());
        assertEquals(ProcessBuilder.Redirect.PIPE, processBuilder.redirectError());
    }

    @Test
    public void shouldSkipStoppingMockServer() throws MojoExecutionException {
        // given
        mockServerRunForkedMojo.skip = true;

        // when
        mockServerRunForkedMojo.execute();

        // then
        verify(mockServerRunForkedMojo, times(0)).newProcessBuilder(anyListOf(String.class));
    }
}
