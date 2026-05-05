package org.mockserver;

import org.mockserver.client.MockServerClient;
import org.mockserver.client.initialize.PluginExpectationInitializer;
import org.mockserver.mock.action.ExpectationCallback;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class TestClasspathInitializationClass implements PluginExpectationInitializer {

    public static class InnerClassCallback implements ExpectationResponseCallback {
        @Override
        public HttpResponse handle(HttpRequest httpRequest) throws Exception {
            return response()
                    .withBody("test_initializer_response_body");
        }
    }

    @Override
    public void initializeExpectations(MockServerClient mockServerClient) {
        mockServerClient
                .when(
                        request()
                                .withPath("/test_initializer_path")
                                .withBody("test_initializer_request_body")
                )
                .respond(callback(InnerClassCallback.class));
    }

}
