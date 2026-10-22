package org.mockserver.validator.jsonschema;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.core.Is.is;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.validator.jsonschema.JsonSchemaRequestDefinitionValidator.jsonSchemaRequestDefinitionValidator;

/**
 * @author jamesdbloom
 */
public class JsonSchemaRequestDefinitionValidatorTest {

    private final JsonSchemaValidator jsonSchemaValidator = jsonSchemaRequestDefinitionValidator(new MockServerLogger());

    // valid httpRequest inputs

    @Test
    public void shouldValidateValidHttpRequestDefinition() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "    \"method\" : \"GET\"," + NEW_LINE +
            "    \"path\" : \"/somePath\"" + NEW_LINE +
            "  }"), is(""));
    }

    @Test
    public void shouldValidateValidHttpRequestWithBody() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "    \"method\" : \"POST\"," + NEW_LINE +
            "    \"path\" : \"/somePath\"," + NEW_LINE +
            "    \"body\" : {" + NEW_LINE +
            "      \"type\" : \"STRING\"," + NEW_LINE +
            "      \"string\" : \"someBody\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }"), is(""));
    }

    @Test
    public void shouldValidateValidHttpRequestWithHeaders() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "    \"method\" : \"GET\"," + NEW_LINE +
            "    \"path\" : \"/somePath\"," + NEW_LINE +
            "    \"headers\" : [ {" + NEW_LINE +
            "      \"name\" : \"someHeaderName\"," + NEW_LINE +
            "      \"values\" : [ \"someHeaderValue\" ]" + NEW_LINE +
            "    } ]" + NEW_LINE +
            "  }"), is(""));
    }

    @Test
    public void shouldValidateValidHttpRequestWithQueryStringParameters() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "    \"method\" : \"GET\"," + NEW_LINE +
            "    \"path\" : \"/somePath\"," + NEW_LINE +
            "    \"queryStringParameters\" : [ {" + NEW_LINE +
            "      \"name\" : \"paramOne\"," + NEW_LINE +
            "      \"values\" : [ \"valueOne\" ]" + NEW_LINE +
            "    } ]" + NEW_LINE +
            "  }"), is(""));
    }

    @Test
    public void shouldValidateValidHttpRequestWithCookies() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "    \"method\" : \"GET\"," + NEW_LINE +
            "    \"path\" : \"/somePath\"," + NEW_LINE +
            "    \"cookies\" : [ {" + NEW_LINE +
            "      \"name\" : \"someCookieName\"," + NEW_LINE +
            "      \"value\" : \"someCookieValue\"" + NEW_LINE +
            "    } ]" + NEW_LINE +
            "  }"), is(""));
    }

    @Test
    public void shouldValidateValidHttpRequestWithSocketAddress() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "    \"method\" : \"GET\"," + NEW_LINE +
            "    \"path\" : \"/somePath\"," + NEW_LINE +
            "    \"socketAddress\" : {" + NEW_LINE +
            "      \"host\" : \"someHost\"," + NEW_LINE +
            "      \"port\" : 1234," + NEW_LINE +
            "      \"scheme\" : \"HTTPS\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }"), is(""));
    }

    @Test
    public void shouldValidateValidHttpRequestWithProtocol() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "    \"method\" : \"GET\"," + NEW_LINE +
            "    \"path\" : \"/somePath\"," + NEW_LINE +
            "    \"secure\" : true," + NEW_LINE +
            "    \"keepAlive\" : false," + NEW_LINE +
            "    \"protocol\" : \"HTTP_2\"" + NEW_LINE +
            "  }"), is(""));
    }

    @Test
    public void shouldValidateValidEmptyHttpRequest() {
        // when - empty object matches httpRequest (all fields optional)
        assertThat(jsonSchemaValidator.isValid("{}"), is(""));
    }

    @Test
    public void shouldValidateNegatedHttpRequestDefinition() {
        // when - a negated request matcher serializes a top-level "not" : true field
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "    \"not\" : true," + NEW_LINE +
            "    \"method\" : \"GET\"," + NEW_LINE +
            "    \"path\" : \"/somePath\"" + NEW_LINE +
            "  }"), is(""));
    }

    // valid openAPIDefinition inputs

    @Test
    public void shouldValidateValidOpenAPIDefinition() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "    \"specUrlOrPayload\" : \"https://example.com/spec.json\"" + NEW_LINE +
            "  }"), is(""));
    }

    @Test
    public void shouldValidateValidOpenAPIDefinitionWithOperationId() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "    \"specUrlOrPayload\" : \"https://example.com/spec.json\"," + NEW_LINE +
            "    \"operationId\" : \"listPets\"" + NEW_LINE +
            "  }"), is(""));
    }

    @Test
    public void shouldValidateValidOpenAPIDefinitionWithContextPathPrefix() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "    \"specUrlOrPayload\" : \"https://example.com/spec.json\"," + NEW_LINE +
            "    \"operationId\" : \"listPets\"," + NEW_LINE +
            "    \"contextPathPrefix\" : \"/api/v1\"" + NEW_LINE +
            "  }"), is(""));
    }

    @Test
    public void shouldValidateNegatedOpenAPIDefinition() {
        // when - a negated OpenAPI request matcher serializes a top-level "not" : true field
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "    \"not\" : true," + NEW_LINE +
            "    \"specUrlOrPayload\" : \"https://example.com/spec.json\"" + NEW_LINE +
            "  }"), is(""));
    }

    // valid binaryRequestDefinition inputs

    @Test
    public void shouldValidateValidBinaryRequestDefinition() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "    \"binaryData\" : \"c29tZUJpbmFyeURhdGE=\"" + NEW_LINE +
            "  }"), is(""));
    }

    @Test
    public void shouldValidateValidBinaryRequestDefinitionWithSocketAddress() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "    \"binaryData\" : \"c29tZUJpbmFyeURhdGE=\"," + NEW_LINE +
            "    \"socketAddress\" : {" + NEW_LINE +
            "      \"host\" : \"someHost\"," + NEW_LINE +
            "      \"port\" : 8080" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }"), is(""));
    }

    // valid dnsRequestDefinition inputs

    @Test
    public void shouldValidateValidDnsRequestDefinition() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "    \"dnsName\" : \"example.com\"" + NEW_LINE +
            "  }"), is(""));
    }

    @Test
    public void shouldValidateValidDnsRequestDefinitionWithAllFields() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
            "    \"dnsName\" : \"example.com\"," + NEW_LINE +
            "    \"dnsType\" : \"A\"," + NEW_LINE +
            "    \"dnsClass\" : \"IN\"" + NEW_LINE +
            "  }"), is(""));
    }

    // invalid inputs

    @Test
    public void shouldValidateInvalidExtraFieldOnHttpRequest() {
        // when - anyOf schema means binaryData, dnsName, specUrlOrPayload required errors also appear
        // because invalidField doesn't match any of the alternative schemas
        String result = jsonSchemaValidator.isValid("{" + NEW_LINE +
            "    \"method\" : \"GET\"," + NEW_LINE +
            "    \"path\" : \"/somePath\"," + NEW_LINE +
            "    \"invalidField\" : \"invalidValue\"" + NEW_LINE +
            "  }");

        // then
        assertThat(result, startsWith("6 error"));
        assertThat(result, containsString("$.anyOf: is missing"));
        assertThat(result, containsString("$.invalidField: is not defined in the schema and the schema does not allow additional properties"));
        assertThat(result, containsString("$.binaryData: is missing but it is required"));
        assertThat(result, containsString("$.dnsName: is missing but it is required"));
        assertThat(result, containsString("$.specUrlOrPayload: is missing, but is required, if specifying OpenAPI request matcher"));
        assertThat(result, containsString("$.if: is missing but it is required"));
    }

    @Test
    public void shouldValidateInvalidMethodType() {
        // when - anyOf schema means required field errors from other branches also appear
        String result = jsonSchemaValidator.isValid("{" + NEW_LINE +
            "    \"method\" : 100" + NEW_LINE +
            "  }");

        // then
        assertThat(result, startsWith("7 error"));
        assertThat(result, containsString("$.anyOf: is missing"));
        assertThat(result, containsString("$.method: integer found, string expected"));
        assertThat(result, containsString("$.method: should be valid to one and only one schema, but 0 are valid"));
        assertThat(result, containsString("$.binaryData: is missing but it is required"));
        assertThat(result, containsString("$.dnsName: is missing but it is required"));
        assertThat(result, containsString("$.specUrlOrPayload: is missing, but is required, if specifying OpenAPI request matcher"));
        assertThat(result, containsString("$.if: is missing but it is required"));
    }

    @Test
    public void shouldValidateEmptyBody() {
        // when - empty string returns empty (no validation)
        assertThat(jsonSchemaValidator.isValid(""), is(""));
    }

    @Test
    public void shouldValidateMalformedJson() {
        // when
        String result = jsonSchemaValidator.isValid("not json");
        assertThat(result, containsString("JsonParseException"));
    }

}
