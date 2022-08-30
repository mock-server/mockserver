package org.mockserver.examples.mockserver;

import org.mockserver.integration.ClientAndServer;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class DashboardExamples {

    public void launchDashboardProgrammatically() {
        ClientAndServer clientAndServer = new ClientAndServer(1080);

        clientAndServer
            .when(
                request()
                    .withPath("/some.*")
            )
            .respond(
                response()
                    .withBody("some_body")
            );

        clientAndServer
            .openUI();
    }

}
