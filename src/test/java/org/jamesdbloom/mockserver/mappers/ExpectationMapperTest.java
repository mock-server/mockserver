package org.jamesdbloom.mockserver.mappers;

import org.codehaus.jackson.map.ObjectMapper;
import org.jamesdbloom.mockserver.client.ExpectationDTO;
import org.jamesdbloom.mockserver.matchers.HttpRequestMatcher;
import org.jamesdbloom.mockserver.matchers.Times;
import org.jamesdbloom.mockserver.mock.Expectation;
import org.jamesdbloom.mockserver.model.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class ExpectationMapperTest {

    @Mock
    private ObjectMapper objectMapper;
    @InjectMocks
    private ExpectationMapper expectationMapper;

    @Before
    public void setupTestFixture() {
        expectationMapper = new ExpectationMapper();

        initMocks(this);
    }

    @Test
    public void deserialize() throws IOException {
        // given
        HttpRequestMatcher httpRequestMatcher = new HttpRequestMatcher();
        HttpResponse httpResponse = new HttpResponse();

        ExpectationDTO expectationDTO = mock(ExpectationDTO.class);
        when(expectationDTO.getHttpRequestMatcher()).thenReturn(httpRequestMatcher);
        when(expectationDTO.getTimes()).thenReturn(Times.unlimited());
        when(expectationDTO.getHttpResponse()).thenReturn(httpResponse);

        when(objectMapper.readValue(any(InputStream.class), same(ExpectationDTO.class))).thenReturn(expectationDTO);

        // when
        Expectation expectation = expectationMapper.deserialize(new MockHttpServletRequest());

        // then
        assertSame(httpResponse, expectation.getHttpResponse());
        assertTrue(expectation.contains(httpRequestMatcher));
    }

    @Test(expected = RuntimeException.class)
    public void deserializeHandlesException() throws IOException {
        // given
        when(objectMapper.readValue(any(InputStream.class), same(ExpectationDTO.class))).thenThrow(new IOException());

        // when
        expectationMapper.deserialize(new MockHttpServletRequest());
    }

    @Test
    public void serialize() throws IOException {
        // given
        ExpectationDTO expectationDTO = mock(ExpectationDTO.class);

        // when
        expectationMapper.serialize(expectationDTO);

        // then
        verify(objectMapper).writeValueAsString(same(expectationDTO));
    }

    @Test(expected = RuntimeException.class)
    public void serializeHandlesException() throws IOException {
        // given
        ExpectationDTO expectationDTO = mock(ExpectationDTO.class);
        when(objectMapper.writeValueAsString(same(expectationDTO))).thenThrow(new IOException());

        // when
        expectationMapper.serialize(expectationDTO);
    }

    @Test
    public void transformsHttpRequestToHttpRequestMatcher() {
        // given
        HttpRequest httpRequest = new HttpRequest()
                .withPath("somepath")
                .withBody("somebody")
                .withHeaders(new Header("name", "value"))
                .withCookies(new Cookie("name", "value"))
                .withQueryParameters(new Parameter("queryParameterName", "queryParameterValue"))
                .withBodyParameters(new Parameter("bodyParameterName", "bodyParameterValue"));

        // when
        HttpRequestMatcher httpRequestMapper = expectationMapper.transformsToMatcher(httpRequest);

        // then
        assertTrue(httpRequestMapper.matches(httpRequest));
    }
}
