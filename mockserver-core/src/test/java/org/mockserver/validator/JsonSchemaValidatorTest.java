package org.mockserver.validator;

import org.junit.Before;
import org.junit.Test;
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
        assertThat(new JsonSchemaValidator(JSON_SCHEMA).isValid( "{}"), is("com.github.fge.jsonschema.core.report.ListProcessingReport: failure" + NEW_LINE +
                "--- BEGIN MESSAGES ---" + NEW_LINE +
                "error: object has missing required properties ([\"arrayField\",\"enumField\"])" + NEW_LINE +
                "    level: \"error\"" + NEW_LINE +
                "    schema: {\"loadingURI\":\"#\",\"pointer\":\"\"}" + NEW_LINE +
                "    instance: {\"pointer\":\"\"}" + NEW_LINE +
                "    domain: \"validation\"" + NEW_LINE +
                "    keyword: \"required\"" + NEW_LINE +
                "    required: [\"arrayField\",\"enumField\"]" + NEW_LINE +
                "    missing: [\"arrayField\",\"enumField\"]" + NEW_LINE +
                "---  END MESSAGES  ---" + NEW_LINE +
                ""));
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
        // given
        assertThat(new JsonSchemaValidator("illegal_json").isValid("illegal_json"),
                is("JsonParseException - Unrecognized token 'illegal_json': was expecting ('true', 'false' or 'null')" + NEW_LINE +
                " at [Source: illegal_json; line: 1, column: 25]"));

        // and
        assertThat(new JsonSchemaValidator("illegal_json").isValid("some_other_illegal_json"),
                is("JsonParseException - Unrecognized token 'illegal_json': was expecting ('true', 'false' or 'null')" + NEW_LINE +
                " at [Source: illegal_json; line: 1, column: 25]"));
    }

    @Test
    public void shouldHandleNullExpectation() {
        // given
        assertThat(new JsonSchemaValidator(null).isValid("some_value"), is("NullPointerException - null"));
    }

    @Test
    public void shouldHandleEmptyExpectation() {
        // given
        assertThat(new JsonSchemaValidator("").isValid("some_value"),
                is("JsonMappingException - No content to map due to end-of-input" + NEW_LINE +
                " at [Source: ; line: 1, column: 0]"));
    }

    @Test
    public void shouldHandleNullTest() {
        // given
        assertThat(new JsonSchemaValidator("some_value").isValid(null),
                is("JsonParseException - Unrecognized token 'some_value': was expecting ('true', 'false' or 'null')" + NEW_LINE +
                " at [Source: some_value; line: 1, column: 21]"));
    }

    @Test
    public void shouldHandleEmptyTest() {
        // given
        assertThat(new JsonSchemaValidator("some_value").isValid( ""),
                is("JsonParseException - Unrecognized token 'some_value': was expecting ('true', 'false' or 'null')" + NEW_LINE +
                " at [Source: some_value; line: 1, column: 21]"));
    }
}