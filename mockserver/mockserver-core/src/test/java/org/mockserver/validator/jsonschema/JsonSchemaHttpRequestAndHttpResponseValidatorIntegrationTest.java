package org.mockserver.validator.jsonschema;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.validator.jsonschema.JsonSchemaHttpRequestAndHttpResponseValidator.jsonSchemaHttpRequestAndHttpResponseValidator;
import static org.mockserver.validator.jsonschema.JsonSchemaValidator.OPEN_API_SPECIFICATION_URL;

/**
 * @author jamesdbloom
 */
public class JsonSchemaHttpRequestAndHttpResponseValidatorIntegrationTest {

    private final JsonSchemaValidator jsonSchemaValidator = jsonSchemaHttpRequestAndHttpResponseValidator(new MockServerLogger());

    private final String completeSerialisedHttpRequestAndHttpResponse = "{" + NEW_LINE +
        "  \"httpRequest\" : {" + NEW_LINE +
        "    \"method\" : \"GET\"," + NEW_LINE +
        "    \"path\" : \"somepath\"," + NEW_LINE +
        "    \"queryStringParameters\" : {" + NEW_LINE +
        "      \"queryStringParameterNameOne\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]," + NEW_LINE +
        "      \"queryStringParameterNameTwo\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
        "    }," + NEW_LINE +
        "    \"headers\" : {" + NEW_LINE +
        "      \"headerName\" : [ \"headerValue\" ]" + NEW_LINE +
        "    }," + NEW_LINE +
        "    \"cookies\" : {" + NEW_LINE +
        "      \"cookieName\" : \"cookieValue\"" + NEW_LINE +
        "    }," + NEW_LINE +
        "    \"secure\" : true," + NEW_LINE +
        "    \"keepAlive\" : true," + NEW_LINE +
        "    \"protocol\" : \"HTTP_2\"," + NEW_LINE +
        "    \"socketAddress\" : {" + NEW_LINE +
        "      \"host\" : \"someHost\"," + NEW_LINE +
        "      \"port\" : 1234," + NEW_LINE +
        "      \"scheme\" : \"HTTPS\"" + NEW_LINE +
        "    }," + NEW_LINE +
        "    \"body\" : \"someBody\"" + NEW_LINE +
        "  }," + NEW_LINE +
        "  \"httpResponse\" : {" + NEW_LINE +
        "    \"statusCode\" : 123," + NEW_LINE +
        "    \"reasonPhrase\" : \"randomPhrase\"," + NEW_LINE +
        "    \"headers\" : {" + NEW_LINE +
        "      \"headerName\" : [ \"headerValue\" ]" + NEW_LINE +
        "    }," + NEW_LINE +
        "    \"cookies\" : {" + NEW_LINE +
        "      \"cookieName\" : \"cookieValue\"" + NEW_LINE +
        "    }," + NEW_LINE +
        "    \"body\" : \"somebody\"," + NEW_LINE +
        "    \"delay\" : {" + NEW_LINE +
        "      \"timeUnit\" : \"MICROSECONDS\"," + NEW_LINE +
        "      \"value\" : 3" + NEW_LINE +
        "    }" + NEW_LINE +
        "  }" + NEW_LINE +
        "}";

    @Test
    public void shouldValidateValidCompleteRequestFromRawJson() {
        // when
        assertThat(jsonSchemaValidator.isValid(completeSerialisedHttpRequestAndHttpResponse), is(""));
    }

    @Test
    public void shouldValidateInvalidBodyType() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"body\" : 1" + NEW_LINE +
                "  }" + NEW_LINE +
                "}"),
            is(
                "1 error:" + NEW_LINE +
                    " - $.httpRequest.body: should match one of its valid types: {" + NEW_LINE +
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
                "  \"invalidField\" : {" + NEW_LINE +
                "    \"type\" : \"STRING\"," + NEW_LINE +
                "    \"value\" : \"someBody\"" + NEW_LINE +
                "  }" + NEW_LINE +
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
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"body\" : 1" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"method\" : 100," + NEW_LINE +
                "  \"path\" : false" + NEW_LINE +
                "  }"),
            is(
                "3 errors:" + NEW_LINE +
                    " - $.httpRequest.body: should match one of its valid types: {" + NEW_LINE +
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
                    " - $.method: is not defined in the schema and the schema does not allow additional properties" + NEW_LINE +
                    " - $.path: is not defined in the schema and the schema does not allow additional properties" + NEW_LINE +
                    NEW_LINE +
                    OPEN_API_SPECIFICATION_URL
            ));
    }

}
