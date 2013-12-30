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
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.contains;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class MockServerRunForkedMojoTest {

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
        processBuilder = new ProcessBuilder("echo", "$JAVA_HOME");
        mockServerRunForkedMojo = new MockServerRunForkedMojo();
        initMocks(this);
    }

    @Test
    public void shouldRunMockServerForked() throws MojoExecutionException, ExecutionException, InterruptedException {
        // given
        mockServerRunForkedMojo.logLevel = "LEVEL";
        mockServerRunForkedMojo.stopPort = 3;
        mockServerRunForkedMojo.stopKey = "stopKey";
        mockServerRunForkedMojo.port = 1;
        mockServerRunForkedMojo.securePort = 2;
        mockServerRunForkedMojo.pipeLogToConsole = true;
        when(mockServerRunForkedMojo.newProcessBuilder(Arrays.asList(
                "java",
                "-Dmockserver.logLevel=LEVEL",
                "-Dmockserver.stopPort=3",
                "-Dmockserver.stopKey=stopKey",
                "-jar", "/foo", "-serverPort", "1", "-serverSecurePort", "2"
        ))).thenReturn(processBuilder);
        when(mockServerRunForkedMojo.getJavaBin()).thenReturn("java");
        when(mockRepositorySystem.createArtifactWithClassifier("org.mock-server", "mockserver-jetty", "2.0-SNAPSHOT", "jar", "jar-with-dependencies")).thenReturn(mockArtifact);
        when(mockArtifact.getFile()).thenReturn(new File("/foo"));


        // when
        mockServerRunForkedMojo.execute();

        // then
        verify(mockServerRunForkedMojo).newProcessBuilder(Arrays.asList(
                "java",
                "-Dmockserver.logLevel=LEVEL",
                "-Dmockserver.stopPort=3",
                "-Dmockserver.stopKey=stopKey",
                "-jar", "/foo", "-serverPort", "1", "-serverSecurePort", "2"
        ));
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectInput());
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectOutput());
        assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectError());
    }

    // todo complete other scenarios
    // todo complete other scenarios
    // todo complete other scenarios
    // todo complete other scenarios
    // todo complete other scenarios
}
