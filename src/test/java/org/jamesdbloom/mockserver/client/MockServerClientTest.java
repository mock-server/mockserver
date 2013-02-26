package org.jamesdbloom.mockserver.client;

import org.jamesdbloom.mockserver.mappers.ExpectationMapper;
import org.jamesdbloom.mockserver.matchers.HttpRequestMatcher;
import org.jamesdbloom.mockserver.matchers.Times;
import org.jamesdbloom.mockserver.model.HttpRequest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class MockServerClientTest {

    @Mock
    private ExpectationMapper expectationMapper;
    @InjectMocks
    private MockServerClient mockServerClient;

    @Before
    public void setupTestFixture() {
        mockServerClient = new MockServerClient("localhost", 8080);

        initMocks(this);
    }

    @Test
    public void setupExpectation() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpRequestMatcher httpRequestMatcher = new HttpRequestMatcher();
        when(expectationMapper.transformsToMatcher(same(httpRequest))).thenReturn(httpRequestMatcher);

        // when
        ExpectationDTO expectationDTO = mockServerClient.when(httpRequest);

        // then
        assertEquals(expectationDTO.getHttpRequestMatcher(), httpRequestMatcher);
        assertEquals(expectationDTO.getTimes(), Times.unlimited());
        assertNull(expectationDTO.getHttpResponse());
    }
}
