package org.mockserver.junit.jupiter.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpResponse;
import org.mockserver.test.TestLoggerExtension;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.OK_200;

/**
 * @author jamesdbloom
 */
@ExtendWith({
    ExtendedMockServerExtension.class,
    TestLoggerExtension.class,
})
class JUnitJupiterExtendedExtensionConstructorParameterIntegrationTest extends AbstractMockingIntegrationTestBase {

    public JUnitJupiterExtendedExtensionConstructorParameterIntegrationTest(ClientAndServer client) {
        mockServerClient = client;
    }

    @Override
    @BeforeEach
    public void resetServer() {
        // do nothing so custom expectation is not reset
    }

    @AfterEach
    public void resetServerAfter() {
        mockServerClient.reset();
    }

    @Override
    public int getServerPort() {
        return mockServerClient.remoteAddress().getPort();
    }

    @Test
    public void shouldReturnExtendedResponse() {
        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_extended_body"),
            makeRequest(
                request()
                    .withPath("/some_extended_path"),
                headersToIgnore)
        );
    }

}