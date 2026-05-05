package org.mockserver.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.OpenAPIExpectation;
import org.mockserver.serialization.model.OpenAPIExpectationDTO;
import org.mockserver.validator.jsonschema.JsonSchemaOpenAPIExpectationValidator;

import java.io.IOException;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author jamesdbloom
 */
public class OpenAPIExpectationSerializerTest {

    private final OpenAPIExpectation fullOpenAPIExpectation =
        new OpenAPIExpectation()
            .withSpecUrlOrPayload("some_random_spec")
            .withOperationsAndResponses(ImmutableMap.of(
                "operationOne", "200",
                "operationTwo", "default"
            ));
    private final OpenAPIExpectationDTO fullOpenAPIExpectationDTO =
        new OpenAPIExpectationDTO()
            .setSpecUrlOrPayload("some_random_spec")
            .setOperationsAndResponses(ImmutableMap.of(
                "operationOne", "200",
                "operationTwo", "default"
            ));
    @Rule
    public ExpectedException thrown = ExpectedException.none();
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ObjectWriter objectWriter;
    @Mock
    private JsonSchemaOpenAPIExpectationValidator httpRequestValidator;
    @InjectMocks
    private OpenAPIExpectationSerializer httpRequestSerializer;

    @Before
    public void setupTestFixture() {
        httpRequestSerializer = spy(new OpenAPIExpectationSerializer(new MockServerLogger()));

        openMocks(this);
    }

    @Test
    public void deserialize() throws IOException {
        // given
        when(httpRequestValidator.isValid(eq("requestBytes"))).thenReturn("");
        when(objectMapper.readValue(eq("requestBytes"), same(OpenAPIExpectationDTO.class))).thenReturn(fullOpenAPIExpectationDTO);

        // when
        OpenAPIExpectation httpRequest = httpRequestSerializer.deserialize("requestBytes");

        // then
        assertEquals(fullOpenAPIExpectation, httpRequest);
    }

    @Test
    public void serialize() throws IOException {
        // when
        httpRequestSerializer.serialize(fullOpenAPIExpectation);

        // then
        verify(objectWriter).writeValueAsString(fullOpenAPIExpectationDTO);
    }

    @Test
    @SuppressWarnings("RedundantArrayCreation")
    public void shouldSerializeArray() throws IOException {
        // when
        httpRequestSerializer.serialize(new OpenAPIExpectation[]{fullOpenAPIExpectation, fullOpenAPIExpectation});

        // then
        verify(objectWriter).writeValueAsString(new OpenAPIExpectationDTO[]{fullOpenAPIExpectationDTO, fullOpenAPIExpectationDTO});
    }

}
