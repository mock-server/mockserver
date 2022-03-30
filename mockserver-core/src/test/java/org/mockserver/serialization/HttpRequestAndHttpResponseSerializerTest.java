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
import org.mockserver.serialization.model.*;
import org.mockserver.validator.jsonschema.JsonSchemaHttpRequestAndHttpResponseValidator;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.StringBody.exact;

/**
 * @author jamesdbloom
 */
public class HttpRequestAndHttpResponseSerializerTest {

    private final HttpRequestAndHttpResponse fullHttpRequestAndHttpResponse =
        new HttpRequestAndHttpResponse()
            .withHttpRequest(
                new HttpRequest()
                    .withMethod("GET")
                    .withPath("somepath")
                    .withPathParameters(
                        new Parameter("pathParameterNameOne", "pathParameterValueOne_One", "pathParameterValueOne_Two"),
                        new Parameter("pathParameterNameTwo", "pathParameterValueTwo_One")
                    )
                    .withQueryStringParameters(
                        new Parameter("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two"),
                        new Parameter("queryStringParameterNameTwo", "queryStringParameterValueTwo_One")
                    )
                    .withBody(new StringBody("someBody"))
                    .withHeaders(new Header("headerName", "headerValue"))
                    .withCookies(new Cookie("cookieName", "cookieValue"))
                    .withSecure(true)
                    .withSocketAddress("someHost", 1234, SocketAddress.Scheme.HTTPS)
                    .withKeepAlive(true)
            )
            .withHttpResponse(
                new HttpResponse()
                    .withStatusCode(123)
                    .withReasonPhrase("randomPhrase")
                    .withBody(exact("somebody"))
                    .withHeaders(header("headerName", "headerValue"))
                    .withCookies(cookie("cookieName", "cookieValue"))
                    .withDelay(new Delay(TimeUnit.MICROSECONDS, 3))
            );
    private final HttpRequestAndHttpResponseDTO fullHttpRequestAndHttpResponseDTO =
        new HttpRequestAndHttpResponseDTO()
            .setHttpRequest(
                new HttpRequestDTO()
                    .setMethod(string("GET"))
                    .setPath(string("somepath"))
                    .setPathParameters(new Parameters().withEntries(
                        param("pathParameterNameOne", "pathParameterValueOne_One", "pathParameterValueOne_Two"),
                        param("pathParameterNameTwo", "pathParameterValueTwo_One")
                    ))
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
                    .setSocketAddress(new SocketAddress()
                        .withHost("someHost")
                        .withPort(1234)
                        .withScheme(SocketAddress.Scheme.HTTPS)
                    )
                    .setKeepAlive(true)
            )
            .setHttpResponse(
                new HttpResponseDTO()
                    .setStatusCode(123)
                    .setReasonPhrase("randomPhrase")
                    .setBody(BodyWithContentTypeDTO.createWithContentTypeDTO(exact("somebody")))
                    .setHeaders(new Headers().withEntries(
                        header("headerName", "headerValue")
                    ))
                    .setCookies(new Cookies().withEntries(
                        cookie("cookieName", "cookieValue")
                    ))
                    .setDelay(new DelayDTO(new Delay(TimeUnit.MICROSECONDS, 3)))
            );

    @Rule
    public ExpectedException thrown = ExpectedException.none();
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ObjectWriter objectWriter;
    @Mock
    private JsonSchemaHttpRequestAndHttpResponseValidator jsonSchemaHttpRequestAndHttpResponseValidator;
    @InjectMocks
    private HttpRequestAndHttpResponseSerializer httpRequestAndHttpResponseSerializer;

    @Before
    public void setupTestFixture() {
        httpRequestAndHttpResponseSerializer = spy(new HttpRequestAndHttpResponseSerializer(new MockServerLogger()));

        openMocks(this);
    }

    @Test
    public void deserialize() throws IOException {
        // given
        when(jsonSchemaHttpRequestAndHttpResponseValidator.isValid(eq("requestBytes"))).thenReturn("");
        when(objectMapper.readValue(eq("requestBytes"), same(HttpRequestAndHttpResponseDTO.class))).thenReturn(fullHttpRequestAndHttpResponseDTO);

        // when
        HttpRequestAndHttpResponse httpRequestAndHttpResponse = httpRequestAndHttpResponseSerializer.deserialize("requestBytes");

        // then
        assertEquals(fullHttpRequestAndHttpResponse, httpRequestAndHttpResponse);
    }

    @Test
    public void serialize() throws IOException {
        // when
        httpRequestAndHttpResponseSerializer.serialize(fullHttpRequestAndHttpResponse);

        // then
        verify(objectWriter).writeValueAsString(fullHttpRequestAndHttpResponseDTO);
    }

    @Test
    @SuppressWarnings("RedundantArrayCreation")
    public void shouldSerializeArray() throws IOException {
        // when
        httpRequestAndHttpResponseSerializer.serialize(new HttpRequestAndHttpResponse[]{fullHttpRequestAndHttpResponse, fullHttpRequestAndHttpResponse});

        // then
        verify(objectWriter).writeValueAsString(new HttpRequestAndHttpResponseDTO[]{fullHttpRequestAndHttpResponseDTO, fullHttpRequestAndHttpResponseDTO});
    }

}
