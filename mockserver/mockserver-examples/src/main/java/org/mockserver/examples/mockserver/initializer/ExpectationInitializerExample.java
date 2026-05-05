package org.mockserver.examples.mockserver.initializer;

import org.mockserver.mock.Expectation;
import org.mockserver.server.initialize.ExpectationInitializer;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class ExpectationInitializerExample implements ExpectationInitializer {
    @Override
    public Expectation[] initializeExpectations() {
        return new Expectation[]{
            new Expectation
                (
                    request("/simpleFirst")
                )
                .thenRespond
                (
                    response("some first response")
                ),
            new Expectation
                (
                    request("/simpleSecond")
                )
                .thenRespond
                (
                    response("some second response")
                )
        };
    }
}
