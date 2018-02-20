package org.mockserver.matchers;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.validator.jsonschema.JsonSchemaValidator;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.configuration.ConfigurationProperties.logLevel;

/**
 * @author jamesdbloom
 */
public class JsonSchemaMatcherTest {

    private static boolean disableSystemOut;

    @BeforeClass
    public static void recordeSystemProperties() {
        disableSystemOut = ConfigurationProperties.disableSystemOut();
        ConfigurationProperties.disableSystemOut(false);
    }

    @AfterClass
    public static void resetSystemProperties() {
        ConfigurationProperties.disableSystemOut(disableSystemOut);
    }

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
    protected Logger logger;
    @Mock
    private JsonSchemaValidator mockJsonSchemaValidator;
    @InjectMocks
    private JsonSchemaMatcher jsonSchemaMatcher;

    @Before
    public void setupMocks() {
        logger = mock(Logger.class);
        jsonSchemaMatcher = new JsonSchemaMatcher(new MockServerLogger(logger, null), JSON_SCHEMA);
        initMocks(this);

        when(logger.isTraceEnabled()).thenReturn(true);
        when(logger.isInfoEnabled()).thenReturn(true);
        when(logger.isErrorEnabled()).thenReturn(true);
    }

    @Test
    public void shouldMatchJson() {
        // given
        String json = "some_json";
        when(mockJsonSchemaValidator.isValid(json)).thenReturn("");

        // then
        assertTrue(jsonSchemaMatcher.matches(null, json));
    }

    @Test
    public void shouldNotMatchJson() {
        Level originalLevel = logLevel();
        try {
            // given
            logLevel("TRACE");
            String json = "some_json";
            when(mockJsonSchemaValidator.isValid(json)).thenReturn("validator_error");

            // when
            assertFalse(jsonSchemaMatcher.matches(null, json));

            // then
            verify(logger).trace("Failed to match JSON: " + NEW_LINE +
                NEW_LINE +
                "\tsome_json" + NEW_LINE +
                NEW_LINE +
                "with schema: " + NEW_LINE +
                NEW_LINE +
                "\t{" + NEW_LINE +
                "\t    \"type\": \"object\"," + NEW_LINE +
                "\t    \"properties\": {" + NEW_LINE +
                "\t        \"enumField\": {" + NEW_LINE +
                "\t            \"enum\": [ \"one\", \"two\" ]" + NEW_LINE +
                "\t        }," + NEW_LINE +
                "\t        \"arrayField\": {" + NEW_LINE +
                "\t            \"type\": \"array\"," + NEW_LINE +
                "\t            \"minItems\": 1," + NEW_LINE +
                "\t            \"items\": {" + NEW_LINE +
                "\t                \"type\": \"string\"" + NEW_LINE +
                "\t            }," + NEW_LINE +
                "\t            \"uniqueItems\": true" + NEW_LINE +
                "\t        }," + NEW_LINE +
                "\t        \"stringField\": {" + NEW_LINE +
                "\t            \"type\": \"string\"," + NEW_LINE +
                "\t            \"minLength\": 5," + NEW_LINE +
                "\t            \"maxLength\": 6" + NEW_LINE +
                "\t        }," + NEW_LINE +
                "\t        \"booleanField\": {" + NEW_LINE +
                "\t            \"type\": \"boolean\"" + NEW_LINE +
                "\t        }," + NEW_LINE +
                "\t        \"objectField\": {" + NEW_LINE +
                "\t            \"type\": \"object\"," + NEW_LINE +
                "\t            \"properties\": {" + NEW_LINE +
                "\t                \"stringField\": {" + NEW_LINE +
                "\t                    \"type\": \"string\"," + NEW_LINE +
                "\t                    \"minLength\": 1," + NEW_LINE +
                "\t                    \"maxLength\": 3" + NEW_LINE +
                "\t                }" + NEW_LINE +
                "\t            }," + NEW_LINE +
                "\t            \"required\": [ \"stringField\" ]" + NEW_LINE +
                "\t        }" + NEW_LINE +
                "\t    }," + NEW_LINE +
                "\t    \"additionalProperties\" : false," + NEW_LINE +
                "\t    \"required\": [ \"enumField\", \"arrayField\" ]" + NEW_LINE +
                "\t}" + NEW_LINE +
                NEW_LINE +
                "because: " + NEW_LINE +
                NEW_LINE +
                "\tvalidator_error" + NEW_LINE);
        } finally {
            logLevel(originalLevel.toString());
        }
    }

