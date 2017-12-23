package org.mockserver.client.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.serialization.model.BodyWithContentTypeDTO;
import org.mockserver.client.serialization.model.DelayDTO;
import org.mockserver.client.serialization.model.HttpResponseDTO;
import org.mockserver.model.*;
import org.mockserver.validator.jsonschema.JsonSchemaHttpResponseValidator;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.StringBody.exact;

/**
 * @author jamesdbloom
 */
public class HttpResponseSerializerTest {

    private final HttpResponse fullHttpResponse =
        new HttpResponse()
            .withStatusCode(123)
            .withReasonPhrase("randomPhrase")
            .withBody(exact("somebody"))
            .withHeaders(header("headerName", "headerValue"))
            .withCookies(cookie("cookieName", "cookieValue"))
            .withDelay(new Delay(TimeUnit.MICROSECONDS, 3));
    private final HttpResponseDTO fullHttpResponseDTO =
        new HttpResponseDTO()
            .setStatusCode(123)
            .setReasonPhrase("randomPhrase")
            .setBody(BodyWithContentTypeDTO.createDTO(exact("somebody")))
            .setHeaders(new Headers().withEntries(
                header("headerName", "headerValue")
            ))
            .setCookies(new Cookies().withEntries(
                cookie("cookieName", "cookieValue")
            ))
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
