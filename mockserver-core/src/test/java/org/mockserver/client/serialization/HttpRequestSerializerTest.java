package org.mockserver.client.serialization;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectWriter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.serialization.model.*;
import org.mockserver.model.*;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class HttpRequestSerializerTest {

    private final HttpRequest fullHttpRequest =
            new HttpRequest()
                    .withMethod("GET")
                    .withURL("http://www.example.com")
                    .withPath("somepath")
                    .withQueryStringParameters(
                            new Parameter("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two"),
                            new Parameter("queryStringParameterNameTwo", "queryStringParameterValueTwo_One")
                    )
                    .withBody(new StringBody("somebody", Body.Type.EXACT))
                    .withHeaders(new Header("headerName", "headerValue"))
                    .withCookies(new Cookie("cookieName", "cookieValue"));
    private final HttpRequestDTO fullHttpRequestDTO =
            new HttpRequestDTO()
                    .setMethod("GET")
                    .setURL("http://www.example.com")
                    .setPath("somepath")
                    .setQueryStringParameters(Arrays.asList(
                            new ParameterDTO(new Parameter("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two")),
                            new ParameterDTO(new Parameter("queryStringParameterNameTwo", "queryStringParameterValueTwo_One"))
                    ))
                    .setBody(BodyDTO.createDTO(new StringBody("somebody", Body.Type.EXACT)))
                    .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("headerName", Arrays.asList("headerValue")))))
                    .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("cookieName", Arrays.asList("cookieValue")))));
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ObjectWriter objectWriter;
    @InjectMocks
    private HttpRequestSerializer httpRequestSerializer;

    @Before
    public void setupTestFixture() {
        httpRequestSerializer = spy(new HttpRequestSerializer());

        initMocks(this);
    }

    @Test
    public void deserialize() throws IOException {
        // given
        when(objectMapper.readValue(eq("requestBytes"), same(HttpRequestDTO.class))).thenReturn(fullHttpRequestDTO);

        // when
        HttpRequest httpRequest = httpRequestSerializer.deserialize("requestBytes");

        // then
        assertEquals(fullHttpRequest, httpRequest);
    }

    @Test
    public void deserializeHandleException() throws IOException {
        // given
        when(objectMapper.readValue(eq("requestBytes"), same(HttpRequestDTO.class))).thenThrow(new IOException("TEST EXCEPTION"));

        try {
            // when
            httpRequestSerializer.deserialize("requestBytes");
        } catch (Throwable t) {
            fail();
        }
    }

    @Test
    public void serialize() throws IOException {
        // given
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);

        // when
        httpRequestSerializer.serialize(fullHttpRequest);

        // then
        verify(objectMapper).writerWithDefaultPrettyPrinter();
        verify(objectWriter).writeValueAsString(fullHttpRequestDTO);
    }

    @Test(expected = RuntimeException.class)
    public void serializeHandlesException() throws IOException {
        // given
        HttpRequest httpRequest = mock(HttpRequest.class);
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);
        when(objectWriter.writeValueAsString(any(HttpRequestDTO.class))).thenThrow(new IOException("TEST EXCEPTION"));

        // when
        httpRequestSerializer.serialize(httpRequest);
    }
}
