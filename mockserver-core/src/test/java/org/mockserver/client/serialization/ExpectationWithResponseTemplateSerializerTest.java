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
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;
import org.mockserver.validator.jsonschema.JsonSchemaExpectationValidator;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpTemplate.template;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class ExpectationWithResponseTemplateSerializerTest {

    private final Expectation fullExpectation = new Expectation(
            new HttpRequest()
                    .withMethod("GET")
                    .withPath("somePath")
                    .withQueryStringParameters(new Parameter("queryParameterName", Collections.singletonList("queryParameterValue")))
                    .withBody(new StringBody("somebody"))
                    .withHeaders(new Header("headerName", "headerValue"))
                    .withCookies(new Cookie("cookieName", "cookieValue")),
            Times.once(),
            TimeToLive.exactly(HOURS, 2l))
            .thenRespond(
                    template(HttpTemplate.TemplateType.JAVASCRIPT, "some_random_template")
                            .withDelay(SECONDS, 5)
            );
    private final ExpectationDTO fullExpectationDTO = new ExpectationDTO()
            .setHttpRequest(
                    new HttpRequestDTO()
                            .setMethod(string("GET"))
                            .setPath(string("somePath"))
                            .setQueryStringParameters(Collections.singletonList(new ParameterDTO(new Parameter("queryParameterName", Collections.singletonList("queryParameterValue")))))
                            .setBody(BodyDTO.createDTO(new StringBody("somebody")))
                            .setHeaders(Collections.singletonList(new HeaderDTO(new Header("headerName", Collections.singletonList("headerValue")))))
                            .setCookies(Collections.singletonList(new CookieDTO(new Cookie("cookieName", "cookieValue"))))
            )
            .setHttpResponseTemplate(
                    new HttpTemplateDTO(
                            new HttpTemplate(HttpTemplate.TemplateType.JAVASCRIPT)
                                    .withTemplate("some_random_template")
                            .withDelay(new Delay(SECONDS, 5))
                    )
            )
            .setTimes(new TimesDTO(Times.once()))
            .setTimeToLive(new TimeToLiveDTO(TimeToLive.exactly(HOURS, 2l)));

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ObjectWriter objectWriter;
    @Mock
    private JsonArraySerializer jsonArraySerializer;
    @Mock
    private JsonSchemaExpectationValidator expectationValidator;

    @InjectMocks
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer();


    @Before
    public void setupTestFixture() {
        initMocks(this);
    }

    @Test
    public void shouldSerializeObject() throws IOException {
        // given
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);

        // when
        expectationSerializer.serialize(fullExpectation);

        // then
        verify(objectMapper).writerWithDefaultPrettyPrinter();
        verify(objectWriter).writeValueAsString(fullExpectationDTO);
    }

    @Test
    public void shouldSerializeArray() throws IOException {
        // given
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);

        // when
        expectationSerializer.serialize(fullExpectation, fullExpectation);

        // then
        verify(objectMapper).writerWithDefaultPrettyPrinter();
        verify(objectWriter).writeValueAsString(new ExpectationDTO[]{fullExpectationDTO, fullExpectationDTO});
    }

    @Test
    public void shouldDeserializeObject() throws IOException {
        // given
        when(objectMapper.readValue(eq("requestBytes"), same(ExpectationDTO.class))).thenReturn(fullExpectationDTO);
        when(expectationValidator.isValid("requestBytes")).thenReturn("");

        // when
        Expectation expectation = expectationSerializer.deserialize("requestBytes");

        // then
        assertThat(expectation, is(fullExpectation));
    }

    @Test
    public void shouldDeserializeArray() throws IOException {
        // given
        when(jsonArraySerializer.returnJSONObjects("requestBytes")).thenReturn(Arrays.asList("requestBytes", "requestBytes"));
        when(expectationValidator.isValid("requestBytes")).thenReturn("");
        when(objectMapper.readValue(eq("requestBytes"), same(ExpectationDTO.class))).thenReturn(fullExpectationDTO);

        // when
        Expectation[] expectations = expectationSerializer.deserializeArray("requestBytes");

        // then
        assertArrayEquals(new Expectation[]{fullExpectation, fullExpectation}, expectations);
    }

    @Test
    public void shouldDeserializeObjectWithError() throws IOException {
        // given
        when(objectMapper.readValue(eq("requestBytes"), same(ExpectationDTO.class))).thenReturn(fullExpectationDTO);
        when(expectationValidator.isValid("requestBytes")).thenReturn("an error");

        // then
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("an error");

        // when
        expectationSerializer.deserialize("requestBytes");
    }

    @Test
    public void shouldDeserializeArrayWithError() throws IOException {
        // given
        when(jsonArraySerializer.returnJSONObjects("requestBytes")).thenReturn(Arrays.asList("requestBytes", "requestBytes"));
        when(expectationValidator.isValid("requestBytes")).thenReturn("an error");
        when(objectMapper.readValue(eq("requestBytes"), same(ExpectationDTO.class))).thenReturn(fullExpectationDTO);

        // then
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("" +
                "[" + NEW_LINE +
                "  an error," + NEW_LINE +
                "  an error" + NEW_LINE +
                "]");

        // when
        expectationSerializer.deserializeArray("requestBytes");
    }
}
