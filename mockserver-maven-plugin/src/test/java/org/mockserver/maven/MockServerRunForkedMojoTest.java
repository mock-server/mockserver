package org.mockserver.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.repository.RepositorySystem;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
@Ignore("@Spy is unreliable and fails the build randomly about 50% of the time")
public class MockServerRunForkedMojoTest {

    public final String level = "LEVEL";
    private final String jarWithDependenciesPath = new File("/foo").getAbsolutePath();
    private final String javaBinaryPath = "java";
    @Mock
    protected RepositorySystem mockRepositorySystem;
    @Mock
    protected ArtifactResolver mockArtifactResolver;
    @Mock
    private InstanceHolder mockEmbeddedJettyHolder;
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
        when(mockRepositorySystem.createArtifactWithClassifier("org.mock-server", "mockserver-netty", mockServerRunForkedMojo.getVersion(), "jar", "jar-with-dependencies")).thenReturn(mockArtifact);
        when(mockArtifact.getFile()).thenReturn(new File(jarWithDependenciesPath));
        mockServerRunForkedMojo.logLevel = level;
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
                "-Dfile.encoding=UTF-8",
                "-Dmockserver.logLevel=" + level,
                "-jar", jarWithDependenciesPath, "-serverPort", "1", "-serverSecurePort", "2", "-proxyPort", "3", "-proxySecurePort", "4"
        ))).thenReturn(processBuilder);


        // when
        mockServerRunForkedMojo.execute();

        // then
        verify(mockRepositorySystem).createArtifactWithClassifier("org.mock-server", "mockserver-netty", mockServerRunForkedMojo.getVersion(), "jar", "jar-with-dependencies");
        verify(mockServerRunForkedMojo).newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dfile.encoding=UTF-8",
                "-Dmockserver.logLevel=" + level,
                "-jar", jarWithDependenciesPath, "-serverPort", "1", "-serverSecurePort", "2", "-proxyPort", "3", "-proxySecurePort", "4"
        ));
        assertEquals(true, processBuilder.redirectErrorStream());
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
                "-Dfile.encoding=UTF-8",
                "-Dmockserver.logLevel=" + level,
                "-jar", jarWithDependenciesPath, "-serverPort", "1", "-proxyPort", "3"
        ))).thenReturn(processBuilder);


        // when
        mockServerRunForkedMojo.execute();

        // then
        verify(mockRepositorySystem).createArtifactWithClassifier("org.mock-server", "mockserver-netty", mockServerRunForkedMojo.getVersion(), "jar", "jar-with-dependencies");
        verify(mockServerRunForkedMojo).newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dfile.encoding=UTF-8",
                "-Dmockserver.logLevel=" + level,
                "-jar", jarWithDependenciesPath, "-serverPort", "1", "-proxyPort", "3"
        ));
        assertEquals(true, processBuilder.redirectErrorStream());
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
                "-Dfile.encoding=UTF-8",
                "-Dmockserver.logLevel=" + level,
                "-jar", jarWithDependenciesPath, "-serverSecurePort", "2", "-proxySecurePort", "4"
        ))).thenReturn(processBuilder);


        // when
        mockServerRunForkedMojo.execute();

        // then
        verify(mockRepositorySystem).createArtifactWithClassifier("org.mock-server", "mockserver-netty", mockServerRunForkedMojo.getVersion(), "jar", "jar-with-dependencies");
        verify(mockServerRunForkedMojo).newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dfile.encoding=UTF-8",
                "-Dmockserver.logLevel=" + level,
                "-jar", jarWithDependenciesPath, "-serverSecurePort", "2", "-proxySecurePort", "4"
        ));
        assertEquals(true, processBuilder.redirectErrorStream());

    }

    @Test
    public void shouldRunMockServerOnlyForkedBothPortsSpecified() throws MojoExecutionException, ExecutionException, InterruptedException {
        // given
        ExampleInitializationClass.mockServerClient = null;
        mockServerRunForkedMojo.serverPort = 1;
        mockServerRunForkedMojo.serverSecurePort = 2;
        mockServerRunForkedMojo.pipeLogToConsole = true;
        mockServerRunForkedMojo.initializationClass = "org.mockserver.maven.ExampleInitializationClass";
        String classLocation = "org/mockserver/maven/ExampleInitializationClass.class";
        mockServerRunForkedMojo.compileClasspath = Arrays.asList(ExampleInitializationClass.class.getClassLoader().getResource(classLocation).getFile().replaceAll(classLocation, ""));
        mockServerRunForkedMojo.testClasspath = Arrays.asList();
        when(mockServerRunForkedMojo.newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dfile.encoding=UTF-8",
                "-Dmockserver.logLevel=" + level,
                "-jar", jarWithDependenciesPath, "-serverPort", "1", "-serverSecurePort", "2"
        ))).thenReturn(processBuilder);


        // when
        mockServerRunForkedMojo.execute();

        // then
        verify(mockServerRunForkedMojo).newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dfile.encoding=UTF-8",
                "-Dmockserver.logLevel=" + level,
                "-jar", jarWithDependenciesPath, "-serverPort", "1", "-serverSecurePort", "2"
        ));
        assertEquals(true, processBuilder.redirectErrorStream());
        assertNotNull(ExampleInitializationClass.mockServerClient);
    }

    @Test
    public void shouldRunMockServerOnlyForkedOnlyNonSecurePort() throws MojoExecutionException, ExecutionException, InterruptedException {
        // given
        ExampleInitializationClass.mockServerClient = null;
        mockServerRunForkedMojo.serverPort = 1;
        mockServerRunForkedMojo.serverSecurePort = -1;
        mockServerRunForkedMojo.pipeLogToConsole = true;
        mockServerRunForkedMojo.initializationClass = "org.mockserver.maven.ExampleInitializationClass";
        String classLocation = "org/mockserver/maven/ExampleInitializationClass.class";
        mockServerRunForkedMojo.compileClasspath = Arrays.asList(ExampleInitializationClass.class.getClassLoader().getResource(classLocation).getFile().replaceAll(classLocation, ""));
        mockServerRunForkedMojo.testClasspath = Arrays.asList();
        when(mockServerRunForkedMojo.newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dfile.encoding=UTF-8",
                "-Dmockserver.logLevel=" + level,
                "-jar", jarWithDependenciesPath, "-serverPort", "1"
        ))).thenReturn(processBuilder);


        // when
        mockServerRunForkedMojo.execute();

        // then
        verify(mockRepositorySystem).createArtifactWithClassifier("org.mock-server", "mockserver-netty", mockServerRunForkedMojo.getVersion(), "jar", "jar-with-dependencies");
        verify(mockServerRunForkedMojo).newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dfile.encoding=UTF-8",
                "-Dmockserver.logLevel=" + level,
                "-jar", jarWithDependenciesPath, "-serverPort", "1"
        ));
        assertEquals(true, processBuilder.redirectErrorStream());
        assertNotNull(ExampleInitializationClass.mockServerClient);
    }

    @Test
    public void shouldRunMockServerOnlyForkedOnlySecurePort() throws MojoExecutionException, ExecutionException, InterruptedException {
        // given
        ExampleInitializationClass.mockServerClient = null;
        mockServerRunForkedMojo.serverPort = -1;
        mockServerRunForkedMojo.serverSecurePort = 2;
        mockServerRunForkedMojo.pipeLogToConsole = true;
        mockServerRunForkedMojo.initializationClass = "org.mockserver.maven.ExampleInitializationClass";
        when(mockServerRunForkedMojo.newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dfile.encoding=UTF-8",
                "-Dmockserver.logLevel=" + level,
                "-jar", jarWithDependenciesPath, "-serverSecurePort", "2"
        ))).thenReturn(processBuilder);


        // when
        mockServerRunForkedMojo.execute();

        // then
        verify(mockRepositorySystem).createArtifactWithClassifier("org.mock-server", "mockserver-netty", mockServerRunForkedMojo.getVersion(), "jar", "jar-with-dependencies");
        verify(mockServerRunForkedMojo).newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dfile.encoding=UTF-8",
                "-Dmockserver.logLevel=" + level,
                "-jar", jarWithDependenciesPath, "-serverSecurePort", "2"
        ));
        assertEquals(true, processBuilder.redirectErrorStream());
        assertNull(ExampleInitializationClass.mockServerClient);
    }

    @Test
    public void shouldRunProxyOnlyForkedBothPortsSpecified() throws MojoExecutionException, ExecutionException, InterruptedException {
        // given
        ExampleInitializationClass.mockServerClient = null;
        mockServerRunForkedMojo.proxyPort = 1;
        mockServerRunForkedMojo.proxySecurePort = 2;
        mockServerRunForkedMojo.pipeLogToConsole = true;
        mockServerRunForkedMojo.initializationClass = "org.mockserver.maven.ExampleInitializationClass";
        when(mockServerRunForkedMojo.newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dfile.encoding=UTF-8",
                "-Dmockserver.logLevel=" + level,
                "-jar", jarWithDependenciesPath, "-proxyPort", "1", "-proxySecurePort", "2"
        ))).thenReturn(processBuilder);


        // when
        mockServerRunForkedMojo.execute();

        // then
        verify(mockRepositorySystem).createArtifactWithClassifier("org.mock-server", "mockserver-netty", mockServerRunForkedMojo.getVersion(), "jar", "jar-with-dependencies");
        verify(mockServerRunForkedMojo).newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dfile.encoding=UTF-8",
                "-Dmockserver.logLevel=" + level,
                "-jar", jarWithDependenciesPath, "-proxyPort", "1", "-proxySecurePort", "2"
        ));
        assertEquals(true, processBuilder.redirectErrorStream());
        assertNull(ExampleInitializationClass.mockServerClient);
    }

    @Test
    public void shouldRunProxyOnlyForkedOnlyNonSecurePort() throws MojoExecutionException, ExecutionException, InterruptedException {
        // given
        mockServerRunForkedMojo.proxyPort = 1;
        mockServerRunForkedMojo.proxySecurePort = -1;
        mockServerRunForkedMojo.pipeLogToConsole = true;
        when(mockServerRunForkedMojo.newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dfile.encoding=UTF-8",
                "-Dmockserver.logLevel=" + level,
                "-jar", jarWithDependenciesPath, "-proxyPort", "1"
        ))).thenReturn(processBuilder);


        // when
        mockServerRunForkedMojo.execute();

        // then
        verify(mockRepositorySystem).createArtifactWithClassifier("org.mock-server", "mockserver-netty", mockServerRunForkedMojo.getVersion(), "jar", "jar-with-dependencies");
        verify(mockServerRunForkedMojo).newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dfile.encoding=UTF-8",
                "-Dmockserver.logLevel=" + level,
                "-jar", jarWithDependenciesPath, "-proxyPort", "1"
        ));
        assertEquals(true, processBuilder.redirectErrorStream());
    }

    @Test
    public void shouldRunProxyOnlyForkedOnlySecurePort() throws MojoExecutionException, ExecutionException, InterruptedException {
        // given
        mockServerRunForkedMojo.proxyPort = -1;
        mockServerRunForkedMojo.proxySecurePort = 2;
        mockServerRunForkedMojo.pipeLogToConsole = true;
        when(mockServerRunForkedMojo.newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dfile.encoding=UTF-8",
                "-Dmockserver.logLevel=" + level,
                "-jar", jarWithDependenciesPath, "-proxySecurePort", "2"
        ))).thenReturn(processBuilder);


        // when
        mockServerRunForkedMojo.execute();

        // then
        verify(mockRepositorySystem).createArtifactWithClassifier("org.mock-server", "mockserver-netty", mockServerRunForkedMojo.getVersion(), "jar", "jar-with-dependencies");
        verify(mockServerRunForkedMojo).newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dfile.encoding=UTF-8",
                "-Dmockserver.logLevel=" + level,
                "-jar", jarWithDependenciesPath, "-proxySecurePort", "2"
        ));
        assertEquals(true, processBuilder.redirectErrorStream());

    }

    @Test
    public void shouldHandleProcessException() throws IOException {
        // given
        when(mockServerRunForkedMojo.newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dfile.encoding=UTF-8",
                "-Dmockserver.logLevel=" + level,
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
                "-Dfile.encoding=UTF-8",
                "-Dmockserver.logLevel=" + level,
                "-jar", jarWithDependenciesPath, "-serverPort", "0", "-serverSecurePort", "0"
        ))).thenReturn(processBuilder);

        // when
        mockServerRunForkedMojo.execute();

        // then
        verify(mockRepositorySystem).createArtifactWithClassifier("org.mock-server", "mockserver-netty", mockServerRunForkedMojo.getVersion(), "jar", "jar-with-dependencies");
        assertEquals(false, processBuilder.redirectErrorStream());
    }

    @Test
    public void shouldHandleIncorrectInitializationClassName() throws MojoExecutionException {
        // given
        mockServerRunForkedMojo.serverPort = 1;
        mockServerRunForkedMojo.serverSecurePort = 2;
        mockServerRunForkedMojo.pipeLogToConsole = true;
        mockServerRunForkedMojo.initializationClass = "org.mockserver.maven.InvalidClassName";
        when(mockServerRunForkedMojo.newProcessBuilder(Arrays.asList(
                javaBinaryPath,
                "-Dfile.encoding=UTF-8",
                "-Dmockserver.logLevel=" + level,
                "-jar", jarWithDependenciesPath, "-serverPort", "1", "-serverSecurePort", "2"
        ))).thenReturn(processBuilder);


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
        verify(mockServerRunForkedMojo, times(0)).newProcessBuilder(anyListOf(String.class));
    }
}