    @Test
    public void shouldHandleExpection() {
        Level originalLevel = logLevel();
        try {
            // given
            logLevel("TRACE");
            String json = "some_json";
            when(mockJsonSchemaValidator.isValid(json)).thenThrow(new RuntimeException("TEST_EXCEPTION"));

            // when
            assertFalse(jsonSchemaMatcher.matches(null, json));

            // then
            verify(logger).trace("Failed to match JSON: " + NEW_LINE +
                NEW_LINE +
                "\tsome_json" + NEW_LINE +
                NEW_LINE +
                "with schema: " + NEW_LINE +
                NEW_LINE +
                "\t{" + NEW_LINE +
                "\t    \"type\": \"object\"," + NEW_LINE +
                "\t    \"properties\": {" + NEW_LINE +
                "\t        \"enumField\": {" + NEW_LINE +
                "\t            \"enum\": [ \"one\", \"two\" ]" + NEW_LINE +
                "\t        }," + NEW_LINE +
                "\t        \"arrayField\": {" + NEW_LINE +
                "\t            \"type\": \"array\"," + NEW_LINE +
                "\t            \"minItems\": 1," + NEW_LINE +
                "\t            \"items\": {" + NEW_LINE +
                "\t                \"type\": \"string\"" + NEW_LINE +
                "\t            }," + NEW_LINE +
                "\t            \"uniqueItems\": true" + NEW_LINE +
                "\t        }," + NEW_LINE +
                "\t        \"stringField\": {" + NEW_LINE +
                "\t            \"type\": \"string\"," + NEW_LINE +
                "\t            \"minLength\": 5," + NEW_LINE +
                "\t            \"maxLength\": 6" + NEW_LINE +
                "\t        }," + NEW_LINE +
                "\t        \"booleanField\": {" + NEW_LINE +
                "\t            \"type\": \"boolean\"" + NEW_LINE +
                "\t        }," + NEW_LINE +
                "\t        \"objectField\": {" + NEW_LINE +
                "\t            \"type\": \"object\"," + NEW_LINE +
                "\t            \"properties\": {" + NEW_LINE +
                "\t                \"stringField\": {" + NEW_LINE +
                "\t                    \"type\": \"string\"," + NEW_LINE +
                "\t                    \"minLength\": 1," + NEW_LINE +
                "\t                    \"maxLength\": 3" + NEW_LINE +
                "\t                }" + NEW_LINE +
                "\t            }," + NEW_LINE +
                "\t            \"required\": [ \"stringField\" ]" + NEW_LINE +
                "\t        }" + NEW_LINE +
                "\t    }," + NEW_LINE +
                "\t    \"additionalProperties\" : false," + NEW_LINE +
                "\t    \"required\": [ \"enumField\", \"arrayField\" ]" + NEW_LINE +
                "\t}" + NEW_LINE +
                NEW_LINE +
                "because: " + NEW_LINE +
                NEW_LINE +
                "\tTEST_EXCEPTION" + NEW_LINE);
        } finally {
            logLevel(originalLevel.toString());
        }
    }

    @Test
    public void showHaveCorrectEqualsBehaviour() {
        MockServerLogger mockServerLogger = new MockServerLogger();
        assertEquals(new JsonSchemaMatcher(mockServerLogger, JSON_SCHEMA), new JsonSchemaMatcher(mockServerLogger, JSON_SCHEMA));
    }
}
