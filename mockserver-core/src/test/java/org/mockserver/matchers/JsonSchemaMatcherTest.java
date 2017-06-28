package org.mockserver.matchers;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.slf4j.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class JsonSchemaMatcherTest {

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
                "{" + NEW_LINE +
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
                        "}",
                "com.github.fge.jsonschema.core.report.ListProcessingReport: failure" + NEW_LINE +
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
        verify(logger).trace("Failed to perform JSON match \"{}\" with schema \"{}\" because {}",
                "{arrayField: [ ],         enumField: \\\"one\\\"}",
                "{" + NEW_LINE +
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
                        "}",
                "JsonParseException - Unexpected character ('\\' (code 92)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')" + NEW_LINE +
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
        verify(logger).trace("Failed to perform JSON match \"{}\" with schema \"{}\" because {}",
                "{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", stringField: \\\"1234567\\\"}",
                "{" + NEW_LINE +
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
                        "}",
                "JsonParseException - Unexpected character ('\\' (code 92)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')" + NEW_LINE +
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
        verify(logger).trace("Failed to perform JSON match \"{}\" with schema \"{}\" because {}", "{arrayField: [ \\\"one\\\" ], enumField: \\\"four\\\"}", "{" + NEW_LINE +
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
                        "}",
                "JsonParseException - Unexpected character ('\\' (code 92)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')" + NEW_LINE +
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
        verify(logger).trace("Failed to perform JSON match \"{}\" with schema \"{}\" because {}",
                "{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", extra: \\\"field\\\"}",
                "{" + NEW_LINE +
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
                        "}",
                "JsonParseException - Unexpected character ('\\' (code 92)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')" + NEW_LINE +
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
                "Failed to perform JSON match \"{}\" with schema \"{}\" because {}",
                "{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", objectField: {stringField: \\\"1234\\\"} }",
                "{" + NEW_LINE +
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
                        "}",
                "JsonParseException - Unexpected character ('\\' (code 92)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')" + NEW_LINE +
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
        verify(logger).trace("Failed to perform JSON match \"{}\" with schema \"{}\" because {}",
                "{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", objectField: { } }",
                "{" + NEW_LINE +
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
                        "}",
                "JsonParseException - Unexpected character ('\\' (code 92)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')" + NEW_LINE +
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
        verify(logger).trace("Failed to perform JSON match \"{}\" with schema \"{}\" because {}",
                "{arrayField: [ ],  stringField: \\\"1234\\\"}",
                "{" + NEW_LINE +
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
                        "}",
                "JsonParseException - Unexpected character ('\\' (code 92)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')" + NEW_LINE +
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
        verify(logger).trace("Failed to perform JSON match \"{}\" with schema \"{}\" because {}", "illegal_json", "illegal_json", "JsonParseException - Unrecognized token 'illegal_json': was expecting ('true', 'false' or 'null')" + NEW_LINE +
                " at [Source: illegal_json; line: 1, column: 25]");

        // and
        assertFalse(jsonSchemaMatcher.matches("some_other_illegal_json"));

        // and
        verify(logger).trace("Failed to perform JSON match \"{}\" with schema \"{}\" because {}", "some_other_illegal_json", "illegal_json", "JsonParseException - Unrecognized token 'illegal_json': was expecting ('true', 'false' or 'null')" + NEW_LINE +
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
        verify(logger).trace("Failed to perform JSON match \"{}\" with schema \"{}\" because {}", "some_value", "", "JsonMappingException - No content to map due to end-of-input" + NEW_LINE +
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
        verify(logger).trace("Failed to perform JSON match \"{}\" with schema \"{}\" because {}", null, "some_value", "JsonParseException - Unrecognized token 'some_value': was expecting ('true', 'false' or 'null')" + NEW_LINE +
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
        verify(logger).trace("Failed to perform JSON match \"{}\" with schema \"{}\" because {}", "", "some_value", "JsonParseException - Unrecognized token 'some_value': was expecting ('true', 'false' or 'null')" + NEW_LINE +
                " at [Source: some_value; line: 1, column: 21]");
    }

    @Test
    public void showHaveCorrectEqualsBehaviour() {
        assertEquals(new JsonSchemaMatcher("some_value"), new JsonSchemaMatcher("some_value"));
    }
}
