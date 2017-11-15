package org.mockserver.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.shared.artifact.resolve.ArtifactResolver;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class MockServerRunForkedMojoTest {

    private final String level = "LEVEL";
    private final String repositoryBaseDir = "repositoryBaseDir";
    private final String repositoryPathOfArtifact = "repositoryPathOfArtifact";
    private final String jarWithDependenciesPath = repositoryBaseDir + "/" + repositoryPathOfArtifact;
    private String javaBinaryPath;
    @Mock
    private RepositorySystem mockRepositorySystem;
    @Mock
    private MavenSession mockSession;
    @Mock
    private ArtifactResolver mockArtifactResolver;
    @Mock
    private ArtifactRepository mockArtifactRepository;

    @Mock
    private ProcessBuildFactory mockProcessBuildFactory;
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

        initMocks(this);

        when(mockRepositorySystem.createArtifactWithClassifier("org.mock-server", "mockserver-netty", mockServerRunForkedMojo.getVersion(), "jar", "jar-with-dependencies")).thenReturn(mockArtifact);
        when(mockSession.getLocalRepository()).thenReturn(mockArtifactRepository);
        when(mockArtifactRepository.getBasedir()).thenReturn(repositoryBaseDir);
        when(mockArtifactRepository.pathOf(mockArtifact)).thenReturn(repositoryPathOfArtifact);
        mockServerRunForkedMojo.logLevel = level;
    }

    @Test
    public void shouldRunMockServerAndProxyForkedBothPortsSpecified() throws MojoExecutionException, ExecutionException, InterruptedException {
        // given
        mockServerRunForkedMojo.serverPort = "1,2";
        mockServerRunForkedMojo.proxyPort = 3;
        mockServerRunForkedMojo.pipeLogToConsole = true;
        when(mockProcessBuildFactory.create(anyListOf(String.class))).thenReturn(processBuilder);


        // when
        mockServerRunForkedMojo.execute();

        // then
        verify(mockRepositorySystem).createArtifactWithClassifier("org.mock-server", "mockserver-netty", mockServerRunForkedMojo.getVersion(), "jar", "jar-with-dependencies");
        verify(mockProcessBuildFactory).create(Arrays.asList(
                javaBinaryPath,
                "-Dfile.encoding=UTF-8",
                "-Dmockserver.logLevel=" + level,
                "-cp", jarWithDependenciesPath, "org.mockserver.cli.Main", "-serverPort", "1,2", "-proxyPort", "3"
        ));
        assertEquals(true, processBuilder.redirectErrorStream());
    }

    @Test
    public void shouldRunMockServerAndProxyForkedOnlyPort() throws MojoExecutionException, ExecutionException, InterruptedException {
        // given
        mockServerRunForkedMojo.serverPort = "1,2";
        mockServerRunForkedMojo.proxyPort = 3;
        mockServerRunForkedMojo.pipeLogToConsole = true;
        when(mockProcessBuildFactory.create(anyListOf(String.class))).thenReturn(processBuilder);


        // when
        mockServerRunForkedMojo.execute();

        // then
        verify(mockRepositorySystem).createArtifactWithClassifier("org.mock-server", "mockserver-netty", mockServerRunForkedMojo.getVersion(), "jar", "jar-with-dependencies");
        verify(mockProcessBuildFactory).create(Arrays.asList(
                javaBinaryPath,
                "-Dfile.encoding=UTF-8",
                "-Dmockserver.logLevel=" + level,
                "-cp", jarWithDependenciesPath, "org.mockserver.cli.Main", "-serverPort", "1,2", "-proxyPort", "3"
        ));
        assertEquals(true, processBuilder.redirectErrorStream());
    }

    @Test
    public void shouldRunMockServerOnlyForkedBothPortsSpecified() throws MojoExecutionException, ExecutionException, InterruptedException {
        // given
        ExampleInitializationClass.mockServerClient = null;
        mockServerRunForkedMojo.serverPort = "1,2";
        mockServerRunForkedMojo.pipeLogToConsole = true;
        mockServerRunForkedMojo.initializationClass = "org.mockserver.maven.ExampleInitializationClass";
        String classLocation = "org/mockserver/maven/ExampleInitializationClass.class";
        mockServerRunForkedMojo.compileClasspath = Collections.singletonList(ExampleInitializationClass.class.getClassLoader().getResource(classLocation).getFile().replaceAll(classLocation, ""));
        mockServerRunForkedMojo.testClasspath = Collections.emptyList();
        when(mockProcessBuildFactory.create(anyListOf(String.class))).thenReturn(processBuilder);


        // when
        mockServerRunForkedMojo.execute();

        // then
        verify(mockProcessBuildFactory).create(Arrays.asList(
                javaBinaryPath,
                "-Dfile.encoding=UTF-8",
                "-Dmockserver.logLevel=" + level,
                "-cp", jarWithDependenciesPath, "org.mockserver.cli.Main", "-serverPort", "1,2"
        ));
        assertEquals(true, processBuilder.redirectErrorStream());
        assertNotNull(ExampleInitializationClass.mockServerClient);
    }

    @Test
    public void shouldRunMockServerOnlyForkedOnlyPort() throws MojoExecutionException, ExecutionException, InterruptedException {
        // given
        ExampleInitializationClass.mockServerClient = null;
        mockServerRunForkedMojo.serverPort = "1,2";
        mockServerRunForkedMojo.pipeLogToConsole = true;
        mockServerRunForkedMojo.initializationClass = "org.mockserver.maven.ExampleInitializationClass";
        String classLocation = "org/mockserver/maven/ExampleInitializationClass.class";
        mockServerRunForkedMojo.compileClasspath = Collections.singletonList(ExampleInitializationClass.class.getClassLoader().getResource(classLocation).getFile().replaceAll(classLocation, ""));
        mockServerRunForkedMojo.testClasspath = Collections.emptyList();
        when(mockProcessBuildFactory.create(anyListOf(String.class))).thenReturn(processBuilder);


        // when
        mockServerRunForkedMojo.execute();

        // then
        verify(mockRepositorySystem).createArtifactWithClassifier("org.mock-server", "mockserver-netty", mockServerRunForkedMojo.getVersion(), "jar", "jar-with-dependencies");
        verify(mockProcessBuildFactory).create(Arrays.asList(
                javaBinaryPath,
                "-Dfile.encoding=UTF-8",
                "-Dmockserver.logLevel=" + level,
                "-cp", jarWithDependenciesPath, "org.mockserver.cli.Main", "-serverPort", "1,2"
        ));
        assertEquals(true, processBuilder.redirectErrorStream());
        assertNotNull(ExampleInitializationClass.mockServerClient);
    }

    @Test
    public void shouldRunProxyOnlyForkedBothPortsSpecified() throws MojoExecutionException, ExecutionException, InterruptedException {
        // given
        ExampleInitializationClass.mockServerClient = null;
        mockServerRunForkedMojo.proxyPort = 1;
        mockServerRunForkedMojo.pipeLogToConsole = true;
        mockServerRunForkedMojo.initializationClass = "org.mockserver.maven.ExampleInitializationClass";
        when(mockProcessBuildFactory.create(anyListOf(String.class))).thenReturn(processBuilder);


        // when
        mockServerRunForkedMojo.execute();

        // then
        verify(mockRepositorySystem).createArtifactWithClassifier("org.mock-server", "mockserver-netty", mockServerRunForkedMojo.getVersion(), "jar", "jar-with-dependencies");
        verify(mockProcessBuildFactory).create(Arrays.asList(
                javaBinaryPath,
                "-Dfile.encoding=UTF-8",
                "-Dmockserver.logLevel=" + level,
                "-cp", jarWithDependenciesPath, "org.mockserver.cli.Main", "-proxyPort", "1"
        ));
        assertEquals(true, processBuilder.redirectErrorStream());
        assertNull(ExampleInitializationClass.mockServerClient);
    }

    @Test
    public void shouldRunProxyOnlyForkedOnlyPort() throws MojoExecutionException, ExecutionException, InterruptedException {
        // given
        mockServerRunForkedMojo.proxyPort = 1;
        mockServerRunForkedMojo.pipeLogToConsole = true;
        when(mockProcessBuildFactory.create(anyListOf(String.class))).thenReturn(processBuilder);


        // when
        mockServerRunForkedMojo.execute();

        // then
        verify(mockRepositorySystem).createArtifactWithClassifier("org.mock-server", "mockserver-netty", mockServerRunForkedMojo.getVersion(), "jar", "jar-with-dependencies");
        verify(mockProcessBuildFactory).create(Arrays.asList(
                javaBinaryPath,
                "-Dfile.encoding=UTF-8",
                "-Dmockserver.logLevel=" + level,
                "-cp", jarWithDependenciesPath, "org.mockserver.cli.Main", "-proxyPort", "1"
        ));
        assertEquals(true, processBuilder.redirectErrorStream());
    }

    @Test
    public void shouldHandleProcessException() throws IOException {
        // given
        when(mockProcessBuildFactory.create(anyListOf(String.class))).thenReturn(new ProcessBuilder("TEST FAIL"));

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
        when(mockProcessBuildFactory.create(anyListOf(String.class))).thenReturn(processBuilder);

        // when
        mockServerRunForkedMojo.execute();

        // then
        verify(mockRepositorySystem).createArtifactWithClassifier("org.mock-server", "mockserver-netty", mockServerRunForkedMojo.getVersion(), "jar", "jar-with-dependencies");
        assertEquals(false, processBuilder.redirectErrorStream());
    }

    @Test
    public void shouldHandleIncorrectInitializationClassName() throws MojoExecutionException {
        // given
        ExampleInitializationClass.mockServerClient = null;
        mockServerRunForkedMojo.serverPort = "1,2";
        mockServerRunForkedMojo.pipeLogToConsole = true;
        mockServerRunForkedMojo.initializationClass = "org.mockserver.maven.InvalidClassName";
        when(mockProcessBuildFactory.create(anyListOf(String.class))).thenReturn(processBuilder);

        // when
        mockServerRunForkedMojo.execute();

        assertNull(ExampleInitializationClass.mockServerClient);
    }

    @Test
    public void shouldSkipStoppingMockServer() throws MojoExecutionException {
        // given
        mockServerRunForkedMojo.skip = true;

        // when
        mockServerRunForkedMojo.execute();

        // then
        verify(mockProcessBuildFactory, times(0)).create(anyListOf(String.class));
    }
}
