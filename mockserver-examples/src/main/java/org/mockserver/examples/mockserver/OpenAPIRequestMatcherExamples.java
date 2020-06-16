package org.mockserver.examples.mockserver;

import org.mockserver.client.MockServerClient;
import org.mockserver.file.FileReader;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.OpenAPIDefinition;

import java.util.concurrent.TimeUnit;

import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.OpenAPIDefinition.openAPI;

/**
 * @author jamesdbloom
 */
public class OpenAPIRequestMatcherExamples {

    public void matchRequestByOpenAPILoadedByHttpUrl() {
        new MockServerClient("localhost", 1080)
            .when(
                openAPI(
                    "https://raw.githubusercontent.com/mock-server/mockserver/master/mockserver-integration-testing/src/main/resources/org/mockserver/mock/openapi_petstore_example.json"
                )
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

    public void matchRequestByOpenAPIOperation() {
        new MockServerClient("localhost", 1080)
            .when(
                openAPI(
                    "https://raw.githubusercontent.com/mock-server/mockserver/master/mockserver-integration-testing/src/main/resources/org/mockserver/mock/openapi_petstore_example.json",
                    "showPetById"
                )
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

    public void matchRequestByOpenAPILoadedByFileUrl() {
        new MockServerClient("localhost", 1080)
            .when(
                openAPI(
                    "file:/Users/jamesbloom/git/mockserver/mockserver/mockserver-core/target/test-classes/org/mockserver/mock/openapi_petstore_example.json"
                )
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

    public void matchRequestByOpenAPILoadedByClasspathLocation() {
        new MockServerClient("localhost", 1080)
            .when(
                openAPI("org/mockserver/mock/openapi_petstore_example.json")
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

    public void matchRequestByOpenAPILoadedByStringLiteral() {
        new MockServerClient("localhost", 1080)
            .when(
                new OpenAPIDefinition()
                    .withSpecUrlOrPayload(
                        FileReader.readFileFromClassPathOrPath("/Users/jamesbloom/git/mockserver/mockserver/mockserver-core/target/test-classes/org/mockserver/mock/openapi_petstore_example.json")
                    )
                    .withOperationId("listPets")
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

    public void matchRequestByOpenAPIOperationTwice() {
        new MockServerClient("localhost", 1080)
            .when(
                new OpenAPIDefinition()
                    .withSpecUrlOrPayload(
                        FileReader.readFileFromClassPathOrPath("/Users/jamesbloom/git/mockserver/mockserver/mockserver-core/target/test-classes/org/mockserver/mock/openapi_petstore_example.json")
                    )
                    .withOperationId("listPets"),
                Times.exactly(2)
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

    public void updateExpectationById() {
        new MockServerClient("localhost", 1080)
            .upsert(
                new Expectation(
                    openAPI(
                        "https://raw.githubusercontent.com/mock-server/mockserver/master/mockserver-integration-testing/src/main/resources/org/mockserver/mock/openapi_petstore_example.json",
                        "showPetById"
                    ),
                    Times.once(),
                    TimeToLive.exactly(TimeUnit.SECONDS, 60L),
                    100
                )
                    .withId("630a6e5b-9d61-4668-a18f-a0d3df558583")
                    .thenRespond(response().withBody("some_response_body"))
            );
    }
}
