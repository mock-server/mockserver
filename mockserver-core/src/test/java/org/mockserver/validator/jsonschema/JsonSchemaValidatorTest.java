package org.mockserver.validator.jsonschema;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.logging.MockServerLogger;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.validator.jsonschema.JsonSchemaValidator.OPEN_API_SPECIFICATION_URL;

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
    @Rule
    public final ExpectedException exception = ExpectedException.none();
    private final MockServerLogger mockServerLogger = new MockServerLogger();

    @Before
    public void createMocks() {
        initMocks(this);
    }

    @Test
    public void shouldMatchJson() {
        assertThat(new JsonSchemaValidator(mockServerLogger, JSON_SCHEMA).isValid("{arrayField: [ \"one\" ], enumField: \"one\"}"), is(""));
    }

    @Test
    public void shouldHandleJsonMissingRequiredFields() {
        // then
        assertThat(new JsonSchemaValidator(mockServerLogger, JSON_SCHEMA).isValid("{}"), is("1 error:" + NEW_LINE +
            " - object has missing required properties ([\"arrayField\",\"enumField\"])" + NEW_LINE +
            NEW_LINE +
            OPEN_API_SPECIFICATION_URL));
    }

    @Test
    public void shouldHandleJsonTooFewItems() {
        // then
        assertThat(new JsonSchemaValidator(mockServerLogger, JSON_SCHEMA).isValid("{arrayField: [ ],         enumField: \\\"one\\\"}"),
            is("JsonParseException - Unexpected character ('\\' (code 92)): expected a valid value (JSON String, Number (or 'NaN'/'INF'/'+INF'), Array, Object or token 'null', 'true' or 'false')\n" +
                " at [Source: (String)\"{arrayField: [ ],         enumField: \\\"one\\\"}\"; line: 1, column: 39]"));
    }

    @Test
    public void shouldHandleJsonTooLongString() {
        assertThat(new JsonSchemaValidator(mockServerLogger, JSON_SCHEMA).isValid("{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", stringField: \\\"1234567\\\"}"),
            is("JsonParseException - Unexpected character ('\\' (code 92)): expected a valid value (JSON String, Number (or 'NaN'/'INF'/'+INF'), Array, Object or token 'null', 'true' or 'false')\n" +
                " at [Source: (String)\"{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", stringField: \\\"1234567\\\"}\"; line: 1, column: 17]"));
    }

    @Test
    public void shouldHandleJsonIncorrectEnum() {
        assertThat(new JsonSchemaValidator(mockServerLogger, JSON_SCHEMA).isValid("{arrayField: [ \\\"one\\\" ], enumField: \\\"four\\\"}"),
            is("JsonParseException - Unexpected character ('\\' (code 92)): expected a valid value (JSON String, Number (or 'NaN'/'INF'/'+INF'), Array, Object or token 'null', 'true' or 'false')\n" +
                " at [Source: (String)\"{arrayField: [ \\\"one\\\" ], enumField: \\\"four\\\"}\"; line: 1, column: 17]"));
    }

    @Test
    public void shouldHandleJsonExtraField() {
        assertThat(new JsonSchemaValidator(mockServerLogger, JSON_SCHEMA).isValid("{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", extra: \\\"field\\\"}"),
            is("JsonParseException - Unexpected character ('\\' (code 92)): expected a valid value (JSON String, Number (or 'NaN'/'INF'/'+INF'), Array, Object or token 'null', 'true' or 'false')\n" +
                " at [Source: (String)\"{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", extra: \\\"field\\\"}\"; line: 1, column: 17]"));
    }


    @Test
    public void shouldHandleJsonIncorrectSubField() {
        assertThat(new JsonSchemaValidator(mockServerLogger, JSON_SCHEMA).isValid("{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", objectField: {stringField: \\\"1234\\\"} }"),
            is("JsonParseException - Unexpected character ('\\' (code 92)): expected a valid value (JSON String, Number (or 'NaN'/'INF'/'+INF'), Array, Object or token 'null', 'true' or 'false')\n" +
                " at [Source: (String)\"{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", objectField: {stringField: \\\"1234\\\"} }\"; line: 1, column: 17]"));
    }


    @Test
    public void shouldHandleJsonMissingSubField() {
        assertThat(new JsonSchemaValidator(mockServerLogger, JSON_SCHEMA).isValid("{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", objectField: { } }"),
            is("JsonParseException - Unexpected character ('\\' (code 92)): expected a valid value (JSON String, Number (or 'NaN'/'INF'/'+INF'), Array, Object or token 'null', 'true' or 'false')\n" +
                " at [Source: (String)\"{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", objectField: { } }\"; line: 1, column: 17]"));
    }


    @Test
    public void shouldHandleJsonMultipleErrors() {
        assertThat(new JsonSchemaValidator(mockServerLogger, JSON_SCHEMA).isValid("{arrayField: [ ],  stringField: \\\"1234\\\"}"),
            is("JsonParseException - Unexpected character ('\\' (code 92)): expected a valid value (JSON String, Number (or 'NaN'/'INF'/'+INF'), Array, Object or token 'null', 'true' or 'false')\n" +
                " at [Source: (String)\"{arrayField: [ ],  stringField: \\\"1234\\\"}\"; line: 1, column: 34]"));
    }

    @Test
    public void shouldHandleIllegalJson() {
        // then
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Schema must either be a path reference to a *.json file or a json string");

        // given
        new JsonSchemaValidator(mockServerLogger, "illegal_json").isValid("illegal_json");
    }

    @Test
    public void shouldHandleNullExpectation() {
        // then
        exception.expect(NullPointerException.class);

        // given
        new JsonSchemaValidator(mockServerLogger, null).isValid("some_value");
    }

    @Test
    public void shouldHandleEmptyExpectation() {
        // then
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Schema must either be a path reference to a *.json file or a json string");

        // given
        new JsonSchemaValidator(mockServerLogger, "").isValid("some_value");
    }

    @Test
    public void shouldHandleNullTest() {
        // given
        assertThat(new JsonSchemaValidator(mockServerLogger, JSON_SCHEMA).isValid(null), is(""));
    }

    @Test
    public void shouldHandleEmptyTest() {
        // given
        assertThat(new JsonSchemaValidator(mockServerLogger, JSON_SCHEMA).isValid(""), is(""));
    }
}
