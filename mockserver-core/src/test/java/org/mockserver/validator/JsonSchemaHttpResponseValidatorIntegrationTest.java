package org.mockserver.validator;

import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class JsonSchemaHttpResponseValidatorIntegrationTest {

    // given
    private JsonSchemaValidator jsonSchemaValidator = new JsonSchemaHttpRequestValidator();

    @Test
    public void shouldValidateValidCompleteRequestWithStringBody() {
        // when
        assertThat(jsonSchemaValidator.isValid("{" + NEW_LINE +
                "    \"method\" : \"POST\"," + NEW_LINE +
                "    \"path\" : \"POST\"," + NEW_LINE +
                "    \"queryStringParameters\" : [ {" + NEW_LINE +
                "      \"name\" : \"someParameterName\"," + NEW_LINE +
                "      \"values\" : [ \"someParameterValue\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"body\" : \"someBody\"," + NEW_LINE +
                "    \"cookies\" : [ {" + NEW_LINE +
                "      \"name\" : \"someCookieName\"," + NEW_LINE +
                "      \"value\" : \"someCookieValue\"" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"headers\" : [ {" + NEW_LINE +
                "      \"name\" : \"someHeaderName\"," + NEW_LINE +
                "      \"values\" : [ \"someHeaderValue\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"keepAlive\" : true," + NEW_LINE +
                "    \"secure\" : true" + NEW_LINE +
                "  }"), is(""));
    }

}