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

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.NottableString.string;

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
        when(objectMapper.readValue(eq("responseBytes"), same(HttpResponseDTO.class))).thenReturn(fullHttpResponseDTO);

        // when
        HttpResponse httpResponse = httpResponseSerializer.deserialize("responseBytes");

        // then
        assertEquals(fullHttpResponse, httpResponse);
    }

    @Test
    public void deserializeHttpResponseAsField() throws IOException {
        // given
        String input = "{" + NEW_LINE +
                "    \"httpResponse\": \"responseBytes\"," + NEW_LINE +
                "}";
        when(objectMapper.readValue(eq(input), same(ExpectationDTO.class))).thenReturn(new ExpectationDTO().setHttpResponse(fullHttpResponseDTO));

        // when
        HttpResponse httpResponse = httpResponseSerializer.deserialize(input);

        // then
        assertEquals(fullHttpResponse, httpResponse);
    }

    @Test
    public void deserializeHandleException() throws IOException {
        // given
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Exception while parsing HttpResponse for [responseBytes]");
        // and
        when(objectMapper.readValue(eq("responseBytes"), same(HttpResponseDTO.class))).thenThrow(new RuntimeException("TEST EXCEPTION"));

        // when
        httpResponseSerializer.deserialize("responseBytes");
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

    @Test
    public void serializeObjectHandlesException() throws IOException {
        // given
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Exception while serializing httpResponse to JSON with value { }");
        // and
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);
        when(objectWriter.writeValueAsString(any(HttpResponseDTO.class))).thenThrow(new RuntimeException("TEST EXCEPTION"));

        // when
        httpResponseSerializer.serialize(response());
    }


    @Test
    public void serializeArrayHandlesException() throws IOException {
        // given
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Exception while serializing HttpResponse to JSON with value [{ }]");
        // and
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);
        when(objectWriter.writeValueAsString(any(HttpResponseDTO.class))).thenThrow(new RuntimeException("TEST EXCEPTION"));

        // when
        httpResponseSerializer.serialize(new HttpResponse[]{response()});
    }
}
