package org.mockserver.examples.mockserver;

import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.netty.MockServer;

import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.Parameter.param;

public class ExpectationsNotification {

    private static void registerClientAndServerExpectationListener() {
        new ClientAndServer()
            .registerListener(expectations -> {
                System.out.println("ClientAndServer expectations updated " + expectations);
            })
            .when(
                request()
                    .withMethod("GET")
                    .withPath("/view/cart")
                    .withCookies(
                        cookie("session", "4930456C-C718-476F-971F-CB8E047AB349")
                    )
                    .withQueryStringParameters(
                        param("cartId", "055CA455-1DF7-45BB-8535-4F83E7266092")
                    )
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

    private static void registerMockServerExpectationListener() {
        MockServer mockServer = new MockServer().registerListener(expectations -> {
            System.out.println("MockServer expectations updated " + expectations);
        });
        new MockServerClient("localhost", mockServer.getLocalPort())
            .when(
                request()
                    .withMethod("GET")
                    .withPath("/view/cart")
                    .withCookies(
                        cookie("session", "4930456C-C718-476F-971F-CB8E047AB349")
                    )
                    .withQueryStringParameters(
                        param("cartId", "055CA455-1DF7-45BB-8535-4F83E7266092")
                    )
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

}
