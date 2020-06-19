package org.mockserver.validator.jsonschema;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
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
                "9 errors:" + NEW_LINE +
                    " - field: \"/body\" for schema: \"bodyWithContentType\" has error: \" a plain string, JSON object or one of the following example bodies must be specified " + NEW_LINE +
                    "   {" + NEW_LINE +
                    "     \"type\": \"BINARY\"," + NEW_LINE +
                    "     \"base64Bytes\": \"\"," + NEW_LINE +
                    "     \"contentType\": \"\"" + NEW_LINE +
                    "   }, " + NEW_LINE +
                    "   {" + NEW_LINE +
                    "     \"type\": \"JSON\"," + NEW_LINE +
                    "     \"json\": \"\"," + NEW_LINE +
                    "     \"contentType\": \"\"" + NEW_LINE +
                    "   }," + NEW_LINE +
                    "   {" + NEW_LINE +
                    "     \"type\": \"PARAMETERS\"," + NEW_LINE +
                    "     \"parameters\": {\"name\": \"value\"}" + NEW_LINE +
                    "   }," + NEW_LINE +
                    "   {" + NEW_LINE +
                    "     \"type\": \"STRING\"," + NEW_LINE +
                    "     \"string\": \"\"" + NEW_LINE +
                    "   }," + NEW_LINE +
                    "   {" + NEW_LINE +
                    "     \"type\": \"XML\"," + NEW_LINE +
                    "     \"xml\": \"\"," + NEW_LINE +
                    "     \"contentType\": \"\"" + NEW_LINE +
                    "   }" + NEW_LINE +
                    NEW_LINE +
                    " - field: \"/body\" for schema: \"bodyWithContentType/anyOf/0\" has error: \"instance type (integer) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
                    " - field: \"/body\" for schema: \"bodyWithContentType/anyOf/1\" has error: \"instance type (integer) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
                    " - field: \"/body\" for schema: \"bodyWithContentType/anyOf/2\" has error: \"instance type (integer) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
                    " - field: \"/body\" for schema: \"bodyWithContentType/anyOf/3\" has error: \"instance type (integer) does not match any allowed primitive type (allowed: [\"array\"])\"" + NEW_LINE +
                    " - field: \"/body\" for schema: \"bodyWithContentType/anyOf/4\" has error: \"instance type (integer) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
                    " - field: \"/body\" for schema: \"bodyWithContentType/anyOf/5\" has error: \"instance type (integer) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
                    " - field: \"/body\" for schema: \"bodyWithContentType/anyOf/6\" has error: \"instance type (integer) does not match any allowed primitive type (allowed: [\"string\"])\"" + NEW_LINE +
                    " - field: \"/body\" for schema: \"bodyWithContentType/anyOf/7\" has error: \"instance type (integer) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
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
                    " - object instance has properties which are not allowed by the schema: [\"invalidField\"]" + NEW_LINE +
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
                "10 errors:" + NEW_LINE +
                    " - field: \"/body\" for schema: \"bodyWithContentType\" has error: \" a plain string, JSON object or one of the following example bodies must be specified " + NEW_LINE +
                    "   {" + NEW_LINE +
                    "     \"type\": \"BINARY\"," + NEW_LINE +
                    "     \"base64Bytes\": \"\"," + NEW_LINE +
                    "     \"contentType\": \"\"" + NEW_LINE +
                    "   }, " + NEW_LINE +
                    "   {" + NEW_LINE +
                    "     \"type\": \"JSON\"," + NEW_LINE +
                    "     \"json\": \"\"," + NEW_LINE +
                    "     \"contentType\": \"\"" + NEW_LINE +
                    "   }," + NEW_LINE +
                    "   {" + NEW_LINE +
                    "     \"type\": \"PARAMETERS\"," + NEW_LINE +
                    "     \"parameters\": {\"name\": \"value\"}" + NEW_LINE +
                    "   }," + NEW_LINE +
                    "   {" + NEW_LINE +
                    "     \"type\": \"STRING\"," + NEW_LINE +
                    "     \"string\": \"\"" + NEW_LINE +
                    "   }," + NEW_LINE +
                    "   {" + NEW_LINE +
                    "     \"type\": \"XML\"," + NEW_LINE +
                    "     \"xml\": \"\"," + NEW_LINE +
                    "     \"contentType\": \"\"" + NEW_LINE +
                    "   }" + NEW_LINE +
                    NEW_LINE +
                    " - field: \"/body\" for schema: \"bodyWithContentType/anyOf/0\" has error: \"instance type (boolean) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
                    " - field: \"/body\" for schema: \"bodyWithContentType/anyOf/1\" has error: \"instance type (boolean) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
                    " - field: \"/body\" for schema: \"bodyWithContentType/anyOf/2\" has error: \"instance type (boolean) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
                    " - field: \"/body\" for schema: \"bodyWithContentType/anyOf/3\" has error: \"instance type (boolean) does not match any allowed primitive type (allowed: [\"array\"])\"" + NEW_LINE +
                    " - field: \"/body\" for schema: \"bodyWithContentType/anyOf/4\" has error: \"instance type (boolean) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
                    " - field: \"/body\" for schema: \"bodyWithContentType/anyOf/5\" has error: \"instance type (boolean) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
                    " - field: \"/body\" for schema: \"bodyWithContentType/anyOf/6\" has error: \"instance type (boolean) does not match any allowed primitive type (allowed: [\"string\"])\"" + NEW_LINE +
                    " - field: \"/body\" for schema: \"bodyWithContentType/anyOf/7\" has error: \"instance type (boolean) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
                    " - field: \"/statusCode\" for schema: \"/properties/statusCode\" has error: \"instance type (string) does not match any allowed primitive type (allowed: [\"integer\"])\"" + NEW_LINE +
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
            is("4 errors:" + NEW_LINE +
                " - field: \"/headers\" for schema: \"keyToMultiValue\" has error: \" only one of the following example formats is allowed: " + NEW_LINE +
                NEW_LINE +
                "   {" + NEW_LINE +
                "       \"exampleRegexHeader\": [" + NEW_LINE +
                "           \"^some +regex$\"" + NEW_LINE +
                "       ], " + NEW_LINE +
                "       \"exampleNottedAndSimpleStringHeader\": [" + NEW_LINE +
                "           \"!notThisValue\", " + NEW_LINE +
                "           \"simpleStringMatch\"" + NEW_LINE +
                "       ]" + NEW_LINE +
                "   }" + NEW_LINE +
                NEW_LINE +
                "or:" + NEW_LINE +
                NEW_LINE +
                "   {" + NEW_LINE +
                "       \"exampleSchemaHeader\": [" + NEW_LINE +
                "           {" + NEW_LINE +
                "               \"type\": \"number\"" + NEW_LINE +
                "           }" + NEW_LINE +
                "       ], " + NEW_LINE +
                "       \"exampleMultiSchemaHeader\": [" + NEW_LINE +
                "           {" + NEW_LINE +
                "               \"type\": \"string\", " + NEW_LINE +
                "               \"pattern\": \"^some +regex$\"" + NEW_LINE +
                "           }, " + NEW_LINE +
                "           {" + NEW_LINE +
                "               \"type\": \"string\", " + NEW_LINE +
                "               \"format\": \"ipv4\"" + NEW_LINE +
                "           }" + NEW_LINE +
                "       ]" + NEW_LINE +
                "   }" + NEW_LINE +
                NEW_LINE +
                " - field: \"/headers\" for schema: \"keyToMultiValue/oneOf/1\" has error: \"instance type (array) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
                " - field: \"/headers/0\" for schema: \"keyToMultiValue/oneOf/0/items\" has error: \"instance type (string) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
                " - field: \"/headers/1\" for schema: \"keyToMultiValue/oneOf/0/items\" has error: \"instance type (string) does not match any allowed primitive type (allowed: [\"object\"])\"" + NEW_LINE +
                NEW_LINE +
                OPEN_API_SPECIFICATION_URL));
    }


}
