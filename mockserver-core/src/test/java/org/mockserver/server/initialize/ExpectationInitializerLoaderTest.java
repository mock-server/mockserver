package org.mockserver.server.initialize;

import org.junit.Test;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.RequestMatchers;
import org.mockserver.serialization.ExpectationSerializer;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class ExpectationInitializerLoaderTest {

    private final ExpectationSerializer expectationSerializer = new ExpectationSerializer(new MockServerLogger());

    @Test
    public void shouldLoadExpectationsFromClasspathInJson() {
        // given
        String initializationJsonPath = ConfigurationProperties.initializationJsonPath();
        try {
            ConfigurationProperties.initializationJsonPath("org/mockserver/server/initialize/initializerJson.json");

            // when
            final Expectation[] expectations = new ExpectationInitializerLoader(new MockServerLogger(), mock(RequestMatchers.class)).loadExpectations();

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
    public void shouldLoadExpectationsFromClasspathInJsonOnWithGlobWithStar() {
        // given
        String initializationJsonPath = ConfigurationProperties.initializationJsonPath();
        try {
            ConfigurationProperties.initializationJsonPath("org/mockserver/server/initialize/initializerJson*.json");

            // when
            final Expectation[] expectations = new ExpectationInitializerLoader(new MockServerLogger(), mock(RequestMatchers.class)).loadExpectations();

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
                ),
                new Expectation(
                    request()
                        .withPath("/pathOneFirst")
                )
                    .thenRespond(
                    response()
                        .withBody("one first response")
                ),
                new Expectation(
                    request()
                        .withPath("/pathOneSecond")
                )
                    .thenRespond(
                    response()
                        .withBody("one second response")
                ),
                new Expectation(
                    request()
                        .withPath("/pathThreeFirst")
                )
                    .thenRespond(
                    response()
                        .withBody("three first response")
                ),
                new Expectation(
                    request()
                        .withPath("/pathThreeSecond")
                )
                    .thenRespond(
                    response()
                        .withBody("three second response")
                ),
                new Expectation(
                    request()
                        .withPath("/pathTwoFirst")
                )
                    .thenRespond(
                    response()
                        .withBody("two first response")
                ),
                new Expectation(
                    request()
                        .withPath("/pathTwoSecond")
                )
                    .thenRespond(
                    response()
                        .withBody("two second response")
                )
            }));
        } finally {
            ConfigurationProperties.initializationJsonPath(initializationJsonPath);
        }
    }

    @Test
    public void shouldLoadExpectationsFromClasspathInJsonWithGlobWithQuestionMarks() {
        // given
        String initializationJsonPath = ConfigurationProperties.initializationJsonPath();
        try {
            ConfigurationProperties.initializationJsonPath("org/mockserver/server/initialize/initializerJson???.json");

            // when
            final Expectation[] expectations = new ExpectationInitializerLoader(new MockServerLogger(), mock(RequestMatchers.class)).loadExpectations();

            // then
            assertThat(expectations, is(new Expectation[]{
                new Expectation(
                    request()
                        .withPath("/pathOneFirst")
                )
                    .thenRespond(
                    response()
                        .withBody("one first response")
                ),
                new Expectation(
                    request()
                        .withPath("/pathOneSecond")
                )
                    .thenRespond(
                    response()
                        .withBody("one second response")
                ),
                new Expectation(
                    request()
                        .withPath("/pathTwoFirst")
                )
                    .thenRespond(
                    response()
                        .withBody("two first response")
                ),
                new Expectation(
                    request()
                        .withPath("/pathTwoSecond")
                )
                    .thenRespond(
                    response()
                        .withBody("two second response")
                )
            }));
        } finally {
            ConfigurationProperties.initializationJsonPath(initializationJsonPath);
        }
    }

    @Test
    public void shouldLoadExpectationsFromFileSystemInJson() throws Exception {
        // given
        String initializationJsonPath = ConfigurationProperties.initializationJsonPath();
        try {
            Expectation[] expections = {
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
            };
            File mockserverInitializer = File.createTempFile("mockserverInitialization", ".json");
            Files.write(mockserverInitializer.toPath(), expectationSerializer.serialize(expections).getBytes(StandardCharsets.UTF_8));
            ConfigurationProperties.initializationJsonPath(mockserverInitializer.getAbsolutePath());

            // when
            final Expectation[] expectations = new ExpectationInitializerLoader(new MockServerLogger(), mock(RequestMatchers.class)).loadExpectations();

            // then
            assertThat(expectations, is(expections));
        } finally {
            ConfigurationProperties.initializationJsonPath(initializationJsonPath);
        }
    }

    @Test
    public void shouldLoadExpectationsFromFileSystemInJsonOnWithGlobWithStar()  throws Exception {
        // given
        String initializationJsonPath = ConfigurationProperties.initializationJsonPath();
        try {
            String uniquePrefix = UUID.randomUUID().toString();
            Expectation[] expections = {
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
            };
            File mockserverInitializer = File.createTempFile(uniquePrefix + "_mockserverInitialization", ".json");
            Files.write(mockserverInitializer.toPath(), expectationSerializer.serialize(expections).getBytes(StandardCharsets.UTF_8));
            Expectation[] expectionsOne = {
                new Expectation(
                    request()
                        .withPath("/pathOneFirst")
                )
                    .thenRespond(
                    response()
                        .withBody("one first response")
                ),
                new Expectation(
                    request()
                        .withPath("/pathOneSecond")
                )
                    .thenRespond(
                    response()
                        .withBody("one second response")
                )
            };
            File mockserverInitializerOne = File.createTempFile(uniquePrefix + "_mockserverInitializationOne", ".json");
            Files.write(mockserverInitializerOne.toPath(), expectationSerializer.serialize(expectionsOne).getBytes(StandardCharsets.UTF_8));
            Expectation[] expectionsTwo = {
                new Expectation(
                    request()
                        .withPath("/pathTwoFirst")
                )
                    .thenRespond(
                    response()
                        .withBody("two first response")
                ),
                new Expectation(
                    request()
                        .withPath("/pathTwoSecond")
                )
                    .thenRespond(
                    response()
                        .withBody("two second response")
                )
            };
            File mockserverInitializerTwo = File.createTempFile(uniquePrefix + "_mockserverInitializationTwo", ".json");
            Files.write(mockserverInitializerTwo.toPath(), expectationSerializer.serialize(expectionsTwo).getBytes(StandardCharsets.UTF_8));
            Expectation[] expectionsThree = {
                new Expectation(
                    request()
                        .withPath("/pathThreeFirst")
                )
                    .thenRespond(
                    response()
                        .withBody("three first response")
                ),
                new Expectation(
                    request()
                        .withPath("/pathThreeSecond")
                )
                    .thenRespond(
                    response()
                        .withBody("three second response")
                )
            };
            File mockserverInitializerThree = File.createTempFile(uniquePrefix + "_mockserverInitializationThree", ".json");
            Files.write(mockserverInitializerThree.toPath(), expectationSerializer.serialize(expectionsThree).getBytes(StandardCharsets.UTF_8));
            ConfigurationProperties.initializationJsonPath(mockserverInitializer.getParentFile().getAbsolutePath() + "/" + uniquePrefix + "_mockserverInitialization*.json");

            // when
            final Expectation[] expectations = new ExpectationInitializerLoader(new MockServerLogger(), mock(RequestMatchers.class)).loadExpectations();

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
                ),
                new Expectation(
                    request()
                        .withPath("/pathOneFirst")
                )
                    .thenRespond(
                    response()
                        .withBody("one first response")
                ),
                new Expectation(
                    request()
                        .withPath("/pathOneSecond")
                )
                    .thenRespond(
                    response()
                        .withBody("one second response")
                ),
                new Expectation(
                    request()
                        .withPath("/pathThreeFirst")
                )
                    .thenRespond(
                    response()
                        .withBody("three first response")
                ),
                new Expectation(
                    request()
                        .withPath("/pathThreeSecond")
                )
                    .thenRespond(
                    response()
                        .withBody("three second response")
                ),
                new Expectation(
                    request()
                        .withPath("/pathTwoFirst")
                )
                    .thenRespond(
                    response()
                        .withBody("two first response")
                ),
                new Expectation(
                    request()
                        .withPath("/pathTwoSecond")
                )
                    .thenRespond(
                    response()
                        .withBody("two second response")
                )
            }));
        } finally {
            ConfigurationProperties.initializationJsonPath(initializationJsonPath);
        }
    }

    @Test
    public void shouldLoadExpectationsFromFileSystemInJsonWithGlobWithSubPatterns()  throws Exception {
        // given
        String initializationJsonPath = ConfigurationProperties.initializationJsonPath();
        try {
            String uniquePrefix = UUID.randomUUID().toString();
            Expectation[] expections = {
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
            };
            File mockserverInitializer = File.createTempFile(uniquePrefix + "_mockserverInitialization", ".json");
            Files.write(mockserverInitializer.toPath(), expectationSerializer.serialize(expections).getBytes(StandardCharsets.UTF_8));
            Expectation[] expectionsOne = {
                new Expectation(
                    request()
                        .withPath("/pathOneFirst")
                )
                    .thenRespond(
                    response()
                        .withBody("one first response")
                ),
                new Expectation(
                    request()
                        .withPath("/pathOneSecond")
                )
                    .thenRespond(
                    response()
                        .withBody("one second response")
                )
            };
            File mockserverInitializerOne = File.createTempFile(uniquePrefix + "_mockserverInitializationOne", ".json");
            Files.write(mockserverInitializerOne.toPath(), expectationSerializer.serialize(expectionsOne).getBytes(StandardCharsets.UTF_8));
            Expectation[] expectionsTwo = {
                new Expectation(
                    request()
                        .withPath("/pathTwoFirst")
                )
                    .thenRespond(
                    response()
                        .withBody("two first response")
                ),
                new Expectation(
                    request()
                        .withPath("/pathTwoSecond")
                )
                    .thenRespond(
                    response()
                        .withBody("two second response")
                )
            };
            File mockserverInitializerTwo = File.createTempFile(uniquePrefix + "_mockserverInitializationTwo", ".json");
            Files.write(mockserverInitializerTwo.toPath(), expectationSerializer.serialize(expectionsTwo).getBytes(StandardCharsets.UTF_8));
            Expectation[] expectionsThree = {
                new Expectation(
                    request()
                        .withPath("/pathThreeFirst")
                )
                    .thenRespond(
                    response()
                        .withBody("three first response")
                ),
                new Expectation(
                    request()
                        .withPath("/pathThreeSecond")
                )
                    .thenRespond(
                    response()
                        .withBody("three second response")
                )
            };
            File mockserverInitializerThree = File.createTempFile(uniquePrefix + "_mockserverInitializationThree", ".json");
            Files.write(mockserverInitializerThree.toPath(), expectationSerializer.serialize(expectionsThree).getBytes(StandardCharsets.UTF_8));
            ConfigurationProperties.initializationJsonPath(mockserverInitializer.getParentFile().getAbsolutePath() + "/" + uniquePrefix + "_mockserverInitialization{One,Two}*.json");

            // when
            final Expectation[] expectations = new ExpectationInitializerLoader(new MockServerLogger(), mock(RequestMatchers.class)).loadExpectations();

            // then
            assertThat(expectations, is(new Expectation[]{
                new Expectation(
                    request()
                        .withPath("/pathOneFirst")
                )
                    .thenRespond(
                    response()
                        .withBody("one first response")
                ),
                new Expectation(
                    request()
                        .withPath("/pathOneSecond")
                )
                    .thenRespond(
                    response()
                        .withBody("one second response")
                ),
                new Expectation(
                    request()
                        .withPath("/pathTwoFirst")
                )
                    .thenRespond(
                    response()
                        .withBody("two first response")
                ),
                new Expectation(
                    request()
                        .withPath("/pathTwoSecond")
                )
                    .thenRespond(
                    response()
                        .withBody("two second response")
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
            final Expectation[] expectations = new ExpectationInitializerLoader(new MockServerLogger(), mock(RequestMatchers.class)).loadExpectations();

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