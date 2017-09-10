package org.mockserver.client.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.serialization.model.*;
import org.mockserver.model.*;
import org.mockserver.validator.jsonschema.JsonSchemaHttpResponseValidator;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class HttpResponseSerializerTest {

    private final HttpResponse fullHttpResponse =
            new HttpResponse()
                    .withStatusCode(123)
                    .withBody(new StringBody("somebody"))
                    .withHeaders(new Header("headerName", "headerValue"))
                    .withCookies(new Cookie("cookieName", "cookieValue"))
                    .withDelay(new Delay(TimeUnit.MICROSECONDS, 3));
    private final HttpResponseDTO fullHttpResponseDTO =
            new HttpResponseDTO()
                    .setStatusCode(123)
                    .setBody(BodyWithContentTypeDTO.createDTO(new StringBody("somebody")))
                    .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("headerName", Arrays.asList("headerValue")))))
                    .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("cookieName", "cookieValue"))))
                    .setDelay(new DelayDTO(new Delay(TimeUnit.MICROSECONDS, 3)));
    @Rule
    public ExpectedException thrown = ExpectedException.none();
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ObjectWriter objectWriter;
    @Mock
    private JsonSchemaHttpResponseValidator httpResponseValidator;
    @InjectMocks
    private HttpResponseSerializer httpResponseSerializer;

    @Before
    public void setupTestFixture() {
        httpResponseSerializer = spy(new HttpResponseSerializer());

        initMocks(this);
    }

    @Test
    public void deserialize() throws IOException {
        // given
        when(httpResponseValidator.isValid(eq("responseBytes"))).thenReturn("");
        when(objectMapper.readValue(eq("responseBytes"), same(HttpResponseDTO.class))).thenReturn(fullHttpResponseDTO);

        // when
        HttpResponse httpResponse = httpResponseSerializer.deserialize("responseBytes");

        // then
        assertEquals(fullHttpResponse, httpResponse);
    }

    @Test
    public void serialize() throws IOException {
        // given
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);

        // when
        httpResponseSerializer.serialize(fullHttpResponse);

        // then
        verify(objectMapper).writerWithDefaultPrettyPrinter();
        verify(objectWriter).writeValueAsString(fullHttpResponseDTO);
    }


    @Test
    public void shouldSerializeArray() throws IOException {
        // given
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);


        // when
        httpResponseSerializer.serialize(new HttpResponse[]{fullHttpResponse, fullHttpResponse});

        // then
        verify(objectMapper).writerWithDefaultPrettyPrinter();
        verify(objectWriter).writeValueAsString(new HttpResponseDTO[]{fullHttpResponseDTO, fullHttpResponseDTO});
    }

}
