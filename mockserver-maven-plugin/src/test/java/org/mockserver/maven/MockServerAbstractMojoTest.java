package org.mockserver.maven;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.Assert.assertSame;
import static org.mockito.MockitoAnnotations.openMocks;

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
            public void execute() {
                throw new UnsupportedOperationException("method not implemented yet");
            }
        };
        openMocks(this);
        MockServerAbstractMojo.instanceHolder = mockEmbeddedJettyHolder;
    }

    @Test
    public void shouldAlwaysReturnSameObject() {
        // given
        MockServerAbstractMojo mockServerAbstractMojo = new MockServerAbstractMojo() {
            @Override
            public void execute() {
                throw new UnsupportedOperationException("method not implemented yet");
            }
        };

        // then
        assertSame(mockServerAbstractMojo.getLocalMockServerInstance(), mockServerAbstractMojo.getLocalMockServerInstance());
    }

    @Test
    public void shouldNotCreateIfAlreadyInitialized() {
        // then
        assertSame(mockEmbeddedJettyHolder, mockServerAbstractMojo.getLocalMockServerInstance());
    }
}
