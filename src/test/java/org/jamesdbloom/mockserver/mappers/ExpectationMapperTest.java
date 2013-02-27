package org.jamesdbloom.mockserver.mappers;

import com.google.common.collect.ImmutableMap;
import org.codehaus.jackson.map.ObjectMapper;
import org.eclipse.jetty.http.HttpStatus;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

    private final HashMap fullMap = new HashMap() {{
        put("times",
                new HashMap() {{
                    put("unlimited", "true");
                    put("remainingTimes", "1");
                }});
        put("httpResponse",
                new HashMap() {{
                    put("headers", Arrays.asList(new Header("name", "value")));
                    put("responseCode", "" + HttpStatus.BAD_GATEWAY_502);
                    put("body", "somebody");
                    put("delay",
                            new HashMap() {{
                                put("timeUnit", "MINUTES");
                                put("value", "2");
                            }});
                    put("cookies", Arrays.asList(new Cookie("name", "value")));
                }});
        put("httpRequest",
                new HashMap() {{
                    put("headers", Arrays.asList(new Header("name", "value")));
                    put("body", "somebody");
                    put("bodyParameters", Arrays.asList(new Parameter("bodyParameterName", "bodyParameterValue")));
                    put("path", "somepath");
                    put("cookies", Arrays.asList(new Cookie("name", "value")));
                    put("queryParameters", Arrays.asList(new Parameter("queryParameterName", "queryParameterValue")));
                }});
    }};
    private final HttpResponse fullHttpResponse = new HttpResponse()
            .withStatusCode(HttpStatus.BAD_GATEWAY_502)
            .withBody("somebody")
            .withHeaders(new Header("name", "value"))
            .withCookies(new Cookie("name", "value"))
            .withDelay(new Delay(TimeUnit.MINUTES, 2));
    private final HttpRequest fullHttpRequest = new HttpRequest()
            .withPath("somepath")
            .withBody("somebody")
            .withHeaders(new Header("name", "value"))
            .withCookies(new Cookie("name", "value"))
            .withQueryParameters(new Parameter("queryParameterName", "queryParameterValue"))
            .withBodyParameters(new Parameter("bodyParameterName", "bodyParameterValue"));
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
        when(objectMapper.readValue(any(InputStream.class), same(Map.class))).thenReturn(fullMap);

        // when
        Expectation expectation = expectationMapper.deserialize(new MockHttpServletRequest());

        // then
        assertEquals(fullHttpResponse, expectation.getHttpResponse());
        assertEquals(fullHttpResponse, expectation.getHttpRequest());
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
        ExpectationDTO expectationDTO = new ExpectationDTO(fullHttpRequest,Times.unlimited());
        expectationDTO.respond(fullHttpResponse);

        // when
        expectationMapper.serialize(expectationDTO);

        // then
        verify(objectMapper).writeValueAsString(fullMap);
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
