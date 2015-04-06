package org.mockserver.matchers;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author jamesdbloom
 */
public class JsonSchemaMatcherTest {

        public static final String JSON_SCHEMA = "{" + System.getProperty("line.separator") +
                "    \"type\": \"object\"," + System.getProperty("line.separator") +
                "    \"properties\": {" + System.getProperty("line.separator") +
                "        \"enumField\": {" + System.getProperty("line.separator") +
                "            \"enum\": [ \"one\", \"two\" ]" + System.getProperty("line.separator") +
                "        }," + System.getProperty("line.separator") +
                "        \"arrayField\": {" + System.getProperty("line.separator") +
                "            \"type\": \"array\"," + System.getProperty("line.separator") +
                "            \"minItems\": 1," + System.getProperty("line.separator") +
                "            \"items\": {" + System.getProperty("line.separator") +
                "                \"type\": \"string\"" + System.getProperty("line.separator") +
                "            }," + System.getProperty("line.separator") +
                "            \"uniqueItems\": true" + System.getProperty("line.separator") +
                "        }," + System.getProperty("line.separator") +
                "        \"stringField\": {" + System.getProperty("line.separator") +
                "            \"type\": \"string\"," + System.getProperty("line.separator") +
                "            \"minLength\": 5," + System.getProperty("line.separator") +
                "            \"maxLength\": 6" + System.getProperty("line.separator") +
                "        }," + System.getProperty("line.separator") +
                "        \"booleanField\": {" + System.getProperty("line.separator") +
                "            \"type\": \"boolean\"" + System.getProperty("line.separator") +
                "        }," + System.getProperty("line.separator") +
                "        \"objectField\": {" + System.getProperty("line.separator") +
                "            \"type\": \"object\"," + System.getProperty("line.separator") +
                "            \"properties\": {" + System.getProperty("line.separator") +
                "                \"stringField\": {" + System.getProperty("line.separator") +
                "                    \"type\": \"string\"," + System.getProperty("line.separator") +
                "                    \"minLength\": 1," + System.getProperty("line.separator") +
                "                    \"maxLength\": 3" + System.getProperty("line.separator") +
                "                }" + System.getProperty("line.separator") +
                "            }," + System.getProperty("line.separator") +
                "            \"required\": [ \"stringField\" ]" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"additionalProperties\" : false," + System.getProperty("line.separator") +
                "    \"required\": [ \"enumField\", \"arrayField\" ]" + System.getProperty("line.separator") +
                "}";

        @Test
    public void shouldMatchJson() {
        assertTrue(new JsonSchemaMatcher(JSON_SCHEMA).matches("{arrayField: [ \"one\" ], enumField: \"one\"}"));
    }

        @Test
    public void shouldNotMatchJsonMissingRequiredFields() {
        assertFalse(new JsonSchemaMatcher(JSON_SCHEMA).matches("{}"));
    }

        @Test
    public void shouldNotMatchJsonTooFewItems() {
                assertFalse(new JsonSchemaMatcher(JSON_SCHEMA).matches("{arrayField: [ ],         enumField: \\\"one\\\"}"));
    }

        @Test
    public void shouldNotMatchJsonTooLongString() {
                assertFalse(new JsonSchemaMatcher(JSON_SCHEMA).matches("{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", stringField: \\\"1234567\\\"}"));
    }

        @Test
    public void shouldNotMatchJsonIncorrectEnum() {
                assertFalse(new JsonSchemaMatcher(JSON_SCHEMA).matches("{arrayField: [ \\\"one\\\" ], enumField: \\\"four\\\"}"));
    }

        @Test
    public void shouldNotMatchJsonExtraField() {
                assertFalse(new JsonSchemaMatcher(JSON_SCHEMA).matches("{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", extra: \\\"field\\\"}"));
    }


        @Test
    public void shouldNotMatchJsonIncorrectSubField() {
                assertFalse(new JsonSchemaMatcher(JSON_SCHEMA).matches("{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", objectField: {stringField: \\\"1234\\\"} }"));
    }


        @Test
    public void shouldNotMatchJsonMissingSubField() {
                assertFalse(new JsonSchemaMatcher(JSON_SCHEMA).matches("{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", objectField: { } }"));
    }


        @Test
    public void shouldNotMatchJsonMultipleErrors() {
                assertFalse(new JsonSchemaMatcher(JSON_SCHEMA).matches("{arrayField: [ ],  stringField: \\\"1234\\\"}"));
    }

    @Test
    public void shouldNotMatchIllegalJson() {
        assertFalse(new JsonSchemaMatcher("illegal_json").matches("illegal_json"));
        assertFalse(new JsonSchemaMatcher("illegal_json").matches("some_other_illegal_json"));
    }

    @Test
    public void shouldNotMatchNullExpectation() {
        assertFalse(new JsonSchemaMatcher(null).matches("some_value"));
    }

    @Test
    public void shouldNotMatchEmptyExpectation() {
        assertFalse(new JsonSchemaMatcher("").matches("some_value"));
    }

    @Test
    public void shouldNotMatchNullTest() {
        assertFalse(new JsonSchemaMatcher("some_value").matches(null));
    }

    @Test
    public void shouldNotMatchEmptyTest() {
        assertFalse(new JsonSchemaMatcher("some_value").matches(""));
    }

    @Test
    public void showHaveCorrectEqualsBehaviour() {
        assertEquals(new JsonSchemaMatcher("some_value"), new JsonSchemaMatcher("some_value"));
    }
}
