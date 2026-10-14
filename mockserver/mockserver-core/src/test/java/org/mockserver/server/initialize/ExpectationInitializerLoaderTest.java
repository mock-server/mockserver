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
import java.util.concurrent.TimeUnit;

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

    // --- failOnInitializationError -------------------------------------------------------------

    @Test
    public void shouldFailFastWhenInitializationClassIsBrokenAndFailOnInitializationErrorEnabled() {
        // given - a class name that does not exist, so loading it throws
        Configuration configuration = configuration()
            .initializationClass("org.mockserver.server.initialize.NoSuchInitializerClass")
            .failOnInitializationError(true);

        // when / then
        ExpectationInitializerException exception = org.junit.Assert.assertThrows(
            ExpectationInitializerException.class,
            () -> new ExpectationInitializerLoader(configuration, new MockServerLogger(configuration, ExpectationInitializerLoaderTest.class), mock(RequestMatchers.class))
        );
        assertThat(exception.getMessage().contains("class"), is(true));
    }

    @Test
    public void shouldContinueWhenInitializationClassIsBrokenAndFailOnInitializationErrorDisabled() {
        // given - default (false): a broken class logs a WARN and yields zero expectations
        Configuration configuration = configuration()
            .initializationClass("org.mockserver.server.initialize.NoSuchInitializerClass");

        // when
        final Expectation[] expectations = new ExpectationInitializerLoader(configuration, new MockServerLogger(configuration, ExpectationInitializerLoaderTest.class), mock(RequestMatchers.class)).loadExpectations();

        // then
        assertThat(expectations.length, equalTo(0));
    }

    @Test
    public void shouldFailFastWhenJsonInitializerIsMalformedAndFailOnInitializationErrorEnabled() throws Exception {
        // given - a JSON file that is not a valid expectation array
        File malformedJson = File.createTempFile("mockserverMalformedInitialization", ".json");
        malformedJson.deleteOnExit();
        Files.write(malformedJson.toPath(), "{ this is not valid expectation json".getBytes(StandardCharsets.UTF_8));
        Configuration configuration = configuration()
            .initializationJsonPath(malformedJson.getAbsolutePath())
            .failOnInitializationError(true);

        // when / then
        org.junit.Assert.assertThrows(
            ExpectationInitializerException.class,
            () -> new ExpectationInitializerLoader(configuration, new MockServerLogger(configuration, ExpectationInitializerLoaderTest.class), mock(RequestMatchers.class))
        );
    }

    // --- an invalid entry in a JSON initializer file ---------------------------------------------

    private static final String ONE_INVALID_OF_THREE = "[" +
        " { \"id\" : \"good-one\", \"httpRequest\" : { \"path\" : \"/one\" }, \"httpResponse\" : { \"statusCode\" : 200 } }," +
        " { \"id\" : \"bad-zero\", \"httpRequest\" : { \"path\" : \"/zero\" }, \"httpResponse\" : { \"statusCode\" : 0 } }," +
        " { \"id\" : \"good-two\", \"httpRequest\" : { \"path\" : \"/two\" }, \"httpResponse\" : { \"statusCode\" : 201 } }" +
        " ]";

    private static File initializerFile(String json) throws Exception {
        File file = File.createTempFile("mockserverOneInvalidEntry", ".json");
        file.deleteOnExit();
        Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @Test
    public void shouldLoadTheValidExpectationsAndSkipAnInvalidOneWithAWarningNamingIt() throws Exception {
        // given - a statusCode of 0, as the dashboard registered for a blank status code before 9.0.0
        File file = initializerFile(ONE_INVALID_OF_THREE);
        Configuration configuration = configuration().initializationJsonPath(file.getAbsolutePath());
        java.util.List<org.mockserver.log.model.LogEntry> logged = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        MockServerLogger capturingLogger = new MockServerLogger(configuration().logLevel("INFO"), ExpectationInitializerLoaderTest.class) {
            @Override
            public void logEvent(org.mockserver.log.model.LogEntry logEntry) {
                logged.add(logEntry);
            }
        };

        // when
        Expectation[] loaded = new ExpectationInitializerLoader(configuration, capturingLogger, mock(RequestMatchers.class)).loadExpectations();

        // then - the other two load, and a WARN names the skipped one and why
        assertThat(java.util.Arrays.stream(loaded).map(Expectation::getId).collect(java.util.stream.Collectors.toList()), is(java.util.Arrays.asList("good-one", "good-two")));
        java.util.List<String> warnings = logged.stream()
            .filter(entry -> entry.getLogLevel() == org.slf4j.event.Level.WARN)
            .map(org.mockserver.log.model.LogEntry::getMessage)
            .collect(java.util.stream.Collectors.toList());
        assertThat(String.valueOf(warnings), warnings.stream().anyMatch(message -> message.contains("skipping an invalid expectation")
            && message.contains("expectation 2 of 3 (id \"bad-zero\") for path \"/zero\"")
            && message.contains(file.getAbsolutePath())
            && message.contains("$.httpResponse.statusCode: must have a minimum value of 100")), is(true));
    }

    @Test
    public void shouldFailStartupOnAnInvalidEntryWhenFailOnInitializationErrorEnabled() throws Exception {
        // given - the flag asks for strictness, so a skipped entry still fails startup
        File file = initializerFile(ONE_INVALID_OF_THREE);
        Configuration configuration = configuration()
            .initializationJsonPath(file.getAbsolutePath())
            .failOnInitializationError(true);

        // when / then
        ExpectationInitializerException exception = org.junit.Assert.assertThrows(
            ExpectationInitializerException.class,
            () -> new ExpectationInitializerLoader(configuration, new MockServerLogger(configuration, ExpectationInitializerLoaderTest.class), mock(RequestMatchers.class))
        );
        assertThat(exception.getCause().getMessage(), is("skipped 1 invalid expectation(s): expectation 2 of 3 (id \"bad-zero\") for path \"/zero\""));
    }

    @Test
    public void shouldFailFastWhenOpenAPIInitializerIsInvalidAndFailOnInitializationErrorEnabled() throws Exception {
        // given - an OpenAPI file that fails to parse
        File invalidSpecFile = File.createTempFile("mockserverInvalidOpenAPIFailFast", ".yaml");
        invalidSpecFile.deleteOnExit();
        Files.write(invalidSpecFile.toPath(), "this is not valid openapi".getBytes(StandardCharsets.UTF_8));
        Configuration configuration = configuration()
            .initializationOpenAPIPath(invalidSpecFile.getAbsolutePath())
            .failOnInitializationError(true);

        // when / then
        org.junit.Assert.assertThrows(
            ExpectationInitializerException.class,
            () -> new ExpectationInitializerLoader(configuration, new MockServerLogger(configuration, ExpectationInitializerLoaderTest.class), mock(RequestMatchers.class))
        );
    }

    // --- lint of the expectations a JSON initializer file loads ----------------------------------

    private static final String LINT_PROBLEMS = "[" +
        " { \"id\" : \"catch-all\", \"httpRequest\" : { \"path\" : \"/orders/.*\" }, \"httpResponse\" : { \"statusCode\" : 200 } }," +
        " { \"id\" : \"specific\", \"httpRequest\" : { \"path\" : \"/orders/1\" }, \"httpResponse\" : { \"statusCode\" : 201 } }," +
        " { \"httpRequest\" : { \"path\" : \"users\" }, \"httpResponse\" : { \"statusCode\" : 202 } }," +
        " { \"id\" : \"strict\", \"httpRequest\" : { \"path\" : \"/strict\", \"body\" : { \"type\" : \"JSON\", \"json\" : \"{\\\"items\\\":[1]}\", \"matchType\" : \"STRICT\" } }, \"httpResponse\" : { \"statusCode\" : 203 } }" +
        " ]";

    private static java.util.List<String> lintWarnings(java.util.List<org.mockserver.log.model.LogEntry> logged) {
        return logged.stream()
            .filter(entry -> entry.getLogLevel() == org.slf4j.event.Level.WARN && entry.getMessage().contains("expectation lint found"))
            .map(org.mockserver.log.model.LogEntry::getMessage)
            .collect(java.util.stream.Collectors.toList());
    }

    private static MockServerLogger capturingLogger(java.util.List<org.mockserver.log.model.LogEntry> logged) {
        return new MockServerLogger(configuration().logLevel("INFO"), ExpectationInitializerLoaderTest.class) {
            @Override
            public void logEvent(org.mockserver.log.model.LogEntry logEntry) {
                logged.add(logEntry);
            }
        };
    }

    @Test
    public void shouldLogOneLintWarningForAJsonInitializerFileAndStillLoadEveryExpectation() throws Exception {
        // given
        File file = initializerFile(LINT_PROBLEMS);
        Configuration configuration = configuration().initializationJsonPath(file.getAbsolutePath());
        java.util.List<org.mockserver.log.model.LogEntry> logged = new java.util.concurrent.CopyOnWriteArrayList<>();
        RequestMatchers requestMatchers = mock(RequestMatchers.class);

        // when
        new ExpectationInitializerLoader(configuration, capturingLogger(logged), requestMatchers);

        // then - all four are loaded unchanged
        org.mockito.ArgumentCaptor<Expectation[]> loaded = org.mockito.ArgumentCaptor.forClass(Expectation[].class);
        org.mockito.Mockito.verify(requestMatchers).update(loaded.capture(), org.mockito.ArgumentMatchers.any());
        assertThat(loaded.getValue().length, is(4));
        String generatedId = loaded.getValue()[2].getId();
        // and - one WARN names the file, the shadowed and the path problems, but not the STRICT information
        java.util.List<String> warnings = lintWarnings(logged);
        assertThat(String.valueOf(warnings), warnings.size(), is(1));
        String warning = warnings.get(0);
        assertThat(warning, org.hamcrest.Matchers.containsString("expectation lint found 2 problem(s) in the expectations loaded from initialization file:"));
        assertThat(warning, org.hamcrest.Matchers.containsString(file.getAbsolutePath()));
        assertThat(warning, org.hamcrest.Matchers.containsString("SHADOWED expectation 'specific' (any method /orders/1): never matches: 'catch-all' (any method /orders/.*) is evaluated first"));
        assertThat(warning, org.hamcrest.Matchers.containsString("PATH_MISSING_LEADING_SLASH expectation any method users: "));
        assertThat("an id the loader generated is not one the user can find in the file", warning.contains(generatedId), is(false));
        assertThat(warning.contains("STRICT_JSON_BODY_WITH_ARRAYS"), is(false));
    }

    @Test
    public void shouldCompareTheExpectationsInAnInitializerFileByPriorityThenFileOrder() throws Exception {
        // given - the catch-all is listed last but has the higher priority, so it is evaluated first
        File file = initializerFile("[" +
            " { \"id\" : \"specific\", \"httpRequest\" : { \"path\" : \"/items/1\" }, \"httpResponse\" : { \"statusCode\" : 200 } }," +
            " { \"id\" : \"catch-all\", \"priority\" : 10, \"httpRequest\" : { \"path\" : \"/items/.*\" }, \"httpResponse\" : { \"statusCode\" : 201 } }" +
            " ]");
        java.util.List<org.mockserver.log.model.LogEntry> logged = new java.util.concurrent.CopyOnWriteArrayList<>();

        // when
        new ExpectationInitializerLoader(configuration().initializationJsonPath(file.getAbsolutePath()), capturingLogger(logged), mock(RequestMatchers.class));

        // then
        java.util.List<String> warnings = lintWarnings(logged);
        assertThat(String.valueOf(warnings), warnings.size(), is(1));
        assertThat(warnings.get(0), org.hamcrest.Matchers.containsString("SHADOWED expectation 'specific' (any method /items/1): never matches: 'catch-all' (any method /items/.*) is evaluated first (priority 10 > 0)"));
    }

    @Test
    public void shouldNotLogALintWarningForAWellFormedInitializerFile() throws Exception {
        // given - distinct paths, and a STRICT JSON array, which is information only
        File file = initializerFile("[" +
            " { \"id\" : \"one\", \"httpRequest\" : { \"method\" : \"GET\", \"path\" : \"/one\" }, \"httpResponse\" : { \"statusCode\" : 200 } }," +
            " { \"id\" : \"strict\", \"httpRequest\" : { \"path\" : \"/strict\", \"body\" : { \"type\" : \"JSON\", \"json\" : \"{\\\"items\\\":[1]}\", \"matchType\" : \"STRICT\" } }, \"httpResponse\" : { \"statusCode\" : 201 } }" +
            " ]");
        java.util.List<org.mockserver.log.model.LogEntry> logged = new java.util.concurrent.CopyOnWriteArrayList<>();

        // when
        new ExpectationInitializerLoader(configuration().initializationJsonPath(file.getAbsolutePath()), capturingLogger(logged), mock(RequestMatchers.class));

        // then
        assertThat(lintWarnings(logged), org.hamcrest.Matchers.empty());
    }

    @Test
    public void shouldCapTheLintWarningForALargeInitializerFileAndSayWhenTheShadowingCheckStopped() throws Exception {
        // given - 600 regex paths, so every pair is a candidate and the 100,000 comparison budget runs out
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < 600; i++) {
            json.append(i == 0 ? "" : ",").append(" { \"httpRequest\" : { \"path\" : \"").append(i < 25 ? "" : "/").append("p").append(i).append("/.*\" }, \"httpResponse\" : { \"statusCode\" : 200 } }");
        }
        File file = initializerFile(json.append(" ]").toString());
        java.util.List<org.mockserver.log.model.LogEntry> logged = new java.util.concurrent.CopyOnWriteArrayList<>();

        // when
        new ExpectationInitializerLoader(configuration().initializationJsonPath(file.getAbsolutePath()), capturingLogger(logged), mock(RequestMatchers.class));

        // then - one WARN, twenty problems listed, the rest counted
        java.util.List<String> warnings = lintWarnings(logged);
        assertThat(String.valueOf(warnings), warnings.size(), is(1));
        String warning = warnings.get(0);
        assertThat(warning, org.hamcrest.Matchers.containsString("expectation lint found 25 problem(s)"));
        assertThat(warning.split("PATH_MISSING_LEADING_SLASH", -1).length - 1, is(20));
        assertThat(warning, org.hamcrest.Matchers.containsString(" - and 5 more"));
        assertThat(warning, org.hamcrest.Matchers.containsString(" - the shadowing check stopped at its budget, so not every pair of expectations was compared"));
    }

    // a regex like /(a|b)* recurses once per repeated character in java.util.regex, so a long literal path that the
    // shadowing check compares with it overflows a small stack long before the step budget ends
    private static final String DEEP_REGEX_RECURSION = "[" +
        " { \"id\" : \"catch-all\", \"httpRequest\" : { \"path\" : \"/(a|b)*\" }, \"httpResponse\" : { \"statusCode\" : 200 } }," +
        " { \"id\" : \"long\", \"httpRequest\" : { \"path\" : \"/" + new String(new char[6_000]).replace("\0", "ab") + "\" }, \"httpResponse\" : { \"statusCode\" : 201 } }" +
        " ]";

    private static void onASmallStack(Runnable runnable) throws Throwable {
        Throwable[] escaped = new Throwable[1];
        Thread thread = new Thread(null, () -> {
            try {
                runnable.run();
            } catch (Throwable throwable) {
                escaped[0] = throwable;
            }
        }, "small-stack", 512 * 1024);
        thread.start();
        thread.join(TimeUnit.SECONDS.toMillis(120));
        assertThat("finished", thread.isAlive(), is(false));
        if (escaped[0] != null) {
            throw escaped[0];
        }
    }

    @Test
    public void shouldLoadAnInitializerFileWhoseRegexOverflowsTheStackWhenLinted() throws Throwable {
        // given
        File file = initializerFile(DEEP_REGEX_RECURSION);
        java.util.List<org.mockserver.log.model.LogEntry> logged = new java.util.concurrent.CopyOnWriteArrayList<>();
        RequestMatchers requestMatchers = mock(RequestMatchers.class);

        // when
        onASmallStack(() -> new ExpectationInitializerLoader(configuration().initializationJsonPath(file.getAbsolutePath()), capturingLogger(logged), requestMatchers));

        // then - both load, the pair counts as not shadowed (it cannot be proved), and lint itself did not fail
        org.mockito.ArgumentCaptor<Expectation[]> loaded = org.mockito.ArgumentCaptor.forClass(Expectation[].class);
        org.mockito.Mockito.verify(requestMatchers).update(loaded.capture(), org.mockito.ArgumentMatchers.any());
        assertThat(loaded.getValue().length, is(2));
        java.util.List<String> warnings = logged.stream()
            .filter(entry -> entry.getLogLevel() == org.slf4j.event.Level.WARN)
            .map(org.mockserver.log.model.LogEntry::getMessage)
            .collect(java.util.stream.Collectors.toList());
        assertThat(String.valueOf(warnings), warnings, org.hamcrest.Matchers.empty());
    }

    @Test
    public void shouldNotRepeatTheLintWarningWhenAReloadFindsTheSameProblems() throws Exception {
        // given - the file is loaded once at construction
        File file = initializerFile("[ { \"id\" : \"no-slash\", \"httpRequest\" : { \"path\" : \"users\" }, \"httpResponse\" : { \"statusCode\" : 200 } } ]");
        java.util.List<org.mockserver.log.model.LogEntry> logged = new java.util.concurrent.CopyOnWriteArrayList<>();
        ExpectationInitializerLoader loader = new ExpectationInitializerLoader(configuration().initializationJsonPath(file.getAbsolutePath()), capturingLogger(logged), mock(RequestMatchers.class));
        assertThat(lintWarnings(logged).size(), is(1));

        // when - reloaded unchanged, as a --watch reload of another file (or a persisted PUT) does
        loader.retrieveExpectationsFromFile("", "", "", org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause.Type.FILE_INITIALISER);

        // then - no repeat
        assertThat(lintWarnings(logged).size(), is(1));

        // when - the problems change
        Files.write(file.toPath(), "[ { \"id\" : \"no-slash\", \"httpRequest\" : { \"path\" : \"orders\" }, \"httpResponse\" : { \"statusCode\" : 200 } } ]".getBytes(StandardCharsets.UTF_8));
        loader.retrieveExpectationsFromFile("", "", "", org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause.Type.FILE_INITIALISER);

        // then - logged again
        java.util.List<String> warnings = lintWarnings(logged);
        assertThat(warnings.size(), is(2));
        assertThat(warnings.get(1), org.hamcrest.Matchers.containsString("use \"/orders\""));

        // when - fixed, then broken again the same way
        Files.write(file.toPath(), "[ { \"id\" : \"no-slash\", \"httpRequest\" : { \"path\" : \"/orders\" }, \"httpResponse\" : { \"statusCode\" : 200 } } ]".getBytes(StandardCharsets.UTF_8));
        loader.retrieveExpectationsFromFile("", "", "", org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause.Type.FILE_INITIALISER);
        Files.write(file.toPath(), "[ { \"id\" : \"no-slash\", \"httpRequest\" : { \"path\" : \"orders\" }, \"httpResponse\" : { \"statusCode\" : 200 } } ]".getBytes(StandardCharsets.UTF_8));
        loader.retrieveExpectationsFromFile("", "", "", org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause.Type.FILE_INITIALISER);

        // then - the reintroduced problem is reported
        assertThat(lintWarnings(logged).size(), is(3));
    }

}
