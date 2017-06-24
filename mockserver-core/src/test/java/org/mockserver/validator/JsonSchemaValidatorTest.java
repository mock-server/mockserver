package org.mockserver.validator;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.slf4j.Logger;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class JsonSchemaValidatorTest {

    public static final String JSON_SCHEMA = "{\n" +
            "    \"type\": \"object\",\n" +
            "    \"properties\": {\n" +
            "        \"enumField\": {\n" +
            "            \"enum\": [ \"one\", \"two\" ]\n" +
            "        },\n" +
            "        \"arrayField\": {\n" +
            "            \"type\": \"array\",\n" +
            "            \"minItems\": 1,\n" +
            "            \"items\": {\n" +
            "                \"type\": \"string\"\n" +
            "            },\n" +
            "            \"uniqueItems\": true\n" +
            "        },\n" +
            "        \"stringField\": {\n" +
            "            \"type\": \"string\",\n" +
            "            \"minLength\": 5,\n" +
            "            \"maxLength\": 6\n" +
            "        },\n" +
            "        \"booleanField\": {\n" +
            "            \"type\": \"boolean\"\n" +
            "        },\n" +
            "        \"objectField\": {\n" +
            "            \"type\": \"object\",\n" +
            "            \"properties\": {\n" +
            "                \"stringField\": {\n" +
            "                    \"type\": \"string\",\n" +
            "                    \"minLength\": 1,\n" +
            "                    \"maxLength\": 3\n" +
            "                }\n" +
            "            },\n" +
            "            \"required\": [ \"stringField\" ]\n" +
            "        }\n" +
            "    },\n" +
            "    \"additionalProperties\" : false,\n" +
            "    \"required\": [ \"enumField\", \"arrayField\" ]\n" +
            "}";
    @Mock
    protected Logger logger;

    @Before
    public void createMocks() {
        initMocks(this);
    }

    @Test
    public void shouldMatchJson() {
        assertThat(new JsonSchemaValidator().validateJson(JSON_SCHEMA, "{arrayField: [ \"one\" ], enumField: \"one\"}"), is(""));
    }

    @Test
    public void shouldHandleJsonMissingRequiredFields() {
        // then
        assertThat(new JsonSchemaValidator().validateJson(JSON_SCHEMA, "{}"), is("com.github.fge.jsonschema.core.report.ListProcessingReport: failure\n" +
                "--- BEGIN MESSAGES ---\n" +
                "error: object has missing required properties ([\"arrayField\",\"enumField\"])\n" +
                "    level: \"error\"\n" +
                "    schema: {\"loadingURI\":\"#\",\"pointer\":\"\"}\n" +
                "    instance: {\"pointer\":\"\"}\n" +
                "    domain: \"validation\"\n" +
                "    keyword: \"required\"\n" +
                "    required: [\"arrayField\",\"enumField\"]\n" +
                "    missing: [\"arrayField\",\"enumField\"]\n" +
                "---  END MESSAGES  ---\n" +
                ""));
    }

    @Test
    public void shouldHandleJsonTooFewItems() {
        // then
        assertThat(new JsonSchemaValidator().validateJson(JSON_SCHEMA, "{arrayField: [ ],         enumField: \\\"one\\\"}"),
                is("JsonParseException - Unexpected character ('\\' (code 92)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')\n" +
                " at [Source: {arrayField: [ ],         enumField: \\\"one\\\"}; line: 1, column: 39]"));
    }

    @Test
    public void shouldHandleJsonTooLongString() {
        assertThat(new JsonSchemaValidator().validateJson(JSON_SCHEMA, "{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", stringField: \\\"1234567\\\"}"),
                is("JsonParseException - Unexpected character ('\\' (code 92)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')\n" +
                " at [Source: {arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", stringField: \\\"1234567\\\"}; line: 1, column: 17]"));
    }

    @Test
    public void shouldHandleJsonIncorrectEnum() {
        assertThat(new JsonSchemaValidator().validateJson(JSON_SCHEMA, "{arrayField: [ \\\"one\\\" ], enumField: \\\"four\\\"}"),
                is("JsonParseException - Unexpected character ('\\' (code 92)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')\n" +
                " at [Source: {arrayField: [ \\\"one\\\" ], enumField: \\\"four\\\"}; line: 1, column: 17]"));
    }

    @Test
    public void shouldHandleJsonExtraField() {
        assertThat(new JsonSchemaValidator().validateJson(JSON_SCHEMA, "{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", extra: \\\"field\\\"}"),
                is("JsonParseException - Unexpected character ('\\' (code 92)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')\n" +
                " at [Source: {arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", extra: \\\"field\\\"}; line: 1, column: 17]"));
    }


    @Test
    public void shouldHandleJsonIncorrectSubField() {
        assertThat(new JsonSchemaValidator().validateJson(JSON_SCHEMA, "{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", objectField: {stringField: \\\"1234\\\"} }"),
                is("JsonParseException - Unexpected character ('\\' (code 92)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')\n" +
                " at [Source: {arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", objectField: {stringField: \\\"1234\\\"} }; line: 1, column: 17]"));
    }


    @Test
    public void shouldHandleJsonMissingSubField() {
        assertThat(new JsonSchemaValidator().validateJson(JSON_SCHEMA, "{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", objectField: { } }"),
                is("JsonParseException - Unexpected character ('\\' (code 92)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')\n" +
                " at [Source: {arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", objectField: { } }; line: 1, column: 17]"));
    }


    @Test
    public void shouldHandleJsonMultipleErrors() {
        assertThat(new JsonSchemaValidator().validateJson(JSON_SCHEMA, "{arrayField: [ ],  stringField: \\\"1234\\\"}"),
                is("JsonParseException - Unexpected character ('\\' (code 92)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')\n" +
                " at [Source: {arrayField: [ ],  stringField: \\\"1234\\\"}; line: 1, column: 34]"));
    }

    @Test
    public void shouldHandleIllegalJson() {
        // given
        assertThat(new JsonSchemaValidator().validateJson("illegal_json", "illegal_json"),
                is("JsonParseException - Unrecognized token 'illegal_json': was expecting ('true', 'false' or 'null')\n" +
                " at [Source: illegal_json; line: 1, column: 25]"));

        // and
        assertThat(new JsonSchemaValidator().validateJson("illegal_json", "some_other_illegal_json"),
                is("JsonParseException - Unrecognized token 'illegal_json': was expecting ('true', 'false' or 'null')\n" +
                " at [Source: illegal_json; line: 1, column: 25]"));
    }

    @Test
    public void shouldHandleNullExpectation() {
        // given
        assertThat(new JsonSchemaValidator().validateJson(null, "some_value"), is("NullPointerException - null"));
    }

    @Test
    public void shouldHandleEmptyExpectation() {
        // given
        assertThat(new JsonSchemaValidator().validateJson("", "some_value"),
                is("JsonMappingException - No content to map due to end-of-input\n" +
                " at [Source: ; line: 1, column: 0]"));
    }

    @Test
    public void shouldHandleNullTest() {
        // given
        assertThat(new JsonSchemaValidator().validateJson("some_value", null),
                is("JsonParseException - Unrecognized token 'some_value': was expecting ('true', 'false' or 'null')\n" +
                " at [Source: some_value; line: 1, column: 21]"));
    }

    @Test
    public void shouldHandleEmptyTest() {
        // given
        assertThat(new JsonSchemaValidator().validateJson("some_value", ""),
                is("JsonParseException - Unrecognized token 'some_value': was expecting ('true', 'false' or 'null')\n" +
                " at [Source: some_value; line: 1, column: 21]"));
    }
}