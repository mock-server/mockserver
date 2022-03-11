package org.mockserver.junit.jupiter.integration;

import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.socket.PortFactory;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class ExtendedMockServerExtension extends MockServerExtension {

    private static final ClientAndServer CLIENT_AND_SERVER = ClientAndServer.startClientAndServer(PortFactory.findFreePort());

    public ExtendedMockServerExtension() {
        super(CLIENT_AND_SERVER);
        customClientAndServer
            .when(
                request()
                    .withPath("/some_extended_path")
            )
            .respond(
                response()
                    .withBody("some_extended_body")
            );
    }

}
