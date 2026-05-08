package org.mockserver.netty.integration.tls.inbound;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.testing.integration.mock.AbstractMockingIntegrationTestBase;

import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.configuration.ConfigurationProperties.*;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.OK_200;
import static org.mockserver.stop.Stop.stopQuietly;

public class ClientAuthenticationDataPlaneOnlyMockingIntegrationTest extends AbstractMockingIntegrationTestBase {

    private static boolean originalTLSMutualAuthenticationRequired;
    private static boolean originalControlPlaneTLSMutualAuthenticationRequired;

    @BeforeClass
    public static void startServer() {
        originalTLSMutualAuthenticationRequired = tlsMutualAuthenticationRequired();
        originalControlPlaneTLSMutualAuthenticationRequired = controlPlaneTLSMutualAuthenticationRequired();

        tlsMutualAuthenticationRequired(true);
        controlPlaneTLSMutualAuthenticationRequired(false);

        mockServerClient = ClientAndServer.startClientAndServer();
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServerClient);

        tlsMutualAuthenticationRequired(originalTLSMutualAuthenticationRequired);
        controlPlaneTLSMutualAuthenticationRequired(originalControlPlaneTLSMutualAuthenticationRequired);
    }

    @Override
    public int getServerPort() {
        return mockServerClient.getPort();
    }

    @Test
    public void shouldAllowControlPlaneOverPlainHttp() {
        mockServerClient
            .when(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_path"))
            )
            .respond(
                response()
                    .withStatusCode(200)
                    .withBody("some_body_response")
            );

        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body_response"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("some_path"))
                    .withMethod("POST"),
                getHeadersToRemove()
            )
        );
    }

    @Test
    public void shouldReturnUpgradeForDataPlaneOverPlainHttp() {
        mockServerClient
            .when(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_path"))
            )
            .respond(
                response()
                    .withStatusCode(200)
                    .withBody("some_body_response")
            );

        assertEquals(
            response()
                .withStatusCode(426)
                .withReasonPhrase("Upgrade Required")
                .withHeader("Upgrade", "TLS/1.2, HTTP/1.1"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path"))
                    .withMethod("POST"),
                getHeadersToRemove()
            )
        );
    }
}
