package org.mockserver.client;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.uuid.UUIDService;
import org.slf4j.event.Level;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThrows;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.MediaType.TEXT_PLAIN;

/**
 * @author jamesdbloom
 */
public class MockServerClientServerValidationErrorsTest {

    private EchoServer echoServer;
    private MockServerClient mockServerClient;

    @Before
    public void setupTestFixture() {
        echoServer = new EchoServer(false);
        mockServerClient = new MockServerClient("localhost", echoServer.getPort());
    }

    @Test
    public void shouldHandleServerValidationFailure() {
        // given
        String responseBody = "2 errors:" + NEW_LINE +
            " - object instance has properties which are not allowed by the schema: [\"paths\"] for field \"/httpRequest\"" + NEW_LINE +
            " - for field \"/httpRequest/body\" a plain string, JSON object or one of the following example bodies must be specified " + NEW_LINE +
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
            "     \"parameters\": {\"name\": \"value\"}" + NEW_LINE +
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
            "   }";
        echoServer.withNextResponse(response()
                                        .withStatusCode(400)
                                        .withContentType(TEXT_PLAIN)
                                        .withBody(responseBody)
        );

        // when
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> mockServerClient.when(request()).respond(response()));

        // then
        assertThat(illegalArgumentException.getMessage(), containsString(responseBody));
    }

    @Test
    public void shouldHandleOtherClientError() {
        Level originalLevel = ConfigurationProperties.logLevel();
        try {
            // given
            ConfigurationProperties.logLevel("INFO");
            UUIDService.fixedUUID = true;
            String responseBody = "some_random_response";
            echoServer.withNextResponse(response()
                                            .withStatusCode(403)
                                            .withContentType(TEXT_PLAIN)
                                            .withBody(responseBody)
            );

            // when
            ClientException clientException = assertThrows(ClientException.class, () -> mockServerClient.when(request()).respond(response()));

            // then
            assertThat(
                clientException.getMessage(),
                containsString("error:" + NEW_LINE +
                            "" + NEW_LINE +
                            "  {" + NEW_LINE +
                            "    \"statusCode\" : 403," + NEW_LINE +
                            "    \"reasonPhrase\" : \"Forbidden\"," + NEW_LINE +
                            "    \"headers\" : {" + NEW_LINE +
                            "      \"content-type\" : [ \"text/plain\" ]," + NEW_LINE +
                            "      \"content-length\" : [ \"20\" ]" + NEW_LINE +
                            "    }," + NEW_LINE +
                            "    \"body\" : {" + NEW_LINE +
                            "      \"type\" : \"STRING\"," + NEW_LINE +
                            "      \"string\" : \"some_random_response\"," + NEW_LINE +
                            "      \"contentType\" : \"text/plain\"" + NEW_LINE +
                            "    }" + NEW_LINE +
                            "  }" + NEW_LINE +
                            "" + NEW_LINE +
                            " while sending request:" + NEW_LINE +
                            "" + NEW_LINE +
                            "  {" + NEW_LINE +
                            "    \"method\" : \"PUT\"," + NEW_LINE +
                            "    \"path\" : \"/mockserver/expectation\",")
            )
            ;
        } finally {
            UUIDService.fixedUUID = false;
            if (originalLevel != null) {
                ConfigurationProperties.logLevel(originalLevel.name());
            }
        }
    }
}
