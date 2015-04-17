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
                "{" + System.getProperty("line.separator") +
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
                        "}",
                "com.github.fge.jsonschema.core.report.ListProcessingReport: failure" + System.getProperty("line.separator") +
                        "--- BEGIN MESSAGES ---" + System.getProperty("line.separator") +
                        "error: object has missing required properties ([\"arrayField\",\"enumField\"])" + System.getProperty("line.separator") +
                        "    level: \"error\"" + System.getProperty("line.separator") +
                        "    schema: {\"loadingURI\":\"#\",\"pointer\":\"\"}" + System.getProperty("line.separator") +
                        "    instance: {\"pointer\":\"\"}" + System.getProperty("line.separator") +
                        "    domain: \"validation\"" + System.getProperty("line.separator") +
                        "    keyword: \"required\"" + System.getProperty("line.separator") +
                        "    required: [\"arrayField\",\"enumField\"]" + System.getProperty("line.separator") +
                        "    missing: [\"arrayField\",\"enumField\"]" + System.getProperty("line.separator") +
                        "---  END MESSAGES  ---" + System.getProperty("line.separator") +
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
                "{" + System.getProperty("line.separator") +
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
                        "}",
                "Unexpected character ('\\' (code 92)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')" + System.getProperty("line.separator") +
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
                "{" + System.getProperty("line.separator") +
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
                        "}",
                "Unexpected character ('\\' (code 92)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')" + System.getProperty("line.separator") +
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
        verify(logger).trace("Failed to perform JSON match \"{}\" with \"{}\" because {}", "{arrayField: [ \\\"one\\\" ], enumField: \\\"four\\\"}", "{" + System.getProperty("line.separator") +
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
                        "}",
                "Unexpected character ('\\' (code 92)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')" + System.getProperty("line.separator") +
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
                "{" + System.getProperty("line.separator") +
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
                        "}",
                "Unexpected character ('\\' (code 92)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')" + System.getProperty("line.separator") +
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
                "{" + System.getProperty("line.separator") +
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
                        "}",
                "Unexpected character ('\\' (code 92)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')" + System.getProperty("line.separator") +
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
                "{" + System.getProperty("line.separator") +
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
                        "}",
                "Unexpected character ('\\' (code 92)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')" + System.getProperty("line.separator") +
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
                "{" + System.getProperty("line.separator") +
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
                        "}",
                "Unexpected character ('\\' (code 92)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')" + System.getProperty("line.separator") +
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
        verify(logger).trace("Failed to perform JSON match \"{}\" with \"{}\" because {}", "illegal_json", "illegal_json", "Unrecognized token 'illegal_json': was expecting ('true', 'false' or 'null')" + System.getProperty("line.separator") +
                " at [Source: illegal_json; line: 1, column: 25]");

        // and
        assertFalse(jsonSchemaMatcher.matches("some_other_illegal_json"));

        // and
        verify(logger).trace("Failed to perform JSON match \"{}\" with \"{}\" because {}", "some_other_illegal_json", "illegal_json", "Unrecognized token 'illegal_json': was expecting ('true', 'false' or 'null')" + System.getProperty("line.separator") +
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
        verify(logger).trace("Failed to perform JSON match \"{}\" with \"{}\" because {}", "some_value", "", "No content to map due to end-of-input" + System.getProperty("line.separator") +
                " at [Source: ; line: 1, column: 1]");
    }

    @Test
    public void shouldNotMatchNullTest() {
        // given
        JsonSchemaMatcher jsonSchemaMatcher = new JsonSchemaMatcher("some_value");
        jsonSchemaMatcher.logger = logger;

        // then
        assertFalse(jsonSchemaMatcher.matches(null));

        // and
        verify(logger).trace("Failed to perform JSON match \"{}\" with \"{}\" because {}", null, "some_value", "Unrecognized token 'some_value': was expecting ('true', 'false' or 'null')" + System.getProperty("line.separator") +
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
        verify(logger).trace("Failed to perform JSON match \"{}\" with \"{}\" because {}", "", "some_value", "Unrecognized token 'some_value': was expecting ('true', 'false' or 'null')" + System.getProperty("line.separator") +
                " at [Source: some_value; line: 1, column: 21]");
    }

    @Test
    public void showHaveCorrectEqualsBehaviour() {
        assertEquals(new JsonSchemaMatcher("some_value"), new JsonSchemaMatcher("some_value"));
    }
}
