package org.mockserver.client;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.client.ClientException;
import org.mockserver.client.MockServerClient;
import org.mockserver.echo.http.EchoServer;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class MockServerClientServerVallidationErrorsTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    private EchoServer echoServer;
    private MockServerClient mockServerClient;

    @Before
    public void setupTestFixture() throws Exception {
        echoServer = new EchoServer(false);
        mockServerClient = new MockServerClient("localhost", echoServer.getPort());
    }

    @Test
    public void shouldHandleServerValidationFailure() {
        // given
        String responseBody = "2 errors:" + NEW_LINE +
                " - object instance has properties which are not allowed by the schema: [\"paths\"] for field \"/httpRequest\"" + NEW_LINE +
                " - for field \"/httpRequest/body\" a plain string or one of the following example bodies must be specified " + NEW_LINE +
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
                "   }";
        echoServer.withNextResponse(response()
                .withStatusCode(400)
                .withBody(responseBody)
        );

        // then
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString(responseBody));

        // when
        mockServerClient.when(request()).respond(response());
    }


    @Test
    public void shouldHandleOtherClientError() {
        // given
        String responseBody = "some_random_response";
        echoServer.withNextResponse(response()
                .withStatusCode(401)
                .withBody(responseBody)
        );

        // then
        exception.expect(ClientException.class);
        exception.expectMessage(is("error:" + NEW_LINE +
            NEW_LINE +
            "\t" + responseBody + NEW_LINE +
            NEW_LINE +
            " while submitted expectation:" + NEW_LINE +
            NEW_LINE +
            "\t{" + NEW_LINE +
            "\t  \"httpRequest\" : { }," + NEW_LINE +
            "\t  \"times\" : {" + NEW_LINE +
            "\t    \"unlimited\" : true" + NEW_LINE +
            "\t  }," + NEW_LINE +
            "\t  \"timeToLive\" : {" + NEW_LINE +
            "\t    \"unlimited\" : true" + NEW_LINE +
            "\t  }," + NEW_LINE +
            "\t  \"httpResponse\" : { }" + NEW_LINE +
            "\t}" + NEW_LINE
        ));

        // when
        mockServerClient.when(request()).respond(response());
    }
}
