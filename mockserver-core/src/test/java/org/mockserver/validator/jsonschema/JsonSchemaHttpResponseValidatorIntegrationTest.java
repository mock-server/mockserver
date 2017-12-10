package org.mockserver.validator.jsonschema;

import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class JsonSchemaHttpResponseValidatorIntegrationTest {

    // given
    private JsonSchemaValidator jsonSchemaValidator = new JsonSchemaHttpResponseValidator();

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
    public void shouldValidateInvalidBodyFields() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
                        "    \"body\" : {" + NEW_LINE +
                        "      \"type\" : \"STRING\"," + NEW_LINE +
                        "      \"value\" : \"someBody\"" + NEW_LINE +
                        "    }" + NEW_LINE +
                        "  }"),
                is(
                        "1 error:" + NEW_LINE +
                                " - for field \"/body\" a plain string or one of the following example bodies must be specified " + NEW_LINE +
                                "   {" + NEW_LINE +
                                "     \"not\": false," + NEW_LINE +
                                "     \"type\": \"BINARY\"," + NEW_LINE +
                                "     \"base64Bytes\": \"\"," + NEW_LINE +
                                "     \"contentType\": \"\"" + NEW_LINE +
                                "   }, " + NEW_LINE +
                                "   {" + NEW_LINE +
                                "     \"not\": false," + NEW_LINE +
                                "     \"type\": \"JSON\"," + NEW_LINE +
                                "     \"json\": \"\"," + NEW_LINE +
                                "     \"contentType\": \"\"," + NEW_LINE +
                                "     \"matchType\": \"ONLY_MATCHING_FIELDS\"" + NEW_LINE +
                                "   }," + NEW_LINE +
                                "   {" + NEW_LINE +
                                "     \"not\": false," + NEW_LINE +
                                "     \"type\": \"JSON_SCHEMA\"," + NEW_LINE +
                                "     \"jsonSchema\": \"\"" + NEW_LINE +
                                "   }," + NEW_LINE +
                                "   {" + NEW_LINE +
                                "     \"not\": false," + NEW_LINE +
                                "     \"type\": \"PARAMETERS\"," + NEW_LINE +
                                "     \"parameters\": \"TO DO\"" + NEW_LINE +
                                "   }," + NEW_LINE +
                                "   {" + NEW_LINE +
                                "     \"not\": false," + NEW_LINE +
                                "     \"type\": \"REGEX\"," + NEW_LINE +
                                "     \"regex\": \"\"" + NEW_LINE +
                                "   }," + NEW_LINE +
                                "   {" + NEW_LINE +
                                "     \"not\": false," + NEW_LINE +
                                "     \"type\": \"STRING\"," + NEW_LINE +
                                "     \"string\": \"\"" + NEW_LINE +
                                "   }," + NEW_LINE +
                                "   {" + NEW_LINE +
                                "     \"not\": false," + NEW_LINE +
                                "     \"type\": \"XML\"," + NEW_LINE +
                                "     \"xml\": \"\"," + NEW_LINE +
                                "     \"contentType\": \"\"" + NEW_LINE +
                                "   }," + NEW_LINE +
                                "   {" + NEW_LINE +
                                "     \"not\": false," + NEW_LINE +
                                "     \"type\": \"XML_SCHEMA\"," + NEW_LINE +
                                "     \"xmlSchema\": \"\"" + NEW_LINE +
                                "   }," + NEW_LINE +
                                "   {" + NEW_LINE +
                                "     \"not\": false," + NEW_LINE +
                                "     \"type\": \"XPATH\"," + NEW_LINE +
                                "     \"xpath\": \"\"" + NEW_LINE +
                                "   }"
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
                                " - object instance has properties which are not allowed by the schema: [\"invalidField\"]"
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
                                " - for field \"/body\" a plain string or one of the following example bodies must be specified " + NEW_LINE +
                                "   {" + NEW_LINE +
                                "     \"not\": false," + NEW_LINE +
                                "     \"type\": \"BINARY\"," + NEW_LINE +
                                "     \"base64Bytes\": \"\"," + NEW_LINE +
                                "     \"contentType\": \"\"" + NEW_LINE +
                                "   }, " + NEW_LINE +
                                "   {" + NEW_LINE +
                                "     \"not\": false," + NEW_LINE +
                                "     \"type\": \"JSON\"," + NEW_LINE +
                                "     \"json\": \"\"," + NEW_LINE +
                                "     \"contentType\": \"\"," + NEW_LINE +
                                "     \"matchType\": \"ONLY_MATCHING_FIELDS\"" + NEW_LINE +
                                "   }," + NEW_LINE +
                                "   {" + NEW_LINE +
                                "     \"not\": false," + NEW_LINE +
                                "     \"type\": \"JSON_SCHEMA\"," + NEW_LINE +
                                "     \"jsonSchema\": \"\"" + NEW_LINE +
                                "   }," + NEW_LINE +
                                "   {" + NEW_LINE +
                                "     \"not\": false," + NEW_LINE +
                                "     \"type\": \"PARAMETERS\"," + NEW_LINE +
                                "     \"parameters\": \"TO DO\"" + NEW_LINE +
                                "   }," + NEW_LINE +
                                "   {" + NEW_LINE +
                                "     \"not\": false," + NEW_LINE +
                                "     \"type\": \"REGEX\"," + NEW_LINE +
                                "     \"regex\": \"\"" + NEW_LINE +
                                "   }," + NEW_LINE +
                                "   {" + NEW_LINE +
                                "     \"not\": false," + NEW_LINE +
                                "     \"type\": \"STRING\"," + NEW_LINE +
                                "     \"string\": \"\"" + NEW_LINE +
                                "   }," + NEW_LINE +
                                "   {" + NEW_LINE +
                                "     \"not\": false," + NEW_LINE +
                                "     \"type\": \"XML\"," + NEW_LINE +
                                "     \"xml\": \"\"," + NEW_LINE +
                                "     \"contentType\": \"\"" + NEW_LINE +
                                "   }," + NEW_LINE +
                                "   {" + NEW_LINE +
                                "     \"not\": false," + NEW_LINE +
                                "     \"type\": \"XML_SCHEMA\"," + NEW_LINE +
                                "     \"xmlSchema\": \"\"" + NEW_LINE +
                                "   }," + NEW_LINE +
                                "   {" + NEW_LINE +
                                "     \"not\": false," + NEW_LINE +
                                "     \"type\": \"XPATH\"," + NEW_LINE +
                                "     \"xpath\": \"\"" + NEW_LINE +
                                "   }" + NEW_LINE +
                                " - instance type (string) does not match any allowed primitive type (allowed: [\"integer\"]) for field \"/statusCode\""
                ));
    }

    @Test
    public void shouldValidateInvalidListItemType() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
                        "    \"headers\" : [ \"invalidValueOne\", \"invalidValueTwo\" ]" + NEW_LINE +
                        "  }"),
                is(
                        "1 error:" + NEW_LINE +
                            " - for field \"/headers\" only one of the following example formats is allowed: " + NEW_LINE +
                            NEW_LINE +
                            "    \"/headers\" : {" + NEW_LINE +
                            "        \"exampleHeaderName\" : [ \"exampleHeaderValue\" ]" + NEW_LINE +
                            "        \"exampleMultiValuedHeaderName\" : [ \"exampleHeaderValueOne\", \"exampleHeaderValueTwo\" ]" + NEW_LINE +
                            "    }" + NEW_LINE +
                            NEW_LINE +
                            "   or:" + NEW_LINE +
                            NEW_LINE +
                            "    \"/headers\" : [" + NEW_LINE +
                            "        {" + NEW_LINE +
                            "            \"name\" : \"exampleHeaderName\"," + NEW_LINE +
                            "            \"values\" : [ \"exampleHeaderValue\" ]" + NEW_LINE +
                            "        }," + NEW_LINE +
                            "        {" + NEW_LINE +
                            "            \"name\" : \"exampleMultiValuedHeaderName\"," + NEW_LINE +
                            "            \"values\" : [ \"exampleHeaderValueOne\", \"exampleHeaderValueTwo\" ]" + NEW_LINE +
                            "        }" + NEW_LINE +
                            "    ]"
                ));
    }


}
