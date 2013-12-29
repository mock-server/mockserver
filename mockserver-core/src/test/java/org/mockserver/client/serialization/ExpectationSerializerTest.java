package org.mockserver.client.serialization;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectWriter;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.serialization.model.*;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;

import java.io.IOException;
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
public class ExpectationSerializerTest {

    private final Expectation fullExpectation = new Expectation(
            new HttpRequest()
                    .withMethod("GET")
                    .withURL("url")
                    .withPath("somepath")
                    .withQueryString("queryString")
                    .withBody("somebody")
                    .withHeaders(new Header("headerName", "headerValue"))
                    .withCookies(new Cookie("cookieName", "cookieValue")),
            Times.once()
    ).respond(new HttpResponse()
            .withStatusCode(304)
            .withBody("somebody")
            .withHeaders(new Header("headerName", "headerValue"))
            .withCookies(new Cookie("cookieName", "cookieValue"))
            .withDelay(new Delay(TimeUnit.MICROSECONDS, 1)));
    private final ExpectationDTO fullExpectationDTO = new ExpectationDTO()
            .setHttpRequest(
                    new HttpRequestDTO()
                            .setMethod("GET")
                            .setURL("url")
                            .setPath("somepath")
                            .setQueryString("queryString")
                            .setBody("somebody")
                            .setHeaders(Arrays.<HeaderDTO>asList((HeaderDTO) new HeaderDTO(new Header("headerName", Arrays.asList("headerValue")))))
                            .setCookies(Arrays.<CookieDTO>asList((CookieDTO) new CookieDTO(new Cookie("cookieName", Arrays.asList("cookieValue")))))
            )
            .setHttpResponse(
                    new HttpResponseDTO()
                            .setStatusCode(304)
                            .setBody("somebody")
                            .setHeaders(Arrays.<HeaderDTO>asList((HeaderDTO) new HeaderDTO(new Header("headerName", Arrays.asList("headerValue")))))
                            .setCookies(Arrays.<CookieDTO>asList((CookieDTO) new CookieDTO(new Cookie("cookieName", Arrays.asList("cookieValue")))))
                            .setDelay(
                                    new DelayDTO()
                                            .setTimeUnit(TimeUnit.MICROSECONDS)
                                            .setValue(1)))
            .setTimes(new TimesDTO(Times.once()));
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ObjectWriter objectWriter;
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
        byte[] requestBytes = "requestBytes".getBytes();
        when(objectMapper.readValue(eq(requestBytes), same(ExpectationDTO.class))).thenReturn(fullExpectationDTO);

        // when
        Expectation expectation = expectationSerializer.deserialize(requestBytes);

        // then
        assertEquals(fullExpectation, expectation);
    }

    @Test(expected = RuntimeException.class)
    public void deserializeHandlesException() throws IOException {
        // given
        byte[] requestBytes = "requestBytes".getBytes();
        when(objectMapper.readValue(eq(requestBytes), same(ExpectationDTO.class))).thenThrow(new IOException());

        // when
        expectationSerializer.deserialize(requestBytes);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldValidateInput() throws IOException {
        // when
        expectationSerializer.deserialize(new byte[0]);
    }

    @Test
    public void serialize() throws IOException {
        // given
        when(objectMapper.setSerializationInclusion(JsonSerialize.Inclusion.NON_DEFAULT)).thenReturn(objectMapper);
        when(objectMapper.setSerializationInclusion(JsonSerialize.Inclusion.NON_NULL)).thenReturn(objectMapper);
        when(objectMapper.setSerializationInclusion(JsonSerialize.Inclusion.NON_EMPTY)).thenReturn(objectMapper);
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);


        // when
        expectationSerializer.serialize(fullExpectation);

        // then
        verify(objectMapper).setSerializationInclusion(JsonSerialize.Inclusion.NON_DEFAULT);
        verify(objectMapper).setSerializationInclusion(JsonSerialize.Inclusion.NON_NULL);
        verify(objectMapper).setSerializationInclusion(JsonSerialize.Inclusion.NON_EMPTY);
        verify(objectMapper).writerWithDefaultPrettyPrinter();
        verify(objectWriter).writeValueAsString(fullExpectationDTO);
    }

    @Test(expected = RuntimeException.class)
    public void serializeHandlesException() throws IOException {
        // given
        when(objectMapper.setSerializationInclusion(JsonSerialize.Inclusion.NON_DEFAULT)).thenReturn(objectMapper);
        when(objectMapper.setSerializationInclusion(JsonSerialize.Inclusion.NON_NULL)).thenReturn(objectMapper);
        when(objectMapper.setSerializationInclusion(JsonSerialize.Inclusion.NON_EMPTY)).thenReturn(objectMapper);
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);
        when(objectWriter.writeValueAsString(any(ExpectationDTO.class))).thenThrow(new IOException());

        // when
        expectationSerializer.serialize(mock(Expectation.class));
    }
}
