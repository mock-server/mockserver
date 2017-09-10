package org.mockserver.matchers;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.validator.jsonschema.JsonSchemaValidator;
import org.slf4j.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class JsonSchemaMatcherTest {

    @Mock
    private JsonSchemaValidator mockJsonSchemaValidator;

    @InjectMocks
    private JsonSchemaMatcher jsonSchemaMatcher;

    @Before
    public void setupMocks() {
        jsonSchemaMatcher = new JsonSchemaMatcher(JSON_SCHEMA);
        initMocks(this);
    }

    @Mock
    protected Logger logger;

    public static final String JSON_SCHEMA = "{" + NEW_LINE +
            "    \"type\": \"object\"," + NEW_LINE +
            "    \"properties\": {" + NEW_LINE +
            "        \"enumField\": {" + NEW_LINE +
            "            \"enum\": [ \"one\", \"two\" ]" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"arrayField\": {" + NEW_LINE +
            "            \"type\": \"array\"," + NEW_LINE +
            "            \"minItems\": 1," + NEW_LINE +
            "            \"items\": {" + NEW_LINE +
            "                \"type\": \"string\"" + NEW_LINE +
            "            }," + NEW_LINE +
            "            \"uniqueItems\": true" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"stringField\": {" + NEW_LINE +
            "            \"type\": \"string\"," + NEW_LINE +
            "            \"minLength\": 5," + NEW_LINE +
            "            \"maxLength\": 6" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"booleanField\": {" + NEW_LINE +
            "            \"type\": \"boolean\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"objectField\": {" + NEW_LINE +
            "            \"type\": \"object\"," + NEW_LINE +
            "            \"properties\": {" + NEW_LINE +
            "                \"stringField\": {" + NEW_LINE +
            "                    \"type\": \"string\"," + NEW_LINE +
            "                    \"minLength\": 1," + NEW_LINE +
            "                    \"maxLength\": 3" + NEW_LINE +
            "                }" + NEW_LINE +
            "            }," + NEW_LINE +
            "            \"required\": [ \"stringField\" ]" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"additionalProperties\" : false," + NEW_LINE +
            "    \"required\": [ \"enumField\", \"arrayField\" ]" + NEW_LINE +
            "}";

    @Test
    public void shouldMatchJson() {
        // given
        String json = "some_json";
        when(mockJsonSchemaValidator.isValid(json)).thenReturn("");

        // then
        assertTrue(jsonSchemaMatcher.matches(json));
    }

    @Test
    public void shouldNotMatchJson() {
        // given
        String json = "some_json";
        when(mockJsonSchemaValidator.isValid(json)).thenReturn("validator_error");

        // when
        assertFalse(jsonSchemaMatcher.matches(json));

        // then
        verify(logger).trace("Failed to perform JSON match \"{}\" with schema \"{}\" because {}", "some_json", JSON_SCHEMA, "validator_error");
    }

    @Test
    public void shouldHandleExpection() {
        // given
        String json = "some_json";
        when(mockJsonSchemaValidator.isValid(json)).thenThrow(new RuntimeException("TEST_EXCEPTION"));

        // when
        assertFalse(jsonSchemaMatcher.matches(json));

        // then
        verify(logger).trace("Failed to perform JSON match \"{}\" with schema \"{}\" because {}", "some_json", JSON_SCHEMA, "TEST_EXCEPTION");
    }

    @Test
    public void showHaveCorrectEqualsBehaviour() {
        assertEquals(new JsonSchemaMatcher(JSON_SCHEMA), new JsonSchemaMatcher(JSON_SCHEMA));
    }
}
