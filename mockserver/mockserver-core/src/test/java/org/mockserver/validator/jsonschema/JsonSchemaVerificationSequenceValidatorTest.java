package org.mockserver.validator.jsonschema;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.core.Is.is;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.validator.jsonschema.JsonSchemaVerificationSequenceValidator.jsonSchemaVerificationSequenceValidator;

/**
 * @author jamesdbloom
 */
public class JsonSchemaVerificationSequenceValidatorTest {

    private final JsonSchemaValidator jsonSchemaValidator = jsonSchemaVerificationSequenceValidator(new MockServerLogger());

    @Test
    public void shouldValidateValidCompleteRequestWithStringBody() {
        // when
        assertThat(jsonSchemaValidator.isValid("{ \"httpRequests\": [" + NEW_LINE +
            "  {" + NEW_LINE +
            "    \"method\" : \"someMethod\"," + NEW_LINE +
            "    \"path\" : \"somePath\"," + NEW_LINE +
            "    \"queryStringParameters\" : [ {" + NEW_LINE +
            "      \"name\" : \"queryStringParameterNameOne\"," + NEW_LINE +
            "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"name\" : \"queryStringParameterNameTwo\"," + NEW_LINE +
            "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
            "    } ]," + NEW_LINE +
            "    \"body\" : {" + NEW_LINE +
            "      \"type\" : \"STRING\"," + NEW_LINE +
            "      \"string\" : \"someBody\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"cookies\" : [ {" + NEW_LINE +
            "      \"name\" : \"someCookieName\"," + NEW_LINE +
            "      \"value\" : \"someCookieValue\"" + NEW_LINE +
            "    } ]," + NEW_LINE +
            "    \"headers\" : [ {" + NEW_LINE +
            "      \"name\" : \"someHeaderName\"," + NEW_LINE +
            "      \"values\" : [ \"someHeaderValue\" ]" + NEW_LINE +
            "    } ]," + NEW_LINE +
            "    \"secure\" : true," + NEW_LINE +
            "    \"keepAlive\" : false," + NEW_LINE +
            "    \"protocol\" : \"HTTP_2\"," + NEW_LINE +
            "  }," +
            "  {" + NEW_LINE +
            "    \"method\" : \"someMethod\"," + NEW_LINE +
            "    \"path\" : \"somePath\"," + NEW_LINE +
            "    \"queryStringParameters\" : [ {" + NEW_LINE +
            "      \"name\" : \"queryStringParameterNameOne\"," + NEW_LINE +
            "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"name\" : \"queryStringParameterNameTwo\"," + NEW_LINE +
            "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
            "    } ]," + NEW_LINE +
            "    \"body\" : {" + NEW_LINE +
            "      \"type\" : \"STRING\"," + NEW_LINE +
            "      \"string\" : \"someBody\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"cookies\" : [ {" + NEW_LINE +
            "      \"name\" : \"someCookieName\"," + NEW_LINE +
            "      \"value\" : \"someCookieValue\"" + NEW_LINE +
            "    } ]," + NEW_LINE +
            "    \"headers\" : [ {" + NEW_LINE +
            "      \"name\" : \"someHeaderName\"," + NEW_LINE +
            "      \"values\" : [ \"someHeaderValue\" ]" + NEW_LINE +
            "    } ]," + NEW_LINE +
            "    \"secure\" : true," + NEW_LINE +
            "    \"keepAlive\" : false," + NEW_LINE +
            "    \"protocol\" : \"HTTP_2\"," + NEW_LINE +
            "  }" + NEW_LINE +
            "]}"), is(""));
    }

