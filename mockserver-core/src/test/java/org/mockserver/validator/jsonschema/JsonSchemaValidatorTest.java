package org.mockserver.validator.jsonschema;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.logging.MockServerLogger;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.MockitoAnnotations.openMocks;
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
        openMocks(this);
    }

    @Test
    public void shouldMatchJson() {
        assertThat(new JsonSchemaValidator(mockServerLogger, JSON_SCHEMA).isValid("{arrayField: [ \"one\" ], enumField: \"one\"}"), is(""));
    }

    @Test
    public void shouldHandleJsonMissingRequiredFields() {
        // then
        assertThat(new JsonSchemaValidator(mockServerLogger, JSON_SCHEMA).isValid("{}"), is("2 errors:" + NEW_LINE +
            " - $.arrayField: is missing but it is required" + NEW_LINE +
            " - $.enumField: is missing but it is required" + NEW_LINE +
            NEW_LINE +
            OPEN_API_SPECIFICATION_URL));
    }

    @Test
    public void shouldHandleJsonTooFewItems() {
        // then
        assertThat(new JsonSchemaValidator(mockServerLogger, JSON_SCHEMA).isValid("{arrayField: [ ],         enumField: \\\"one\\\"}"),
            is("JsonParseException - Unexpected character ('\\' (code 92)): expected a valid value (JSON String, Number (or 'NaN'/'INF'/'+INF'), Array, Object or token 'null', 'true' or 'false')" + NEW_LINE +
                " at [Source: (String)\"{arrayField: [ ],         enumField: \\\"one\\\"}\"; line: 1, column: 39]"));
    }

    @Test
    public void shouldHandleJsonTooLongString() {
        assertThat(new JsonSchemaValidator(mockServerLogger, JSON_SCHEMA).isValid("{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", stringField: \\\"1234567\\\"}"),
            is("JsonParseException - Unexpected character ('\\' (code 92)): expected a valid value (JSON String, Number (or 'NaN'/'INF'/'+INF'), Array, Object or token 'null', 'true' or 'false')" + NEW_LINE +
                " at [Source: (String)\"{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", stringField: \\\"1234567\\\"}\"; line: 1, column: 17]"));
    }

    @Test
    public void shouldHandleJsonIncorrectEnum() {
        assertThat(new JsonSchemaValidator(mockServerLogger, JSON_SCHEMA).isValid("{arrayField: [ \\\"one\\\" ], enumField: \\\"four\\\"}"),
            is("JsonParseException - Unexpected character ('\\' (code 92)): expected a valid value (JSON String, Number (or 'NaN'/'INF'/'+INF'), Array, Object or token 'null', 'true' or 'false')" + NEW_LINE +
                " at [Source: (String)\"{arrayField: [ \\\"one\\\" ], enumField: \\\"four\\\"}\"; line: 1, column: 17]"));
    }

    @Test
    public void shouldHandleJsonExtraField() {
        assertThat(new JsonSchemaValidator(mockServerLogger, JSON_SCHEMA).isValid("{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", extra: \\\"field\\\"}"),
            is("JsonParseException - Unexpected character ('\\' (code 92)): expected a valid value (JSON String, Number (or 'NaN'/'INF'/'+INF'), Array, Object or token 'null', 'true' or 'false')" + NEW_LINE +
                " at [Source: (String)\"{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", extra: \\\"field\\\"}\"; line: 1, column: 17]"));
    }

    @Test
    public void shouldHandleJsonIncorrectSubField() {
        assertThat(new JsonSchemaValidator(mockServerLogger, JSON_SCHEMA).isValid("{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", objectField: {stringField: \\\"1234\\\"} }"),
            is("JsonParseException - Unexpected character ('\\' (code 92)): expected a valid value (JSON String, Number (or 'NaN'/'INF'/'+INF'), Array, Object or token 'null', 'true' or 'false')" + NEW_LINE +
                " at [Source: (String)\"{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", objectField: {stringField: \\\"1234\\\"} }\"; line: 1, column: 17]"));
    }

    @Test
    public void shouldHandleJsonMissingSubField() {
        assertThat(new JsonSchemaValidator(mockServerLogger, JSON_SCHEMA).isValid("{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", objectField: { } }"),
            is("JsonParseException - Unexpected character ('\\' (code 92)): expected a valid value (JSON String, Number (or 'NaN'/'INF'/'+INF'), Array, Object or token 'null', 'true' or 'false')" + NEW_LINE +
                " at [Source: (String)\"{arrayField: [ \\\"one\\\" ], enumField: \\\"one\\\", objectField: { } }\"; line: 1, column: 17]"));
    }

    @Test
    public void shouldHandleJsonMultipleErrors() {
        assertThat(new JsonSchemaValidator(mockServerLogger, JSON_SCHEMA).isValid("{arrayField: [ ],  stringField: \\\"1234\\\"}"),
            is("JsonParseException - Unexpected character ('\\' (code 92)): expected a valid value (JSON String, Number (or 'NaN'/'INF'/'+INF'), Array, Object or token 'null', 'true' or 'false')" + NEW_LINE +
                " at [Source: (String)\"{arrayField: [ ],  stringField: \\\"1234\\\"}\"; line: 1, column: 34]"));
    }

    @Test
    public void shouldHandleDraft04JsonExampleWithItems() {
        // given
        String schema = "{" + NEW_LINE +
            "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
            "    \"type\": \"object\", " + NEW_LINE +
            "    \"properties\": {" + NEW_LINE +
            "        \"sessions\": {" + NEW_LINE +
            "            \"type\": \"array\", " + NEW_LINE +
            "            \"items\": {" + NEW_LINE +
            "                \"type\": \"object\", " + NEW_LINE +
            "                \"properties\": {" + NEW_LINE +
            "                    \"sessionId\": {" + NEW_LINE +
            "                        \"type\": \"string\", " + NEW_LINE +
            "                        \"enum\": [" + NEW_LINE +
            "                            \"SESSION_1\"" + NEW_LINE +
            "                        ]" + NEW_LINE +
            "                    }, " + NEW_LINE +
            "                    \"logs\": {" + NEW_LINE +
            "                        \"type\": \"array\", " + NEW_LINE +
            "                        \"items\": {" + NEW_LINE +
            "                            \"type\": \"object\", " + NEW_LINE +
            "                            \"properties\": {" + NEW_LINE +
            "                                \"logType\": {" + NEW_LINE +
            "                                    \"type\": \"number\", " + NEW_LINE +
            "                                    \"enum\": [" + NEW_LINE +
            "                                        0" + NEW_LINE +
            "                                    ]" + NEW_LINE +
            "                                }, " + NEW_LINE +
            "                                \"logName\": {" + NEW_LINE +
            "                                    \"type\": \"string\", " + NEW_LINE +
            "                                    \"enum\": [" + NEW_LINE +
            "                                        \"FALogNameStartSession\"" + NEW_LINE +
            "                                    ]" + NEW_LINE +
            "                                }, " + NEW_LINE +
            "                                \"logUserId\": {" + NEW_LINE +
            "                                    \"type\": \"string\", " + NEW_LINE +
            "                                    \"enum\": [" + NEW_LINE +
            "                                        null" + NEW_LINE +
            "                                    ]" + NEW_LINE +
            "                                }, " + NEW_LINE +
            "                                \"logDetails\": {" + NEW_LINE +
            "                                    \"type\": [" + NEW_LINE +
            "                                        \"string\", " + NEW_LINE +
            "                                        \"object\"" + NEW_LINE +
            "                                    ], " + NEW_LINE +
            "                                    \"enum\": [" + NEW_LINE +
            "                                        null" + NEW_LINE +
            "                                    ]" + NEW_LINE +
            "                                }, " + NEW_LINE +
            "                                \"logDate\": {" + NEW_LINE +
            "                                    \"type\": [" + NEW_LINE +
            "                                        \"string\"" + NEW_LINE +
            "                                    ]" + NEW_LINE +
            "                                }" + NEW_LINE +
            "                            }, " + NEW_LINE +
            "                            \"required\": [" + NEW_LINE +
            "                                \"logType\", " + NEW_LINE +
            "                                \"logName\", " + NEW_LINE +
            "                                \"logDate\"" + NEW_LINE +
            "                            ]" + NEW_LINE +
            "                        }" + NEW_LINE +
            "                    }" + NEW_LINE +
            "                }, " + NEW_LINE +
            "                \"required\": [" + NEW_LINE +
            "                    \"sessionId\", " + NEW_LINE +
            "                    \"logs\"" + NEW_LINE +
            "                ]" + NEW_LINE +
            "            }" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }, " + NEW_LINE +
            "    \"required\": [" + NEW_LINE +
            "        \"sessions\"" + NEW_LINE +
            "    ]" + NEW_LINE +
            "}";
        String json = "{" + NEW_LINE +
            "    \"deviceId\": \"21f5ce37ef664944\", " + NEW_LINE +
            "    \"sdkPlatform\": \"Android\", " + NEW_LINE +
            "    \"sdkVersion\": \"6.7.0\", " + NEW_LINE +
            "    \"requestDate\": \"2020-12-01T11:38:46.404+0100\", " + NEW_LINE +
            "    \"packageName\": \"com.reactnativetestapp\", " + NEW_LINE +
            "    \"sessions\": [" + NEW_LINE +
            "        {" + NEW_LINE +
            "            \"sessionId\": \"SESSION_1\", " + NEW_LINE +
            "            \"logs\": [" + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"logDate\": \"2020-12-01T11:38:46.350+0100\", " + NEW_LINE +
            "                    \"logType\": 0, " + NEW_LINE +
            "                    \"logName\": \"FALogNameStartSession\"" + NEW_LINE +
            "                }" + NEW_LINE +
            "            ]" + NEW_LINE +
            "        }" + NEW_LINE +
            "    ]" + NEW_LINE +
            "}";

        // then
        assertThat(new JsonSchemaValidator(mockServerLogger, schema).isValid(json), is(""));
    }

    @Test
    public void shouldHandleNestedDraft04JsonExampleWithItems() {
        // given
        String schema = "{" + NEW_LINE +
            "    \"type\": \"object\", " + NEW_LINE +
            "    \"properties\": {" + NEW_LINE +
            "        \"sessions\": {" + NEW_LINE +
            "            \"type\": \"array\", " + NEW_LINE +
            "            \"items\": {" + NEW_LINE +
            "                \"type\": \"object\", " + NEW_LINE +
            "                \"properties\": {" + NEW_LINE +
            "                    \"schema\": {" + NEW_LINE +
            "                        \"$ref\": \"http://json-schema.org/draft-04/schema\"" + NEW_LINE +
            "                    }," + NEW_LINE +
            "                    \"sessionId\": {" + NEW_LINE +
            "                        \"type\": \"string\", " + NEW_LINE +
            "                        \"enum\": [" + NEW_LINE +
            "                            \"SESSION_1\"" + NEW_LINE +
            "                        ]" + NEW_LINE +
            "                    }, " + NEW_LINE +
            "                    \"logs\": {" + NEW_LINE +
            "                        \"type\": \"array\", " + NEW_LINE +
            "                        \"items\": {" + NEW_LINE +
            "                            \"type\": \"object\", " + NEW_LINE +
            "                            \"properties\": {" + NEW_LINE +
            "                                \"logType\": {" + NEW_LINE +
            "                                    \"type\": \"number\", " + NEW_LINE +
            "                                    \"enum\": [" + NEW_LINE +
            "                                        0" + NEW_LINE +
            "                                    ]" + NEW_LINE +
            "                                }, " + NEW_LINE +
            "                                \"logName\": {" + NEW_LINE +
            "                                    \"type\": \"string\", " + NEW_LINE +
            "                                    \"enum\": [" + NEW_LINE +
            "                                        \"FALogNameStartSession\"" + NEW_LINE +
            "                                    ]" + NEW_LINE +
            "                                }, " + NEW_LINE +
            "                                \"logUserId\": {" + NEW_LINE +
            "                                    \"type\": \"string\", " + NEW_LINE +
            "                                    \"enum\": [" + NEW_LINE +
            "                                        null" + NEW_LINE +
            "                                    ]" + NEW_LINE +
            "                                }, " + NEW_LINE +
            "                                \"logDetails\": {" + NEW_LINE +
            "                                    \"type\": [" + NEW_LINE +
            "                                        \"string\", " + NEW_LINE +
            "                                        \"object\"" + NEW_LINE +
            "                                    ], " + NEW_LINE +
            "                                    \"enum\": [" + NEW_LINE +
            "                                        null" + NEW_LINE +
            "                                    ]" + NEW_LINE +
            "                                }, " + NEW_LINE +
            "                                \"logDate\": {" + NEW_LINE +
            "                                    \"type\": [" + NEW_LINE +
            "                                        \"string\"" + NEW_LINE +
            "                                    ]" + NEW_LINE +
            "                                }" + NEW_LINE +
            "                            }, " + NEW_LINE +
            "                            \"required\": [" + NEW_LINE +
            "                                \"logType\", " + NEW_LINE +
            "                                \"logName\", " + NEW_LINE +
            "                                \"logDate\"" + NEW_LINE +
            "                            ]" + NEW_LINE +
            "                        }" + NEW_LINE +
            "                    }" + NEW_LINE +
            "                }, " + NEW_LINE +
            "                \"required\": [" + NEW_LINE +
            "                    \"sessionId\", " + NEW_LINE +
            "                    \"logs\"" + NEW_LINE +
            "                ]" + NEW_LINE +
            "            }" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }, " + NEW_LINE +
            "    \"required\": [" + NEW_LINE +
            "        \"sessions\"" + NEW_LINE +
            "    ]" + NEW_LINE +
            "}";
        String json = "{" + NEW_LINE +
            "    \"deviceId\": \"21f5ce37ef664944\", " + NEW_LINE +
            "    \"sdkPlatform\": \"Android\", " + NEW_LINE +
            "    \"sdkVersion\": \"6.7.0\", " + NEW_LINE +
            "    \"requestDate\": \"2020-12-01T11:38:46.404+0100\", " + NEW_LINE +
            "    \"packageName\": \"com.reactnativetestapp\", " + NEW_LINE +
            "    \"sessions\": [" + NEW_LINE +
            "        {" + NEW_LINE +
            "            \"sessionId\": \"SESSION_1\", " + NEW_LINE +
            "            \"logs\": [" + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"logDate\": \"2020-12-01T11:38:46.350+0100\", " + NEW_LINE +
            "                    \"logType\": 0, " + NEW_LINE +
            "                    \"logName\": \"FALogNameStartSession\"" + NEW_LINE +
            "                }" + NEW_LINE +
            "            ]" + NEW_LINE +
            "        }" + NEW_LINE +
            "    ]" + NEW_LINE +
            "}";

        // then
        assertThat(new JsonSchemaValidator(mockServerLogger, schema).isValid(json), is(""));
    }

    @Test
    public void shouldHandleDraft06JsonExampleWithContains() {
        // given
        String schema = "{" + NEW_LINE +
            "    \"$schema\": \"http://json-schema.org/draft-06/schema\"," + NEW_LINE +
            "    \"type\": \"object\", " + NEW_LINE +
            "    \"properties\": {" + NEW_LINE +
            "        \"sessions\": {" + NEW_LINE +
            "            \"type\": \"array\", " + NEW_LINE +
            "            \"contains\": {" + NEW_LINE +
            "                \"type\": \"object\", " + NEW_LINE +
            "                \"properties\": {" + NEW_LINE +
            "                    \"sessionId\": {" + NEW_LINE +
            "                        \"type\": \"string\", " + NEW_LINE +
            "                        \"enum\": [" + NEW_LINE +
            "                            \"SESSION_1\"" + NEW_LINE +
            "                        ]" + NEW_LINE +
            "                    }, " + NEW_LINE +
            "                    \"logs\": {" + NEW_LINE +
            "                        \"type\": \"array\", " + NEW_LINE +
            "                        \"contains\": {" + NEW_LINE +
            "                            \"type\": \"object\", " + NEW_LINE +
            "                            \"properties\": {" + NEW_LINE +
            "                                \"logType\": {" + NEW_LINE +
            "                                    \"type\": \"number\", " + NEW_LINE +
            "                                    \"enum\": [" + NEW_LINE +
            "                                        0" + NEW_LINE +
            "                                    ]" + NEW_LINE +
            "                                }, " + NEW_LINE +
            "                                \"logName\": {" + NEW_LINE +
            "                                    \"type\": \"string\", " + NEW_LINE +
            "                                    \"enum\": [" + NEW_LINE +
            "                                        \"FALogNameStartSession\"" + NEW_LINE +
            "                                    ]" + NEW_LINE +
            "                                }, " + NEW_LINE +
            "                                \"logUserId\": {" + NEW_LINE +
            "                                    \"type\": \"string\", " + NEW_LINE +
            "                                    \"enum\": [" + NEW_LINE +
            "                                        null" + NEW_LINE +
            "                                    ]" + NEW_LINE +
            "                                }, " + NEW_LINE +
            "                                \"logDetails\": {" + NEW_LINE +
            "                                    \"type\": [" + NEW_LINE +
            "                                        \"string\", " + NEW_LINE +
            "                                        \"object\"" + NEW_LINE +
            "                                    ], " + NEW_LINE +
            "                                    \"enum\": [" + NEW_LINE +
            "                                        null" + NEW_LINE +
            "                                    ]" + NEW_LINE +
            "                                }, " + NEW_LINE +
            "                                \"logDate\": {" + NEW_LINE +
            "                                    \"type\": [" + NEW_LINE +
            "                                        \"string\"" + NEW_LINE +
            "                                    ]" + NEW_LINE +
            "                                }" + NEW_LINE +
            "                            }, " + NEW_LINE +
            "                            \"required\": [" + NEW_LINE +
            "                                \"logType\", " + NEW_LINE +
            "                                \"logName\", " + NEW_LINE +
            "                                \"logDate\"" + NEW_LINE +
            "                            ]" + NEW_LINE +
            "                        }" + NEW_LINE +
            "                    }" + NEW_LINE +
            "                }, " + NEW_LINE +
            "                \"required\": [" + NEW_LINE +
            "                    \"sessionId\", " + NEW_LINE +
            "                    \"logs\"" + NEW_LINE +
            "                ]" + NEW_LINE +
            "            }" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }, " + NEW_LINE +
            "    \"required\": [" + NEW_LINE +
            "        \"sessions\"" + NEW_LINE +
            "    ]" + NEW_LINE +
            "}";
        String json = "{" + NEW_LINE +
            "    \"deviceId\": \"21f5ce37ef664944\", " + NEW_LINE +
            "    \"sdkPlatform\": \"Android\", " + NEW_LINE +
            "    \"sdkVersion\": \"6.7.0\", " + NEW_LINE +
            "    \"requestDate\": \"2020-12-01T11:38:46.404+0100\", " + NEW_LINE +
            "    \"packageName\": \"com.reactnativetestapp\", " + NEW_LINE +
            "    \"sessions\": [" + NEW_LINE +
            "        {" + NEW_LINE +
            "            \"sessionId\": \"SESSION_1\", " + NEW_LINE +
            "            \"logs\": [" + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"logDate\": \"2020-12-01T11:38:46.350+0100\", " + NEW_LINE +
            "                    \"logType\": 0, " + NEW_LINE +
            "                    \"logName\": \"FALogNameStartSession\"" + NEW_LINE +
            "                }" + NEW_LINE +
            "            ]" + NEW_LINE +
            "        }" + NEW_LINE +
            "    ]" + NEW_LINE +
            "}";

        // then
        assertThat(new JsonSchemaValidator(mockServerLogger, schema).isValid(json), is(""));
    }

    @Test
    public void shouldHandleDraft201909JsonExampleWithContains() {
        // given
        String schema = "{" + NEW_LINE +
            "    \"$schema\": \"http://json-schema.org/draft/2019-09/schema\"," + NEW_LINE +
            "    \"type\": \"object\", " + NEW_LINE +
            "    \"properties\": {" + NEW_LINE +
            "        \"sessions\": {" + NEW_LINE +
            "            \"type\": \"array\", " + NEW_LINE +
            "            \"contains\": {" + NEW_LINE +
            "                \"type\": \"object\", " + NEW_LINE +
            "                \"properties\": {" + NEW_LINE +
            "                    \"sessionId\": {" + NEW_LINE +
            "                        \"type\": \"string\", " + NEW_LINE +
            "                        \"enum\": [" + NEW_LINE +
            "                            \"SESSION_1\"" + NEW_LINE +
            "                        ]" + NEW_LINE +
            "                    }, " + NEW_LINE +
            "                    \"logs\": {" + NEW_LINE +
            "                        \"type\": \"array\", " + NEW_LINE +
            "                        \"contains\": {" + NEW_LINE +
            "                            \"type\": \"object\", " + NEW_LINE +
            "                            \"properties\": {" + NEW_LINE +
            "                                \"logType\": {" + NEW_LINE +
            "                                    \"type\": \"number\", " + NEW_LINE +
            "                                    \"enum\": [" + NEW_LINE +
            "                                        0" + NEW_LINE +
            "                                    ]" + NEW_LINE +
            "                                }, " + NEW_LINE +
            "                                \"logName\": {" + NEW_LINE +
            "                                    \"type\": \"string\", " + NEW_LINE +
            "                                    \"enum\": [" + NEW_LINE +
            "                                        \"FALogNameStartSession\"" + NEW_LINE +
            "                                    ]" + NEW_LINE +
            "                                }, " + NEW_LINE +
            "                                \"logUserId\": {" + NEW_LINE +
            "                                    \"type\": \"string\", " + NEW_LINE +
            "                                    \"enum\": [" + NEW_LINE +
            "                                        null" + NEW_LINE +
            "                                    ]" + NEW_LINE +
            "                                }, " + NEW_LINE +
            "                                \"logDetails\": {" + NEW_LINE +
            "                                    \"type\": [" + NEW_LINE +
            "                                        \"string\", " + NEW_LINE +
            "                                        \"object\"" + NEW_LINE +
            "                                    ], " + NEW_LINE +
            "                                    \"enum\": [" + NEW_LINE +
            "                                        null" + NEW_LINE +
            "                                    ]" + NEW_LINE +
            "                                }, " + NEW_LINE +
            "                                \"logDate\": {" + NEW_LINE +
            "                                    \"type\": [" + NEW_LINE +
            "                                        \"string\"" + NEW_LINE +
            "                                    ]" + NEW_LINE +
            "                                }" + NEW_LINE +
            "                            }, " + NEW_LINE +
            "                            \"required\": [" + NEW_LINE +
            "                                \"logType\", " + NEW_LINE +
            "                                \"logName\", " + NEW_LINE +
            "                                \"logDate\"" + NEW_LINE +
            "                            ]" + NEW_LINE +
            "                        }" + NEW_LINE +
            "                    }" + NEW_LINE +
            "                }, " + NEW_LINE +
            "                \"required\": [" + NEW_LINE +
            "                    \"sessionId\", " + NEW_LINE +
            "                    \"logs\"" + NEW_LINE +
            "                ]" + NEW_LINE +
            "            }," + NEW_LINE +
            "            \"minContains\": 1," + NEW_LINE +
            "            \"maxContains\": 3" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }, " + NEW_LINE +
            "    \"required\": [" + NEW_LINE +
            "        \"sessions\"" + NEW_LINE +
            "    ]" + NEW_LINE +
            "}";
        String json = "{" + NEW_LINE +
            "    \"deviceId\": \"21f5ce37ef664944\", " + NEW_LINE +
            "    \"sdkPlatform\": \"Android\", " + NEW_LINE +
            "    \"sdkVersion\": \"6.7.0\", " + NEW_LINE +
            "    \"requestDate\": \"2020-12-01T11:38:46.404+0100\", " + NEW_LINE +
            "    \"packageName\": \"com.reactnativetestapp\", " + NEW_LINE +
            "    \"sessions\": [" + NEW_LINE +
            "        {" + NEW_LINE +
            "            \"sessionId\": \"SESSION_1\", " + NEW_LINE +
            "            \"logs\": [" + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"logDate\": \"2020-12-01T11:38:46.350+0100\", " + NEW_LINE +
            "                    \"logType\": 0, " + NEW_LINE +
            "                    \"logName\": \"FALogNameStartSession\"" + NEW_LINE +
            "                }" + NEW_LINE +
            "            ]" + NEW_LINE +
            "        }" + NEW_LINE +
            "    ]" + NEW_LINE +
            "}";

        // then
        assertThat(new JsonSchemaValidator(mockServerLogger, schema).isValid(json), is(""));
    }

    @Test
    public void shouldHandleNestedDraft201909JsonExampleWithContains() {
        // given
        String schema = "{" + NEW_LINE +
            "  \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
            "  \"title\": \"string value that can be support nottable, optional or a json schema\"," + NEW_LINE +
            "  \"oneOf\": [" + NEW_LINE +
            "    {" + NEW_LINE +
            "      \"type\": \"string\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    {" + NEW_LINE +
            "      \"type\": \"object\"," + NEW_LINE +
            "      \"additionalProperties\": false," + NEW_LINE +
            "      \"properties\": {" + NEW_LINE +
            "        \"not\": {" + NEW_LINE +
            "          \"type\": \"boolean\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"optional\": {" + NEW_LINE +
            "          \"type\": \"boolean\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"value\": {" + NEW_LINE +
            "          \"type\": \"string\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"schema\": {" + NEW_LINE +
            "          \"$ref\": \"http://json-schema.org/draft/2019-09/schema\"" + NEW_LINE +
            "        }," + NEW_LINE +
            "        \"parameterStyle\": {" + NEW_LINE +
            "          \"type\": \"string\"," + NEW_LINE +
            "          \"enum\": [" + NEW_LINE +
            "            \"SIMPLE\"," + NEW_LINE +
            "            \"SIMPLE_EXPLODED\"," + NEW_LINE +
            "            \"LABEL\"," + NEW_LINE +
            "            \"LABEL_EXPLODED\"," + NEW_LINE +
            "            \"MATRIX\"," + NEW_LINE +
            "            \"MATRIX_EXPLODED\"," + NEW_LINE +
            "            \"FORM_EXPLODED\"," + NEW_LINE +
            "            \"FORM\"," + NEW_LINE +
            "            \"SPACE_DELIMITED_EXPLODED\"," + NEW_LINE +
            "            \"SPACE_DELIMITED\"," + NEW_LINE +
            "            \"PIPE_DELIMITED_EXPLODED\"," + NEW_LINE +
            "            \"PIPE_DELIMITED\"," + NEW_LINE +
            "            \"DEEP_OBJECT\"" + NEW_LINE +
            "          ]," + NEW_LINE +
            "          \"default\": \"\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }" + NEW_LINE +
            "  ]," + NEW_LINE +
            "  \"definitions\": {" + NEW_LINE +
            "  }" + NEW_LINE +
            "}" + NEW_LINE;
        String json = "\"some_string\"";

        // then
        assertThat(new JsonSchemaValidator(mockServerLogger, schema).isValid(json), is(""));
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
