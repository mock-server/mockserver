package org.mockserver.server.initialize;

import org.junit.Test;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class ExpectationInitializerLoaderTest {

    @Test
    public void shouldLoadExpectationsFromJson() {
        // given
        String initializationJsonPath = ConfigurationProperties.initializationJsonPath();
        try {
            ConfigurationProperties.initializationJsonPath("org/mockserver/server/initialize/initializerJson.json");

            // when
            final Expectation[] expectations = new ExpectationInitializerLoader(new MockServerLogger()).loadExpectations();

            // then
            assertThat(expectations, is(new Expectation[]{
                new Expectation(
                    request()
                        .withPath("/simpleFirst")
                )
                    .thenRespond(
                    response()
                        .withBody("some first response")
                ),
                new Expectation(
                    request()
                        .withPath("/simpleSecond")
                )
                    .thenRespond(
                    response()
                        .withBody("some second response")
                )
            }));
        } finally {
            ConfigurationProperties.initializationJsonPath(initializationJsonPath);
        }
    }

    @Test
    public void shouldLoadExpectationsFromInitializerClass() {
        // given
        String initializationClass = ConfigurationProperties.initializationClass();
        try {
            ConfigurationProperties.initializationClass(ExpectationInitializerExample.class.getName());

            // when
            final Expectation[] expectations = new ExpectationInitializerLoader(new MockServerLogger()).loadExpectations();

            // then
            assertThat(expectations, is(new Expectation[]{
                new Expectation(
                    request("/simpleFirst")
                )
                    .thenRespond(
                    response("some first response")
                ),
                new Expectation(
                    request("/simpleSecond")
                )
                    .thenRespond(
                    response("some second response")
                )
            }));
        } finally {
            ConfigurationProperties.initializationClass(initializationClass);
        }
    }

}