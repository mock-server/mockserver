package org.mockserver.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.*;
import org.mockserver.serialization.model.HttpRequestDTO;
import org.mockserver.serialization.model.SocketAddressDTO;
import org.mockserver.serialization.model.StringBodyDTO;
import org.mockserver.validator.jsonschema.JsonSchemaHttpRequestValidator;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.StringBody.exact;

/**
 * @author jamesdbloom
 */
public class HttpRequestSerializerTest {

    private final HttpRequest fullHttpRequest =
        new HttpRequest()
            .withMethod("GET")
            .withPath("somepath")
            .withQueryStringParameters(
                new Parameter("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two"),
                new Parameter("queryStringParameterNameTwo", "queryStringParameterValueTwo_One")
            )
            .withBody(new StringBody("someBody"))
            .withHeaders(new Header("headerName", "headerValue"))
            .withCookies(new Cookie("cookieName", "cookieValue"))
            .withSecure(true)
            .withSocketAddress(
                new SocketAddress()
                    .withHost("someHost")
                    .withPort(1234)
                    .withScheme(SocketAddress.Scheme.HTTPS)
            )
            .withKeepAlive(true);
    private final HttpRequestDTO fullHttpRequestDTO =
        new HttpRequestDTO()
            .setMethod(string("GET"))
            .setPath(string("somepath"))
            .setQueryStringParameters(new Parameters().withEntries(
                param("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two"),
                param("queryStringParameterNameTwo", "queryStringParameterValueTwo_One")
            ))
            .setBody(new StringBodyDTO(exact("someBody")))
            .setHeaders(new Headers().withEntries(
                header("headerName", "headerValue")
            ))
            .setCookies(new Cookies().withEntries(
                cookie("cookieName", "cookieValue")
            ))
            .setSecure(true)
            .setSocketAddress(new SocketAddressDTO(
                new SocketAddress()
                    .withHost("someHost")
                    .withPort(1234)
                    .withScheme(SocketAddress.Scheme.HTTPS)
            ))
            .setKeepAlive(true);
    @Rule
    public ExpectedException thrown = ExpectedException.none();
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ObjectWriter objectWriter;
    @Mock
    private JsonSchemaHttpRequestValidator httpRequestValidator;
    @InjectMocks
    private HttpRequestSerializer httpRequestSerializer;

    @Before
    public void setupTestFixture() {
        httpRequestSerializer = spy(new HttpRequestSerializer(new MockServerLogger()));

        initMocks(this);
    }

    @Test
    public void deserialize() throws IOException {
        // given
        when(httpRequestValidator.isValid(eq("requestBytes"))).thenReturn("");
        when(objectMapper.readValue(eq("requestBytes"), same(HttpRequestDTO.class))).thenReturn(fullHttpRequestDTO);

        // when
        HttpRequest httpRequest = httpRequestSerializer.deserialize("requestBytes");

        // then
        assertEquals(fullHttpRequest, httpRequest);
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

    @Test
    public void shouldSerializeArray() throws IOException {
        // given
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);


        // when
        httpRequestSerializer.serialize(new HttpRequest[]{fullHttpRequest, fullHttpRequest});

        // then
        verify(objectMapper).writerWithDefaultPrettyPrinter();
        verify(objectWriter).writeValueAsString(new HttpRequestDTO[]{fullHttpRequestDTO, fullHttpRequestDTO});
    }

}
