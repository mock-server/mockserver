package org.mockserver.validator.jsonschema;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.validator.jsonschema.JsonSchemaHttpResponseValidator.jsonSchemaHttpResponseValidator;
import static org.mockserver.validator.jsonschema.JsonSchemaValidator.OPEN_API_SPECIFICATION_URL;

/**
 * @author jamesdbloom
 */
public class JsonSchemaHttpResponseValidatorIntegrationTest {

    // given
    private final JsonSchemaValidator jsonSchemaValidator = jsonSchemaHttpResponseValidator(new MockServerLogger());

    @Test
    public void shouldValidateValidCompleteRequestWithStringBody() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "    \"statusCode\" : 304," + NEW_LINE +
            "    \"body\" : \"someBody\"," + NEW_LINE +
            "    \"cookies\" : [ {" + NEW_LINE +
            "      \"name\" : \"someCookieName\"," + NEW_LINE +
            "      \"value\" : \"someCookieValue\"" + NEW_LINE +
            "    } ]," + NEW_LINE +
            "    \"headers\" : [ {" + NEW_LINE +
            "      \"name\" : \"someHeaderName\"," + NEW_LINE +
            "      \"values\" : [ \"someHeaderValue\" ]" + NEW_LINE +
            "    } ]," + NEW_LINE +
            "    \"delay\" : {" + NEW_LINE +
            "      \"timeUnit\" : \"MICROSECONDS\"," + NEW_LINE +
            "      \"value\" : 1" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"connectionOptions\" : {" + NEW_LINE +
            "      \"suppressContentLengthHeader\" : true," + NEW_LINE +
            "      \"contentLengthHeaderOverride\" : 50," + NEW_LINE +
            "      \"suppressConnectionHeader\" : true," + NEW_LINE +
            "      \"keepAliveOverride\" : true," + NEW_LINE +
            "      \"closeSocket\" : true" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }"), is(""));
    }

    @Test
    public void shouldValidateValidShortHandJsonObjectBodyType() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
                "    \"body\" : {\"foo\":\"bar\"}" + NEW_LINE +
                "  }"),
            is(""));
    }

    @Test
    public void shouldValidateValidShortHandJsonArrayBodyType() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
                "    \"body\" : [{\"foo\":\"bar\"},{\"bar\":\"foo\"}]" + NEW_LINE +
                "  }"),
            is(""));
    }

    @Test
    public void shouldValidateInvalidBodyType() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
                "    \"body\" : 1" + NEW_LINE +
                "  }"),
            is(
                "1 error:" + NEW_LINE +
                    " - $.body: should match one of its valid types: {" + NEW_LINE +
                    "     \"title\": \"request body matcher\"," + NEW_LINE +
                    "     \"anyOf\": [" + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"object\"," + NEW_LINE +
                    "         \"additionalProperties\": false," + NEW_LINE +
                    "         \"properties\": {" + NEW_LINE +
                    "           \"not\": {" + NEW_LINE +
                    "             \"type\": \"boolean\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"type\": {" + NEW_LINE +
                    "             \"enum\": [" + NEW_LINE +
                    "               \"BINARY\"" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"base64Bytes\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"contentType\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "         }" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"object\"," + NEW_LINE +
                    "         \"additionalProperties\": false," + NEW_LINE +
                    "         \"properties\": {" + NEW_LINE +
                    "           \"not\": {" + NEW_LINE +
                    "             \"type\": \"boolean\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"type\": {" + NEW_LINE +
                    "             \"enum\": [" + NEW_LINE +
                    "               \"JSON\"" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"json\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"contentType\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"matchType\": {" + NEW_LINE +
                    "             \"enum\": [" + NEW_LINE +
                    "               \"STRICT\"," + NEW_LINE +
                    "               \"ONLY_MATCHING_FIELDS\"" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "         }" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"object\"," + NEW_LINE +
                    "         \"additionalProperties\": true" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"array\"," + NEW_LINE +
                    "         \"additionalProperties\": true" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"object\"," + NEW_LINE +
                    "         \"additionalProperties\": false," + NEW_LINE +
                    "         \"properties\": {" + NEW_LINE +
                    "           \"not\": {" + NEW_LINE +
                    "             \"type\": \"boolean\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"type\": {" + NEW_LINE +
                    "             \"enum\": [" + NEW_LINE +
                    "               \"JSON_SCHEMA\"" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"jsonSchema\": {" + NEW_LINE +
                    "             \"oneOf\": [" + NEW_LINE +
                    "               {" + NEW_LINE +
                    "                 \"type\": \"string\"" + NEW_LINE +
                    "               }," + NEW_LINE +
                    "               {" + NEW_LINE +
                    "                 \"$ref\": \"http://json-schema.org/draft-07/schema\"" + NEW_LINE +
                    "               }" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "         }" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"object\"," + NEW_LINE +
                    "         \"additionalProperties\": false," + NEW_LINE +
                    "         \"properties\": {" + NEW_LINE +
                    "           \"not\": {" + NEW_LINE +
                    "             \"type\": \"boolean\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"type\": {" + NEW_LINE +
                    "             \"enum\": [" + NEW_LINE +
                    "               \"JSON_PATH\"" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"jsonPath\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "         }" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"object\"," + NEW_LINE +
                    "         \"additionalProperties\": false," + NEW_LINE +
                    "         \"properties\": {" + NEW_LINE +
                    "           \"not\": {" + NEW_LINE +
                    "             \"type\": \"boolean\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"type\": {" + NEW_LINE +
                    "             \"enum\": [" + NEW_LINE +
                    "               \"PARAMETERS\"" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"parameters\": {" + NEW_LINE +
                    "             \"$ref\": \"#/definitions/keyToMultiValue\"" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "         }" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"object\"," + NEW_LINE +
                    "         \"additionalProperties\": false," + NEW_LINE +
                    "         \"properties\": {" + NEW_LINE +
                    "           \"not\": {" + NEW_LINE +
                    "             \"type\": \"boolean\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"type\": {" + NEW_LINE +
                    "             \"enum\": [" + NEW_LINE +
                    "               \"REGEX\"" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"regex\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "         }" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"object\"," + NEW_LINE +
                    "         \"additionalProperties\": false," + NEW_LINE +
                    "         \"properties\": {" + NEW_LINE +
                    "           \"not\": {" + NEW_LINE +
                    "             \"type\": \"boolean\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"type\": {" + NEW_LINE +
                    "             \"enum\": [" + NEW_LINE +
                    "               \"STRING\"" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"string\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"subString\": {" + NEW_LINE +
                    "             \"type\": \"boolean\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"contentType\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "         }" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"string\"" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"object\"," + NEW_LINE +
                    "         \"additionalProperties\": false," + NEW_LINE +
                    "         \"properties\": {" + NEW_LINE +
                    "           \"not\": {" + NEW_LINE +
                    "             \"type\": \"boolean\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"type\": {" + NEW_LINE +
                    "             \"enum\": [" + NEW_LINE +
                    "               \"XML\"" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"xml\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"contentType\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "         }" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"object\"," + NEW_LINE +
                    "         \"additionalProperties\": false," + NEW_LINE +
                    "         \"properties\": {" + NEW_LINE +
                    "           \"not\": {" + NEW_LINE +
                    "             \"type\": \"boolean\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"type\": {" + NEW_LINE +
                    "             \"enum\": [" + NEW_LINE +
                    "               \"XML_SCHEMA\"" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"xmlSchema\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "         }" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"object\"," + NEW_LINE +
                    "         \"additionalProperties\": false," + NEW_LINE +
                    "         \"properties\": {" + NEW_LINE +
                    "           \"not\": {" + NEW_LINE +
                    "             \"type\": \"boolean\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"type\": {" + NEW_LINE +
                    "             \"enum\": [" + NEW_LINE +
                    "               \"XPATH\"" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"xpath\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "         }" + NEW_LINE +
                    "       }" + NEW_LINE +
                    "     ]" + NEW_LINE +
                    "   }" + NEW_LINE +
                    NEW_LINE +
                    OPEN_API_SPECIFICATION_URL
            ));
    }

    @Test
    public void shouldValidateInvalidExtraField() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
                "    \"invalidField\" : {" + NEW_LINE +
                "      \"type\" : \"STRING\"," + NEW_LINE +
                "      \"value\" : \"someBody\"" + NEW_LINE +
                "    }" + NEW_LINE +
                "  }"),
            is(
                "1 error:" + NEW_LINE +
                    " - $.invalidField: is not defined in the schema and the schema does not allow additional properties" + NEW_LINE +
                    NEW_LINE +
                    OPEN_API_SPECIFICATION_URL
            ));
    }

    @Test
    public void shouldValidateMultipleInvalidFieldTypes() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
                "    \"statusCode\" : \"100\"," + NEW_LINE +
                "    \"body\" : false" + NEW_LINE +
                "  }"),
            is(
                "2 errors:" + NEW_LINE +
                    " - $.body: should match one of its valid types: {" + NEW_LINE +
                    "     \"title\": \"request body matcher\"," + NEW_LINE +
                    "     \"anyOf\": [" + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"object\"," + NEW_LINE +
                    "         \"additionalProperties\": false," + NEW_LINE +
                    "         \"properties\": {" + NEW_LINE +
                    "           \"not\": {" + NEW_LINE +
                    "             \"type\": \"boolean\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"type\": {" + NEW_LINE +
                    "             \"enum\": [" + NEW_LINE +
                    "               \"BINARY\"" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"base64Bytes\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"contentType\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "         }" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"object\"," + NEW_LINE +
                    "         \"additionalProperties\": false," + NEW_LINE +
                    "         \"properties\": {" + NEW_LINE +
                    "           \"not\": {" + NEW_LINE +
                    "             \"type\": \"boolean\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"type\": {" + NEW_LINE +
                    "             \"enum\": [" + NEW_LINE +
                    "               \"JSON\"" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"json\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"contentType\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"matchType\": {" + NEW_LINE +
                    "             \"enum\": [" + NEW_LINE +
                    "               \"STRICT\"," + NEW_LINE +
                    "               \"ONLY_MATCHING_FIELDS\"" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "         }" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"object\"," + NEW_LINE +
                    "         \"additionalProperties\": true" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"array\"," + NEW_LINE +
                    "         \"additionalProperties\": true" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"object\"," + NEW_LINE +
                    "         \"additionalProperties\": false," + NEW_LINE +
                    "         \"properties\": {" + NEW_LINE +
                    "           \"not\": {" + NEW_LINE +
                    "             \"type\": \"boolean\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"type\": {" + NEW_LINE +
                    "             \"enum\": [" + NEW_LINE +
                    "               \"JSON_SCHEMA\"" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"jsonSchema\": {" + NEW_LINE +
                    "             \"oneOf\": [" + NEW_LINE +
                    "               {" + NEW_LINE +
                    "                 \"type\": \"string\"" + NEW_LINE +
                    "               }," + NEW_LINE +
                    "               {" + NEW_LINE +
                    "                 \"$ref\": \"http://json-schema.org/draft-07/schema\"" + NEW_LINE +
                    "               }" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "         }" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"object\"," + NEW_LINE +
                    "         \"additionalProperties\": false," + NEW_LINE +
                    "         \"properties\": {" + NEW_LINE +
                    "           \"not\": {" + NEW_LINE +
                    "             \"type\": \"boolean\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"type\": {" + NEW_LINE +
                    "             \"enum\": [" + NEW_LINE +
                    "               \"JSON_PATH\"" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"jsonPath\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "         }" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"object\"," + NEW_LINE +
                    "         \"additionalProperties\": false," + NEW_LINE +
                    "         \"properties\": {" + NEW_LINE +
                    "           \"not\": {" + NEW_LINE +
                    "             \"type\": \"boolean\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"type\": {" + NEW_LINE +
                    "             \"enum\": [" + NEW_LINE +
                    "               \"PARAMETERS\"" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"parameters\": {" + NEW_LINE +
                    "             \"$ref\": \"#/definitions/keyToMultiValue\"" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "         }" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"object\"," + NEW_LINE +
                    "         \"additionalProperties\": false," + NEW_LINE +
                    "         \"properties\": {" + NEW_LINE +
                    "           \"not\": {" + NEW_LINE +
                    "             \"type\": \"boolean\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"type\": {" + NEW_LINE +
                    "             \"enum\": [" + NEW_LINE +
                    "               \"REGEX\"" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"regex\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "         }" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"object\"," + NEW_LINE +
                    "         \"additionalProperties\": false," + NEW_LINE +
                    "         \"properties\": {" + NEW_LINE +
                    "           \"not\": {" + NEW_LINE +
                    "             \"type\": \"boolean\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"type\": {" + NEW_LINE +
                    "             \"enum\": [" + NEW_LINE +
                    "               \"STRING\"" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"string\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"subString\": {" + NEW_LINE +
                    "             \"type\": \"boolean\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"contentType\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "         }" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"string\"" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"object\"," + NEW_LINE +
                    "         \"additionalProperties\": false," + NEW_LINE +
                    "         \"properties\": {" + NEW_LINE +
                    "           \"not\": {" + NEW_LINE +
                    "             \"type\": \"boolean\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"type\": {" + NEW_LINE +
                    "             \"enum\": [" + NEW_LINE +
                    "               \"XML\"" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"xml\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"contentType\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "         }" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"object\"," + NEW_LINE +
                    "         \"additionalProperties\": false," + NEW_LINE +
                    "         \"properties\": {" + NEW_LINE +
                    "           \"not\": {" + NEW_LINE +
                    "             \"type\": \"boolean\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"type\": {" + NEW_LINE +
                    "             \"enum\": [" + NEW_LINE +
                    "               \"XML_SCHEMA\"" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"xmlSchema\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "         }" + NEW_LINE +
                    "       }," + NEW_LINE +
                    "       {" + NEW_LINE +
                    "         \"type\": \"object\"," + NEW_LINE +
                    "         \"additionalProperties\": false," + NEW_LINE +
                    "         \"properties\": {" + NEW_LINE +
                    "           \"not\": {" + NEW_LINE +
                    "             \"type\": \"boolean\"" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"type\": {" + NEW_LINE +
                    "             \"enum\": [" + NEW_LINE +
                    "               \"XPATH\"" + NEW_LINE +
                    "             ]" + NEW_LINE +
                    "           }," + NEW_LINE +
                    "           \"xpath\": {" + NEW_LINE +
                    "             \"type\": \"string\"" + NEW_LINE +
                    "           }" + NEW_LINE +
                    "         }" + NEW_LINE +
                    "       }" + NEW_LINE +
                    "     ]" + NEW_LINE +
                    "   }" + NEW_LINE +
                    " - $.statusCode: string found, integer expected" + NEW_LINE +
                    NEW_LINE +
                    OPEN_API_SPECIFICATION_URL
            ));
    }

    @Test
    public void shouldValidateInvalidListItemType() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
                "    \"headers\" : [ \"invalidValueOne\", \"invalidValueTwo\" ]" + NEW_LINE +
                "  }"),
            is("3 errors:" + NEW_LINE +
                " - $.headers: array found, object expected" + NEW_LINE +
                " - $.headers[0]: string found, object expected" + NEW_LINE +
                " - $.headers[1]: string found, object expected" + NEW_LINE +
                NEW_LINE +
                OPEN_API_SPECIFICATION_URL));
    }


}
