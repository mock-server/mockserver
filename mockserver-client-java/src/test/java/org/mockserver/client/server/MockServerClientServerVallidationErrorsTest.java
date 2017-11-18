package org.mockserver.client.server;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.socket.PortFactory;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.MockitoAnnotations.initMocks;
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
        int echoServerPort = PortFactory.findFreePort();
        echoServer = new EchoServer(echoServerPort, false);
        mockServerClient = new MockServerClient("localhost", echoServerPort);
    }

    @Test
    public void shouldHandleServerValidationFailure() {
        // given
        String responseBody = "2 errors:\n" +
                " - object instance has properties which are not allowed by the schema: [\"paths\"] for field \"/httpRequest\"\n" +
                " - for field \"/httpRequest/body\" a plain string or one of the following example bodies must be specified \n" +
                "   {\n" +
                "     \"not\": false,\n" +
                "     \"type\": \"BINARY\",\n" +
                "     \"base64Bytes\": \"\",\n" +
                "     \"contentType\": \"\"\n" +
                "   }, \n" +
                "   {\n" +
                "     \"not\": false,\n" +
                "     \"type\": \"JSON\",\n" +
                "     \"json\": \"\",\n" +
                "     \"contentType\": \"\",\n" +
                "     \"matchType\": \"ONLY_MATCHING_FIELDS\"\n" +
                "   },\n" +
                "   {\n" +
                "     \"not\": false,\n" +
                "     \"type\": \"JSON_SCHEMA\",\n" +
                "     \"jsonSchema\": \"\"\n" +
                "   },\n" +
                "   {\n" +
                "     \"not\": false,\n" +
                "     \"type\": \"PARAMETERS\",\n" +
                "     \"parameters\": \"TO DO\"\n" +
                "   },\n" +
                "   {\n" +
                "     \"not\": false,\n" +
                "     \"type\": \"REGEX\",\n" +
                "     \"regex\": \"\"\n" +
                "   },\n" +
                "   {\n" +
                "     \"not\": false,\n" +
                "     \"type\": \"STRING\",\n" +
                "     \"string\": \"\"\n" +
                "   },\n" +
                "   {\n" +
                "     \"not\": false,\n" +
                "     \"type\": \"XML\",\n" +
                "     \"xml\": \"\",\n" +
                "     \"contentType\": \"\"\n" +
                "   },\n" +
                "   {\n" +
                "     \"not\": false,\n" +
                "     \"type\": \"XML_SCHEMA\",\n" +
                "     \"xmlSchema\": \"\"\n" +
                "   },\n" +
                "   {\n" +
                "     \"not\": false,\n" +
                "     \"type\": \"XPATH\",\n" +
                "     \"xpath\": \"\"\n" +
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
        exception.expectMessage(containsString(NEW_LINE +
                NEW_LINE +
                "error:" + NEW_LINE +
                NEW_LINE +
                "\t" + responseBody + NEW_LINE +
                NEW_LINE +
                "while submitted expectation:" + NEW_LINE +
                NEW_LINE +
                "\t{" + NEW_LINE
        ));

        // when
        mockServerClient.when(request()).respond(response());
    }
}
