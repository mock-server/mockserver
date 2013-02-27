package org.jamesdbloom.mockserver.client.serialization;

import org.codehaus.jackson.map.ObjectMapper;
import org.eclipse.jetty.http.HttpStatus;
import org.jamesdbloom.mockserver.client.serialization.model.*;
import org.jamesdbloom.mockserver.matchers.Times;
import org.jamesdbloom.mockserver.mock.Expectation;
import org.jamesdbloom.mockserver.model.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class ExpectationSerialiazerTest {

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
    private final ExpectationDTO fullExpectationDTO = new ExpectationDTO() {{
        setHttpRequest(new HttpRequestDTO() {{
            setPath("somepath");
            setBody("somebody");
            setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO() {{
                setName("headerName");
                setValues(Arrays.asList("headerValue"));
            }}));
            setCookies(Arrays.<CookieDTO>asList(new CookieDTO() {{
                setName("cookieName");
                setValues(Arrays.asList("cookieValue"));
            }}));
            setQueryParameters(Arrays.<ParameterDTO>asList(new ParameterDTO() {{
                setName("queryParameterName");
                setValues(Arrays.asList("queryParameterValue"));
            }}));
            setBodyParameters(Arrays.<ParameterDTO>asList(new ParameterDTO() {{
                setName("bodyParameterName");
                setValues(Arrays.asList("bodyParameterValue"));
            }}));
        }});
        setHttpResponse(new HttpResponseDTO() {{
            setResponseCode(HttpStatus.NOT_MODIFIED_304);
            setBody("somebody");
            setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO() {{
                setName("headerName");
                setValues(Arrays.asList("headerValue"));
            }}));
            setCookies(Arrays.<CookieDTO>asList(new CookieDTO() {{
                setName("cookieName");
                setValues(Arrays.asList("cookieValue"));
            }}));
            setDelay(new DelayDTO() {{

            }});
        }});
        setTimes(new TimesDTO() {{
            setRemainingTimes(2);
            setUnlimited(false);
        }});
    }};
    @Mock
    private ObjectMapper objectMapper;
    @InjectMocks
    private ExpectationSerializer expectationSerializer;

    @Before
    public void setupTestFixture() {
        expectationSerializer = spy(new ExpectationSerializer());

        initMocks(this);
    }

    @Test
    public void deserialize() throws IOException {
        // given
        InputStream inputStream = mock(InputStream.class);

        // when
        expectationSerializer.deserialize(inputStream);

        // then
        verify(objectMapper).readValue(same(inputStream), same(ExpectationDTO.class));
    }

    @Test(expected = RuntimeException.class)
    public void deserializeHandlesException() throws IOException {
        // given
        when(objectMapper.readValue(any(InputStream.class), same(ExpectationDTO.class))).thenThrow(new IOException());

        // when
        expectationSerializer.deserialize(mock(InputStream.class));
    }

    @Test
    public void serialize() throws IOException {
        // given
        Expectation expectation = mock(Expectation.class);
        ExpectationDTO expectationDTO = mock(ExpectationDTO.class);
        when(expectationSerializer.mapToDTO(same(expectation))).thenReturn(expectationDTO);

        // when
        expectationSerializer.serialize(expectation);

        // then
        verify(objectMapper).writeValueAsString(expectationDTO);
    }

    @Test(expected = RuntimeException.class)
    public void serializeHandlesException() throws IOException {
        // given
        Expectation expectation = mock(Expectation.class);
        when(objectMapper.writeValueAsString(any(Map.class))).thenThrow(new IOException());

        // when
        expectationSerializer.serialize(expectation);
    }

    @Test
    public void mapsToDTO() {
        // when
        ExpectationDTO expectationDTO = expectationSerializer.mapToDTO(new Expectation(fullHttpRequest, Times.once()).respond(fullHttpResponse));

        // then
        assertEquals(fullExpectationDTO, expectationDTO);
    }

    @Test
    public void mapsToExpectation() {
        // when
        Expectation expectation = expectationSerializer.mapFromDTO(fullExpectationDTO);

        // then
        assertEquals(new Expectation(fullHttpRequest, Times.once()).respond(fullHttpResponse), expectation);
    }
}
