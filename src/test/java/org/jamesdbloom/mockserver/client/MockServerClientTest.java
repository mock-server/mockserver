package org.jamesdbloom.mockserver.client;

import org.jamesdbloom.mockserver.mappers.ExpectationMapper;
import org.jamesdbloom.mockserver.matchers.Times;
import org.jamesdbloom.mockserver.model.HttpRequest;
import org.jamesdbloom.mockserver.model.HttpResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
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
        mockServerClient = spy(new MockServerClient("localhost", 8080));

        initMocks(this);
    }

    @Test
    public void setupExpectation() {
        // given
        final HttpRequest httpRequest = new HttpRequest();
        final HttpResponse httpResponse = new HttpResponse();
        ArgumentCaptor<ExpectationDTO> argumentCaptor = ArgumentCaptor.forClass(ExpectationDTO.class);
        doAnswer(new Answer() {
            public Object answer(InvocationOnMock invocation) {
                return null;
            }
        }).when(mockServerClient).sendExpectation(argumentCaptor.capture());

        // when
        mockServerClient.when(httpRequest).respond(httpResponse);

        // then
        ExpectationDTO expectationDTO = argumentCaptor.getValue();
        assertSame(httpRequest, expectationDTO.getHttpRequest());
        assertEquals(Times.unlimited(), expectationDTO.getTimes());
        assertSame(httpResponse, expectationDTO.getHttpResponse());
    }
}
