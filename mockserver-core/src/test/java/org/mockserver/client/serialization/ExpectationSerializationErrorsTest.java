package org.mockserver.client.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.mock.Expectation;
import org.mockserver.validator.jsonschema.JsonSchemaExpectationValidator;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class ExpectationSerializationErrorsTest {

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
    private ExpectationSerializer expectationSerializer;

    @Before
    public void setupTestFixture() {
        expectationSerializer = spy(new ExpectationSerializer());

        initMocks(this);
    }

    @Test
    public void shouldHandleNullAndEmptyWhileSerializingArray() throws IOException {
        // when
        assertEquals("", expectationSerializer.serialize(new Expectation[]{}));
        assertEquals("", expectationSerializer.serialize((Expectation[]) null));
    }

    @Test
    public void shouldValidateInputForObject() throws IOException {
        // given
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("1 error:" + NEW_LINE + " - an expectation is required but value was \"\"");
        // when
        expectationSerializer.deserialize("");
    }

    @Test
    public void shouldValidateInputForArray() throws IOException {
        // given
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("1 error:" + NEW_LINE + " - an expectation or expectation array is required but value was \"\"");
        // when
        expectationSerializer.deserializeArray("");
    }
}
