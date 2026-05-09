package org.mockserver.server.initialize;

import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.RequestMatchers;
import org.mockserver.serialization.ExpectationSerializer;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;
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
        Configuration configuration = configuration()
            .initializationJsonPath("org/mockserver/server/initialize/initializerJson.json");

        // when
        final Expectation[] expectations = new ExpectationInitializerLoader(configuration, new MockServerLogger(configuration, ExpectationInitializerLoaderTest.class), mock(RequestMatchers.class)).loadExpectations();

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
    }

    @Test
    public void shouldLoadExpectationsFromClasspathInJsonOnWithGlobWithStar() {
        // given
        Configuration configuration = configuration()
            .initializationJsonPath("org/mockserver/server/initialize/initializerJson*.json");

        // when
        final Expectation[] expectations = new ExpectationInitializerLoader(configuration, new MockServerLogger(configuration, ExpectationInitializerLoaderTest.class), mock(RequestMatchers.class)).loadExpectations();

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
    }

    @Test
    public void shouldLoadExpectationsFromClasspathInJsonWithGlobWithQuestionMarks() {
        // given
        Configuration configuration = configuration()
            .initializationJsonPath("org/mockserver/server/initialize/initializerJson???.json");

        // when
        final Expectation[] expectations = new ExpectationInitializerLoader(configuration, new MockServerLogger(configuration, ExpectationInitializerLoaderTest.class), mock(RequestMatchers.class)).loadExpectations();

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
    }

    @Test
    public void shouldLoadExpectationsFromFileSystemInJson() throws Exception {
        // given
        Expectation[] expectations = {
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
        Files.write(mockserverInitializer.toPath(), expectationSerializer.serialize(expectations).getBytes(StandardCharsets.UTF_8));
        Configuration configuration = configuration()
            .initializationJsonPath(mockserverInitializer.getAbsolutePath());

        // when
        final Expectation[] loadedExpectations = new ExpectationInitializerLoader(configuration, new MockServerLogger(configuration, ExpectationInitializerLoaderTest.class), mock(RequestMatchers.class)).loadExpectations();

        // then
        assertThat(expectations, is(loadedExpectations));
    }

    @Test
    public void shouldLoadExpectationsFromFileSystemInJsonOnWithGlobWithStar() throws Exception {
        // given
        String uniquePrefix = UUID.randomUUID().toString();
        Expectation[] expectations = {
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
        Files.write(mockserverInitializer.toPath(), expectationSerializer.serialize(expectations).getBytes(StandardCharsets.UTF_8));
        Expectation[] expectationsOne = {
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
        Files.write(mockserverInitializerOne.toPath(), expectationSerializer.serialize(expectationsOne).getBytes(StandardCharsets.UTF_8));
        Expectation[] expectationsTwo = {
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
        Files.write(mockserverInitializerTwo.toPath(), expectationSerializer.serialize(expectationsTwo).getBytes(StandardCharsets.UTF_8));
        Expectation[] expectationsThree = {
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
        Files.write(mockserverInitializerThree.toPath(), expectationSerializer.serialize(expectationsThree).getBytes(StandardCharsets.UTF_8));
        Configuration configuration = configuration()
            .initializationJsonPath(mockserverInitializer.getParentFile().getAbsolutePath() + "/" + uniquePrefix + "_mockserverInitialization*.json");

        // when
        expectations = new ExpectationInitializerLoader(configuration, new MockServerLogger(configuration, ExpectationInitializerLoaderTest.class), mock(RequestMatchers.class)).loadExpectations();

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
    }

    @Test
    public void shouldLoadExpectationsFromFileSystemInJsonWithGlobWithSubPatterns() throws Exception {
        // given
        String uniquePrefix = UUID.randomUUID().toString();
        Expectation[] expectations = {
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
        Files.write(mockserverInitializer.toPath(), expectationSerializer.serialize(expectations).getBytes(StandardCharsets.UTF_8));
        Expectation[] expectationsOne = {
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
        Files.write(mockserverInitializerOne.toPath(), expectationSerializer.serialize(expectationsOne).getBytes(StandardCharsets.UTF_8));
        Expectation[] expectationsTwo = {
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
        Files.write(mockserverInitializerTwo.toPath(), expectationSerializer.serialize(expectationsTwo).getBytes(StandardCharsets.UTF_8));
        Expectation[] expectationsThree = {
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
        Files.write(mockserverInitializerThree.toPath(), expectationSerializer.serialize(expectationsThree).getBytes(StandardCharsets.UTF_8));
        Configuration configuration = configuration()
            .initializationJsonPath(mockserverInitializer.getParentFile().getAbsolutePath() + "/" + uniquePrefix + "_mockserverInitialization{One,Two}*.json");

        // when
        expectations = new ExpectationInitializerLoader(configuration, new MockServerLogger(configuration, ExpectationInitializerLoaderTest.class), mock(RequestMatchers.class)).loadExpectations();

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
    }

    @Test
    public void shouldLoadExpectationsFromInitializerClass() {
        // given
        Configuration configuration = configuration()
            .initializationClass(ExpectationInitializerExample.class.getName());

        // when
        final Expectation[] expectations = new ExpectationInitializerLoader(configuration, new MockServerLogger(configuration, ExpectationInitializerLoaderTest.class), mock(RequestMatchers.class)).loadExpectations();

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
    }

    @Test
    public void shouldLoadExpectationsFromClasspathOpenAPIYaml() {
        // given
        Configuration configuration = configuration()
            .initializationOpenAPIPath("org/mockserver/openapi/openapi_petstore_example.yaml");

        // when
        final Expectation[] expectations = new ExpectationInitializerLoader(configuration, new MockServerLogger(configuration, ExpectationInitializerLoaderTest.class), mock(RequestMatchers.class)).loadExpectations();

        // then
        assertThat(expectations.length, equalTo(4));
    }

    @Test
    public void shouldLoadExpectationsFromClasspathOpenAPIJson() {
        // given
        Configuration configuration = configuration()
            .initializationOpenAPIPath("org/mockserver/openapi/openapi_petstore_example.json");

        // when
        final Expectation[] expectations = new ExpectationInitializerLoader(configuration, new MockServerLogger(configuration, ExpectationInitializerLoaderTest.class), mock(RequestMatchers.class)).loadExpectations();

        // then
        assertThat(expectations.length, equalTo(4));
    }

    @Test
    public void shouldLoadExpectationsFromFileSystemOpenAPIYaml() throws Exception {
        // given
        File openAPIFile = File.createTempFile("mockserverOpenAPI", ".yaml");
        openAPIFile.deleteOnExit();
        try (InputStream inputStream = ExpectationInitializerLoaderTest.class.getClassLoader().getResourceAsStream("org/mockserver/openapi/openapi_petstore_example.yaml")) {
            Files.write(openAPIFile.toPath(), inputStream.readAllBytes());
        }
        Configuration configuration = configuration()
            .initializationOpenAPIPath(openAPIFile.getAbsolutePath());

        // when
        final Expectation[] expectations = new ExpectationInitializerLoader(configuration, new MockServerLogger(configuration, ExpectationInitializerLoaderTest.class), mock(RequestMatchers.class)).loadExpectations();

        // then
        assertThat(expectations.length, equalTo(4));
    }

    @Test
    public void shouldLoadExpectationsFromBothJsonAndOpenAPI() {
        // given
        Configuration configuration = configuration()
            .initializationJsonPath("org/mockserver/server/initialize/initializerJson.json")
            .initializationOpenAPIPath("org/mockserver/openapi/openapi_petstore_example.yaml");

        // when
        final Expectation[] expectations = new ExpectationInitializerLoader(configuration, new MockServerLogger(configuration, ExpectationInitializerLoaderTest.class), mock(RequestMatchers.class)).loadExpectations();

        // then
        assertThat(expectations.length, equalTo(6));
    }

    @Test
    public void shouldLoadExpectationsFromFileSystemOpenAPIWithGlob() throws Exception {
        // given
        String uniquePrefix = UUID.randomUUID().toString();
        File openAPIFile = File.createTempFile(uniquePrefix + "_openapi", ".yaml");
        openAPIFile.deleteOnExit();
        try (InputStream inputStream = ExpectationInitializerLoaderTest.class.getClassLoader().getResourceAsStream("org/mockserver/openapi/openapi_petstore_example.yaml")) {
            Files.write(openAPIFile.toPath(), inputStream.readAllBytes());
        }
        Configuration configuration = configuration()
            .initializationOpenAPIPath(openAPIFile.getParentFile().getAbsolutePath() + "/" + uniquePrefix + "_openapi*.yaml");

        // when
        final Expectation[] expectations = new ExpectationInitializerLoader(configuration, new MockServerLogger(configuration, ExpectationInitializerLoaderTest.class), mock(RequestMatchers.class)).loadExpectations();

        // then
        assertThat(expectations.length, equalTo(4));
    }

    @Test
    public void shouldHandleInvalidOpenAPISpecGracefully() throws Exception {
        // given
        File invalidSpecFile = File.createTempFile("mockserverInvalidOpenAPI", ".yaml");
        invalidSpecFile.deleteOnExit();
        Files.write(invalidSpecFile.toPath(), "this is not valid openapi".getBytes(StandardCharsets.UTF_8));
        Configuration configuration = configuration()
            .initializationOpenAPIPath(invalidSpecFile.getAbsolutePath());

        // when
        final Expectation[] expectations = new ExpectationInitializerLoader(configuration, new MockServerLogger(configuration, ExpectationInitializerLoaderTest.class), mock(RequestMatchers.class)).loadExpectations();

        // then
        assertThat(expectations.length, equalTo(0));
    }

    @Test
    public void shouldHandleNonExistentOpenAPIPathGracefully() {
        // given
        Configuration configuration = configuration()
            .initializationOpenAPIPath("/nonexistent/path/spec.yaml");

        // when
        final Expectation[] expectations = new ExpectationInitializerLoader(configuration, new MockServerLogger(configuration, ExpectationInitializerLoaderTest.class), mock(RequestMatchers.class)).loadExpectations();

        // then
        assertThat(expectations.length, equalTo(0));
    }

    @Test
    public void shouldNotLoadOpenAPIExpectationsWhenPathNotSet() {
        // given
        Configuration configuration = configuration()
            .initializationOpenAPIPath("")
            .initializationJsonPath("")
            .initializationClass("");

        // when
        final Expectation[] expectations = new ExpectationInitializerLoader(configuration, new MockServerLogger(configuration, ExpectationInitializerLoaderTest.class), mock(RequestMatchers.class)).loadExpectations();

        // then
        assertThat(expectations.length, equalTo(0));
    }

}