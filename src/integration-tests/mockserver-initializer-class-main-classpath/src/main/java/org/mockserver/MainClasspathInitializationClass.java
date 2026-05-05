package org.mockserver;

import org.mockserver.client.MockServerClient;
import org.mockserver.client.initialize.PluginExpectationInitializer;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class MainClasspathInitializationClass implements PluginExpectationInitializer {

    @Override
    public void initializeExpectations(MockServerClient mockServerClient) {
        mockServerClient
                .when(
                        request()
                                .withPath("/test_initializer_path")
                                .withBody("test_initializer_request_body")
                )
                .respond(
                        response()
                                .withBody("test_initializer_response_body")
                );
    }

}
