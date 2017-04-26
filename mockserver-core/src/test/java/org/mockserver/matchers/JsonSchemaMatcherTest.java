package org.mockserver.matchers;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.slf4j.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class JsonSchemaMatcherTest {

    @Mock
    protected Logger logger;

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

    @Before
    public void createMocks() {
        initMocks(this);
    }

    @Test
    public void shouldMatchJson() {
        assertTrue(new JsonSchemaMatcher(JSON_SCHEMA).matches("{arrayField: [ \"one\" ], enumField: \"one\"}"));
    }

    @Test
    public void shouldNotMatchJsonMissingRequiredFields() {
        // given
        JsonSchemaMatcher jsonSchemaMatcher = new JsonSchemaMatcher(JSON_SCHEMA);
        jsonSchemaMatcher.logger = logger;

        // then
        assertFalse(jsonSchemaMatcher.matches("{}"));

        // and
        verify(logger).trace("Failed to perform JSON match \"{}\" with schema \"{}\" because {}",
                "{}",
                "{\n" +
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
                        "}",
                "com.github.fge.jsonschema.core.report.ListProcessingReport: failure\n" +
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
                        "");
    }

    @Test
    public void shouldNotMatchJsonTooFewItems() {
        // given
        JsonSchemaMatcher jsonSchemaMatcher = new JsonSchemaMatcher(JSON_SCHEMA);
        jsonSchemaMatcher.logger = logger;

        // then
        assertFalse(jsonSchemaMatcher.matches("{arrayField: [ ],         enumField: \\\"one\\\"}"));

        // and
        verify(logger).trace("Failed to perform JSON match \"{}\" with \"{}\" because {}",
                "{arrayField: [ ],         enumField: \\\"one\\\"}",
                "{\n" +
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
                        "}",
                "Unexpected character ('\\' (code 92)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')\n" +
                        " at [Source: {arrayField: [ ],         enumField: \\\"one\\\"}; line: 1, column: 39]");
    }

    @Test
    public void shouldNotMatchJsonTooLongString() {
        // given
        JsonSchemaMatcher jsonSchemaMatcher = new JsonSchemaMatcher(JSON_SCHEMA);
        jsonSchemaMatcher.logger = logger;

        // then
        assertFalse(jsonSchemaMatcher.matches("{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", stringField: \\\"1234567\\\"}"));

        // and
        verify(logger).trace("Failed to perform JSON match \"{}\" with \"{}\" because {}",
                "{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", stringField: \\\"1234567\\\"}",
                "{\n" +
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
                        "}",
                "Unexpected character ('\\' (code 92)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')\n" +
                        " at [Source: {arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", stringField: \\\"1234567\\\"}; line: 1, column: 17]");
    }

    @Test
    public void shouldNotMatchJsonIncorrectEnum() {
        // given
        JsonSchemaMatcher jsonSchemaMatcher = new JsonSchemaMatcher(JSON_SCHEMA);
        jsonSchemaMatcher.logger = logger;

        // then
        assertFalse(jsonSchemaMatcher.matches("{arrayField: [ \\\"one\\\" ], enumField: \\\"four\\\"}"));

        // and
        verify(logger).trace("Failed to perform JSON match \"{}\" with \"{}\" because {}", "{arrayField: [ \\\"one\\\" ], enumField: \\\"four\\\"}", "{\n" +
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
                        "}",
                "Unexpected character ('\\' (code 92)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')\n" +
                        " at [Source: {arrayField: [ \\\"one\\\" ], enumField: \\\"four\\\"}; line: 1, column: 17]");
    }

    @Test
    public void shouldNotMatchJsonExtraField() {
        // given
        JsonSchemaMatcher jsonSchemaMatcher = new JsonSchemaMatcher(JSON_SCHEMA);
        jsonSchemaMatcher.logger = logger;

        // then
        assertFalse(jsonSchemaMatcher.matches("{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", extra: \\\"field\\\"}"));

        // and
        verify(logger).trace("Failed to perform JSON match \"{}\" with \"{}\" because {}",
                "{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", extra: \\\"field\\\"}",
                "{\n" +
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
                        "}",
                "Unexpected character ('\\' (code 92)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')\n" +
                        " at [Source: {arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", extra: \\\"field\\\"}; line: 1, column: 17]");
    }


    @Test
    public void shouldNotMatchJsonIncorrectSubField() {
        // given
        JsonSchemaMatcher jsonSchemaMatcher = new JsonSchemaMatcher(JSON_SCHEMA);
        jsonSchemaMatcher.logger = logger;

        // then
        assertFalse(jsonSchemaMatcher.matches("{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", objectField: {stringField: \\\"1234\\\"} }"));

        // and
        verify(logger).trace(
                "Failed to perform JSON match \"{}\" with \"{}\" because {}",
                "{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", objectField: {stringField: \\\"1234\\\"} }",
                "{\n" +
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
                        "}",
                "Unexpected character ('\\' (code 92)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')\n" +
                        " at [Source: {arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", objectField: {stringField: \\\"1234\\\"} }; line: 1, column: 17]");
    }


    @Test
    public void shouldNotMatchJsonMissingSubField() {
        // given
        JsonSchemaMatcher jsonSchemaMatcher = new JsonSchemaMatcher(JSON_SCHEMA);
        jsonSchemaMatcher.logger = logger;

        // then
        assertFalse(jsonSchemaMatcher.matches("{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", objectField: { } }"));

        // and
        verify(logger).trace("Failed to perform JSON match \"{}\" with \"{}\" because {}",
                "{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", objectField: { } }",
                "{\n" +
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
                        "}",
                "Unexpected character ('\\' (code 92)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')\n" +
                        " at [Source: {arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", objectField: { } }; line: 1, column: 17]");
    }


    @Test
    public void shouldNotMatchJsonMultipleErrors() {
        // given
        JsonSchemaMatcher jsonSchemaMatcher = new JsonSchemaMatcher(JSON_SCHEMA);
        jsonSchemaMatcher.logger = logger;

        // then
        assertFalse(jsonSchemaMatcher.matches("{arrayField: [ ],  stringField: \\\"1234\\\"}"));

        // and
        verify(logger).trace("Failed to perform JSON match \"{}\" with \"{}\" because {}",
                "{arrayField: [ ],  stringField: \\\"1234\\\"}",
                "{\n" +
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
                        "}",
                "Unexpected character ('\\' (code 92)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')\n" +
                        " at [Source: {arrayField: [ ],  stringField: \\\"1234\\\"}; line: 1, column: 34]");
    }

    @Test
    public void shouldNotMatchIllegalJson() {
        // given
        JsonSchemaMatcher jsonSchemaMatcher = new JsonSchemaMatcher("illegal_json");
        jsonSchemaMatcher.logger = logger;

        // then
        assertFalse(jsonSchemaMatcher.matches("illegal_json"));

        // and
        verify(logger).trace("Failed to perform JSON match \"{}\" with \"{}\" because {}", "illegal_json", "illegal_json", "Unrecognized token 'illegal_json': was expecting ('true', 'false' or 'null')\n" +
                " at [Source: illegal_json; line: 1, column: 25]");

        // and
        assertFalse(jsonSchemaMatcher.matches("some_other_illegal_json"));

        // and
        verify(logger).trace("Failed to perform JSON match \"{}\" with \"{}\" because {}", "some_other_illegal_json", "illegal_json", "Unrecognized token 'illegal_json': was expecting ('true', 'false' or 'null')\n" +
                " at [Source: illegal_json; line: 1, column: 25]");
    }

    @Test
    public void shouldNotMatchNullExpectation() {
        // given
        JsonSchemaMatcher jsonSchemaMatcher = new JsonSchemaMatcher(null);
        jsonSchemaMatcher.logger = logger;

        // then
        assertFalse(new JsonSchemaMatcher(null).matches("some_value"));

        // and
        verifyNoMoreInteractions(logger);
    }

    @Test
    public void shouldNotMatchEmptyExpectation() {
        // given
        JsonSchemaMatcher jsonSchemaMatcher = new JsonSchemaMatcher("");
        jsonSchemaMatcher.logger = logger;

        // then
        assertFalse(jsonSchemaMatcher.matches("some_value"));

        // and
        verify(logger).trace("Failed to perform JSON match \"{}\" with \"{}\" because {}", "some_value", "", "No content to map due to end-of-input\n" +
                " at [Source: ; line: 1, column: 0]");
    }

    @Test
    public void shouldNotMatchNullTest() {
        // given
        JsonSchemaMatcher jsonSchemaMatcher = new JsonSchemaMatcher("some_value");
        jsonSchemaMatcher.logger = logger;

        // then
        assertFalse(jsonSchemaMatcher.matches(null));

        // and
        verify(logger).trace("Failed to perform JSON match \"{}\" with \"{}\" because {}", null, "some_value", "Unrecognized token 'some_value': was expecting ('true', 'false' or 'null')\n" +
                " at [Source: some_value; line: 1, column: 21]");
    }

    @Test
    public void shouldNotMatchEmptyTest() {
        // given
        JsonSchemaMatcher jsonSchemaMatcher = new JsonSchemaMatcher("some_value");
        jsonSchemaMatcher.logger = logger;

        // then
        assertFalse(jsonSchemaMatcher.matches(""));

        // and
        verify(logger).trace("Failed to perform JSON match \"{}\" with \"{}\" because {}", "", "some_value", "Unrecognized token 'some_value': was expecting ('true', 'false' or 'null')\n" +
                " at [Source: some_value; line: 1, column: 21]");
    }

    @Test
    public void showHaveCorrectEqualsBehaviour() {
        assertEquals(new JsonSchemaMatcher("some_value"), new JsonSchemaMatcher("some_value"));
    }
}