    @Test
    public void shouldValidateInvalidBodyType() {
        // when
        String result = jsonSchemaValidator.isValid("{ \"httpRequests\": [" + NEW_LINE +
            "  {" + NEW_LINE +
            "    \"body\" : 1" + NEW_LINE +
            "  }" + NEW_LINE +
            "]}");

        // then
        assertThat(result, startsWith("7 error"));
        assertThat(result, containsString("$.httpRequests[0].anyOf: is missing"));
        assertThat(result, containsString("$.httpRequests[0].body: should match one of its valid types"));
        assertThat(result, containsString("$.httpRequests[0].binaryData: is missing, but is required"));
        assertThat(result, containsString("$.httpRequests[0].dnsName: is missing, but is required"));
        assertThat(result, containsString("$.httpRequests[0].specUrlOrPayload: is missing, but is required"));
    }

    @Test
    public void shouldValidateInvalidExtraField() {
        // when
        String result = jsonSchemaValidator.isValid("{ \"httpRequests\": [" + NEW_LINE +
            "  {" + NEW_LINE +
            "    \"invalidField\" : {" + NEW_LINE +
            "      \"type\" : \"STRING\"," + NEW_LINE +
            "      \"value\" : \"someBody\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }" + NEW_LINE +
            "]}");

        // then
        assertThat(result, startsWith("7 error"));
        assertThat(result, containsString("$.httpRequests[0].anyOf: is missing"));
        assertThat(result, containsString("$.httpRequests[0].invalidField: is not defined in the schema and the schema does not allow additional properties"));
        assertThat(result, containsString("$.httpRequests[0].binaryData: is missing, but is required"));
        assertThat(result, containsString("$.httpRequests[0].dnsName: is missing, but is required"));
        assertThat(result, containsString("$.httpRequests[0].specUrlOrPayload: is missing, but is required"));
    }

    @Test
    public void shouldValidateMultipleInvalidFieldTypes() {
        // when
        String result = jsonSchemaValidator.isValid("{ \"httpRequests\": [" + NEW_LINE +
            "  {" + NEW_LINE +
            "    \"method\" : 100," + NEW_LINE +
            "    \"path\" : false" + NEW_LINE +
            "  }" + NEW_LINE +
            "]}");

        // then
        assertThat(result, startsWith("10 error"));
        assertThat(result, containsString("$.httpRequests[0].anyOf: is missing"));
        assertThat(result, containsString("$.httpRequests[0].method: integer found, string expected"));
        assertThat(result, containsString("$.httpRequests[0].method: should be valid to one and only one schema, but 0 are valid"));
        assertThat(result, containsString("$.httpRequests[0].path: boolean found, string expected"));
        assertThat(result, containsString("$.httpRequests[0].path: should be valid to one and only one schema, but 0 are valid"));
        assertThat(result, containsString("$.httpRequests[0].binaryData: is missing, but is required"));
        assertThat(result, containsString("$.httpRequests[0].dnsName: is missing, but is required"));
        assertThat(result, containsString("$.httpRequests[0].specUrlOrPayload: is missing, but is required"));
    }

    @Test
    public void shouldValidateInvalidListItemType() {
        // when
        String result = jsonSchemaValidator.isValid("{ \"httpRequests\": [" + NEW_LINE +
            "  {" + NEW_LINE +
            "    \"headers\" : [ \"invalidValueOne\", \"invalidValueTwo\" ]" + NEW_LINE +
            "  }" + NEW_LINE +
            "]}");

        // then
        assertThat(result, startsWith("10 error"));
        assertThat(result, containsString("$.httpRequests[0].anyOf: is missing"));
        assertThat(result, containsString("$.httpRequests[0].headers: array found, object expected"));
        assertThat(result, containsString("$.httpRequests[0].headers: should be valid to one and only one schema, but 0 are valid"));
        assertThat(result, containsString("$.httpRequests[0].headers[0]: string found, object expected"));
        assertThat(result, containsString("$.httpRequests[0].headers[1]: string found, object expected"));
        assertThat(result, containsString("$.httpRequests[0].binaryData: is missing, but is required"));
        assertThat(result, containsString("$.httpRequests[0].dnsName: is missing, but is required"));
        assertThat(result, containsString("$.httpRequests[0].specUrlOrPayload: is missing, but is required"));
    }

}
