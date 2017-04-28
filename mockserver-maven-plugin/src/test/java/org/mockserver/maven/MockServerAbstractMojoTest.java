package org.mockserver.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.Assert.assertSame;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class MockServerAbstractMojoTest {

    @Mock
    private InstanceHolder mockEmbeddedJettyHolder;

    @InjectMocks
    private MockServerAbstractMojo mockServerAbstractMojo;

    @Before
    public void setupMocks() {
        mockServerAbstractMojo = new MockServerAbstractMojo() {
            @Override
            public void execute() throws MojoExecutionException, MojoFailureException {
                throw new UnsupportedOperationException("method not implemented yet");
            }
        };
        initMocks(this);
        MockServerAbstractMojo.embeddedJettyHolder = mockEmbeddedJettyHolder;
    }

    @Test
    public void shouldAlwaysReturnSameObject() {
        // given
        MockServerAbstractMojo mockServerAbstractMojo = new MockServerAbstractMojo() {
            @Override
            public void execute() throws MojoExecutionException, MojoFailureException {
                throw new UnsupportedOperationException("method not implemented yet");
            }
        };

        // then
        assertSame(mockServerAbstractMojo.getEmbeddedJettyHolder(), mockServerAbstractMojo.getEmbeddedJettyHolder());
    }

    @Test
    public void shouldNotCreateIfAlreadyInitialized() {
        // then
        assertSame(mockEmbeddedJettyHolder, mockServerAbstractMojo.getEmbeddedJettyHolder());
    }
}
