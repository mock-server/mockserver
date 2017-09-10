package org.mockserver.validator.jsonschema;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.slf4j.Logger;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class JsonSchemaValidatorTest {

    @Rule
    public final ExpectedException exception = ExpectedException.none();

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
    @Mock
    protected Logger logger;

    @Before
    public void createMocks() {
        initMocks(this);
    }

    @Test
    public void shouldMatchJson() {
        assertThat(new JsonSchemaValidator(JSON_SCHEMA).isValid("{arrayField: [ \"one\" ], enumField: \"one\"}"), is(""));
    }

    @Test
    public void shouldHandleJsonMissingRequiredFields() {
        // then
        assertThat(new JsonSchemaValidator(JSON_SCHEMA).isValid( "{}"), is("1 error:\n" +
                " - object has missing required properties ([\"arrayField\",\"enumField\"])"));
    }

    @Test
    public void shouldHandleJsonTooFewItems() {
        // then
        assertThat(new JsonSchemaValidator(JSON_SCHEMA).isValid( "{arrayField: [ ],         enumField: \\\"one\\\"}"),
                is("JsonParseException - Unexpected character ('\\' (code 92)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')" + NEW_LINE +
                " at [Source: {arrayField: [ ],         enumField: \\\"one\\\"}; line: 1, column: 39]"));
    }

    @Test
    public void shouldHandleJsonTooLongString() {
        assertThat(new JsonSchemaValidator(JSON_SCHEMA).isValid( "{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", stringField: \\\"1234567\\\"}"),
                is("JsonParseException - Unexpected character ('\\' (code 92)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')" + NEW_LINE +
                " at [Source: {arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", stringField: \\\"1234567\\\"}; line: 1, column: 17]"));
    }

    @Test
    public void shouldHandleJsonIncorrectEnum() {
        assertThat(new JsonSchemaValidator(JSON_SCHEMA).isValid( "{arrayField: [ \\\"one\\\" ], enumField: \\\"four\\\"}"),
                is("JsonParseException - Unexpected character ('\\' (code 92)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')" + NEW_LINE +
                " at [Source: {arrayField: [ \\\"one\\\" ], enumField: \\\"four\\\"}; line: 1, column: 17]"));
    }

    @Test
    public void shouldHandleJsonExtraField() {
        assertThat(new JsonSchemaValidator(JSON_SCHEMA).isValid( "{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", extra: \\\"field\\\"}"),
                is("JsonParseException - Unexpected character ('\\' (code 92)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')" + NEW_LINE +
                " at [Source: {arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", extra: \\\"field\\\"}; line: 1, column: 17]"));
    }


    @Test
    public void shouldHandleJsonIncorrectSubField() {
        assertThat(new JsonSchemaValidator(JSON_SCHEMA).isValid( "{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", objectField: {stringField: \\\"1234\\\"} }"),
                is("JsonParseException - Unexpected character ('\\' (code 92)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')" + NEW_LINE +
                " at [Source: {arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", objectField: {stringField: \\\"1234\\\"} }; line: 1, column: 17]"));
    }


    @Test
    public void shouldHandleJsonMissingSubField() {
        assertThat(new JsonSchemaValidator(JSON_SCHEMA).isValid( "{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", objectField: { } }"),
                is("JsonParseException - Unexpected character ('\\' (code 92)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')" + NEW_LINE +
                " at [Source: {arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", objectField: { } }; line: 1, column: 17]"));
    }


    @Test
    public void shouldHandleJsonMultipleErrors() {
        assertThat(new JsonSchemaValidator(JSON_SCHEMA).isValid( "{arrayField: [ ],  stringField: \\\"1234\\\"}"),
                is("JsonParseException - Unexpected character ('\\' (code 92)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')" + NEW_LINE +
                " at [Source: {arrayField: [ ],  stringField: \\\"1234\\\"}; line: 1, column: 34]"));
    }

    @Test
    public void shouldHandleIllegalJson() {
        // then
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Schema must either be a path reference to a *.json file or a json string");

        // given
        new JsonSchemaValidator("illegal_json").isValid("illegal_json");
    }

    @Test
    public void shouldHandleNullExpectation() {
        // then
        exception.expect(NullPointerException.class);

        // given
        new JsonSchemaValidator(null).isValid("some_value");
    }

    @Test
    public void shouldHandleEmptyExpectation() {
        // then
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Schema must either be a path reference to a *.json file or a json string");

        // given
        new JsonSchemaValidator("").isValid("some_value");
    }

    @Test
    public void shouldHandleNullTest() {
        // given
        assertThat(new JsonSchemaValidator(JSON_SCHEMA).isValid(null), is(""));
    }

    @Test
    public void shouldHandleEmptyTest() {
        // given
        assertThat(new JsonSchemaValidator(JSON_SCHEMA).isValid( ""),
                is("JsonMappingException - No content to map due to end-of-input\n at [Source: ; line: 1, column: 0]"));
    }
}