package org.jamesdbloom.mockserver.mappers;

import com.google.common.collect.ImmutableMap;
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
import java.util.ArrayList;
import java.util.Map;

import static org.junit.Assert.assertEquals;
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
        HttpResponse httpResponse = new HttpResponse().withBody("somebody");
        when(objectMapper.readValue(any(InputStream.class), same(Map.class))).thenReturn(ImmutableMap.of("httpResponse", ImmutableMap.of("body", "somebody")));

        // when
        Expectation expectation = expectationMapper.deserialize(new MockHttpServletRequest());

        // then
        assertEquals(httpResponse, expectation.getHttpResponse());
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
        ExpectationDTO expectationDTO = new ExpectationDTO(new HttpRequest().withBody("somebody"), Times.unlimited());
        expectationDTO.respond(new HttpResponse().withBody("somebody"));

        // when
        expectationMapper.serialize(expectationDTO);

        // then
        verify(objectMapper).writeValueAsString(ImmutableMap.of(
                "times",
                ImmutableMap.of("unlimited", "true", "remainingTimes", "1"),
                "httpRequest",
                ImmutableMap.of("headers", new ArrayList<Header>(), "body", "somebody", "responseCode", "200", "delay", ImmutableMap.of("timeUnit", "MICROSECONDS", "value", "0"), "cookies", new ArrayList<Cookie>()),
                "httpResponse",
                ImmutableMap.of("headers", new ArrayList<Header>(), "body", "somebody", "path", "", "cookies", new ArrayList<Cookie>(), "queryParameters", new ArrayList<Parameter>())
        ));
    }

    @Test(expected = RuntimeException.class)
    public void serializeHandlesException() throws IOException {
        // given
        ExpectationDTO expectationDTO = mock(ExpectationDTO.class);
        when(objectMapper.writeValueAsString(any(Map.class))).thenThrow(new IOException());

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
