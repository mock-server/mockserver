package org.mockserver.persistence;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.RequestMatchers;
import org.mockserver.mock.listeners.MockServerMatcherNotifier;
import org.mockserver.scheduler.Scheduler;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause.API;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class ExpectationFileSystemPersistenceTest {

    private MockServerLogger mockServerLogger;
    private RequestMatchers requestMatchers;

    @Before
    public void createMockServerMatcher() {
        mockServerLogger = new MockServerLogger();
        requestMatchers = new RequestMatchers(mockServerLogger, new Scheduler(mockServerLogger), new WebSocketClientRegistry(mockServerLogger));
    }

    @Test
    public void shouldPersistExpectationsToJsonOnAdd() throws Exception {
        // given
        String persistedExpectationsPath = ConfigurationProperties.persistedExpectationsPath();
        ConfigurationProperties.persistExpectations(true);
        ExpectationFileSystemPersistence expectationFileSystemPersistence = null;
        try {
            File persistedExpectations = File.createTempFile("persistedExpectations", ".json");
            ConfigurationProperties.persistedExpectationsPath(persistedExpectations.getAbsolutePath());

            // when
            expectationFileSystemPersistence = new ExpectationFileSystemPersistence(mockServerLogger, requestMatchers);
            requestMatchers.add(new Expectation(
                request()
                    .withPath("/simpleFirst")
            )
                .withId("one")
                .thenRespond(
                    response()
                        .withBody("some first response")
                ), API);
            requestMatchers.add(new Expectation(
                request()
                    .withPath("/simpleSecond")
            )
                .withId("two")
                .thenRespond(
                    response()
                        .withBody("some second response")
                ), API);
            requestMatchers.add(new Expectation(
                request()
                    .withPath("/simpleThird")
            )
                .withId("three")
                .thenRespond(
                    response()
                        .withBody("some third response")
                ), API);
            MILLISECONDS.sleep(1500);

            // then
            String expectedFileContents = "[ {" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleFirst\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some first response\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"id\" : \"one\"," + NEW_LINE +
                "  \"priority\" : 0," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleSecond\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some second response\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"id\" : \"two\"," + NEW_LINE +
                "  \"priority\" : 0," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleThird\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some third response\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"id\" : \"three\"," + NEW_LINE +
                "  \"priority\" : 0," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }" + NEW_LINE +
                "} ]";
            assertThat(persistedExpectations.getAbsolutePath() + " does not match expected content", new String(Files.readAllBytes(persistedExpectations.toPath()), StandardCharsets.UTF_8), is(expectedFileContents));
        } finally {
            ConfigurationProperties.persistedExpectationsPath(persistedExpectationsPath);
            ConfigurationProperties.persistExpectations(false);
            if (expectationFileSystemPersistence != null) {
                expectationFileSystemPersistence.stop();
            }
        }
    }

    @Test
    public void shouldPersistExpectationsToJsonOnRemove() throws Exception {
        // given
        String persistedExpectationsPath = ConfigurationProperties.persistedExpectationsPath();
        ConfigurationProperties.persistExpectations(true);
        ExpectationFileSystemPersistence expectationFileSystemPersistence = null;
        try {
            File persistedExpectations = File.createTempFile("persistedExpectations", ".json");
            ConfigurationProperties.persistedExpectationsPath(persistedExpectations.getAbsolutePath());

            // when
            expectationFileSystemPersistence = new ExpectationFileSystemPersistence(mockServerLogger, requestMatchers);
            requestMatchers.add(new Expectation(
                request()
                    .withPath("/simpleFirst")
            )
                .withId("one")
                .thenRespond(
                    response()
                        .withBody("some first response")
                ), API);
            requestMatchers.add(new Expectation(
                request()
                    .withPath("/simpleSecond")
            )
                .withId("two")
                .thenRespond(
                    response()
                        .withBody("some second response")
                ), API);
            requestMatchers.add(new Expectation(
                request()
                    .withPath("/simpleThird")
            )
                .withId("three")
                .thenRespond(
                    response()
                        .withBody("some third response")
                ), API);
            requestMatchers.clear(
                request()
                    .withPath("/simpleSecond")
            );
            MILLISECONDS.sleep(1500);

            // then
            String expectedFileContents = "[ {" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleFirst\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some first response\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"id\" : \"one\"," + NEW_LINE +
                "  \"priority\" : 0," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleThird\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some third response\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"id\" : \"three\"," + NEW_LINE +
                "  \"priority\" : 0," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }" + NEW_LINE +
                "} ]";
            assertThat(persistedExpectations.getAbsolutePath() + " does not match expected content", new String(Files.readAllBytes(persistedExpectations.toPath()), StandardCharsets.UTF_8), is(expectedFileContents));
        } finally {
            ConfigurationProperties.persistedExpectationsPath(persistedExpectationsPath);
            ConfigurationProperties.persistExpectations(false);
            if (expectationFileSystemPersistence != null) {
                expectationFileSystemPersistence.stop();
            }
        }
    }

    @Test
    public void shouldPersistExpectationsToJsonOnUpdate() throws Exception {
        // given
        String persistedExpectationsPath = ConfigurationProperties.persistedExpectationsPath();
        ConfigurationProperties.persistExpectations(true);
        ExpectationFileSystemPersistence expectationFileSystemPersistence = null;
        try {
            File persistedExpectations = File.createTempFile("persistedExpectations", ".json");
            ConfigurationProperties.persistedExpectationsPath(persistedExpectations.getAbsolutePath());

            // when
            expectationFileSystemPersistence = new ExpectationFileSystemPersistence(mockServerLogger, requestMatchers);
            requestMatchers.add(new Expectation(
                request()
                    .withPath("/simpleFirst")
            )
                .withId("one")
                .thenRespond(
                    response()
                        .withBody("some first response")
                ), API);
            requestMatchers.add(new Expectation(
                request()
                    .withPath("/simpleSecond")
            )
                .withId("two")
                .thenRespond(
                    response()
                        .withBody("some second response")
                ), API);
            requestMatchers.add(new Expectation(
                request()
                    .withPath("/simpleThird")
            )
                .withId("three")
                .thenRespond(
                    response()
                        .withBody("some third response")
                ), API);
            requestMatchers.add(new Expectation(
                request()
                    .withPath("/simpleSecondUpdated")
            )
                .withId("two")
                .thenRespond(
                    response()
                        .withBody("some second updated response")
                ), API);
            MILLISECONDS.sleep(1500);

            // then
            String expectedFileContents = "[ {" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleFirst\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some first response\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"id\" : \"one\"," + NEW_LINE +
                "  \"priority\" : 0," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleSecondUpdated\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some second updated response\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"id\" : \"two\"," + NEW_LINE +
                "  \"priority\" : 0," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleThird\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some third response\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"id\" : \"three\"," + NEW_LINE +
                "  \"priority\" : 0," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }" + NEW_LINE +
                "} ]";
            assertThat(persistedExpectations.getAbsolutePath() + " does not match expected content", new String(Files.readAllBytes(persistedExpectations.toPath()), StandardCharsets.UTF_8), is(expectedFileContents));
        } finally {
            ConfigurationProperties.persistedExpectationsPath(persistedExpectationsPath);
            ConfigurationProperties.persistExpectations(false);
            if (expectationFileSystemPersistence != null) {
                expectationFileSystemPersistence.stop();
            }
        }
    }

    @Test
    public void shouldPersistExpectationsToJsonOnUpdateAll() throws Exception {
        // given
        String persistedExpectationsPath = ConfigurationProperties.persistedExpectationsPath();
        ConfigurationProperties.persistExpectations(true);
        ExpectationFileSystemPersistence expectationFileSystemPersistence = null;
        try {
            File persistedExpectations = File.createTempFile("persistedExpectations", ".json");
            ConfigurationProperties.persistedExpectationsPath(persistedExpectations.getAbsolutePath());

            // when
            expectationFileSystemPersistence = new ExpectationFileSystemPersistence(mockServerLogger, requestMatchers);
            requestMatchers.add(new Expectation(
                request()
                    .withPath("/simpleFirst")
            )
                .withId("one")
                .thenRespond(
                    response()
                        .withBody("some first response")
                ), API);
            requestMatchers.add(new Expectation(
                request()
                    .withPath("/simpleSecond")
            )
                .withId("two")
                .thenRespond(
                    response()
                        .withBody("some second response")
                ), API);
            requestMatchers.add(new Expectation(
                request()
                    .withPath("/simpleThird")
            )
                .withId("three")
                .thenRespond(
                    response()
                        .withBody("some third response")
                ), API);
            requestMatchers.update(new Expectation[]{
                new Expectation(
                    request()
                        .withPath("/simpleFirst")
                )
                    .withId("one")
                    .thenRespond(
                    response()
                        .withBody("some first response")
                ),
                new Expectation(
                    request()
                        .withPath("/simpleSecondUpdated")
                )
                    .withId("two")
                    .thenRespond(
                    response()
                        .withBody("some second updated response")
                ),
                new Expectation(
                    request()
                        .withPath("/simpleFourth")
                )
                    .withId("four")
                    .thenRespond(
                    response()
                        .withBody("some fourth response")
                )
            }, API);
            MILLISECONDS.sleep(1500);

            // then
            String expectedFileContents = "[ {" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleFirst\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some first response\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"id\" : \"one\"," + NEW_LINE +
                "  \"priority\" : 0," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleSecondUpdated\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some second updated response\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"id\" : \"two\"," + NEW_LINE +
                "  \"priority\" : 0," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleFourth\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some fourth response\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"id\" : \"four\"," + NEW_LINE +
                "  \"priority\" : 0," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }" + NEW_LINE +
                "} ]";
            assertThat(persistedExpectations.getAbsolutePath() + " does not match expected content", new String(Files.readAllBytes(persistedExpectations.toPath()), StandardCharsets.UTF_8), is(expectedFileContents));
        } finally {
            ConfigurationProperties.persistedExpectationsPath(persistedExpectationsPath);
            ConfigurationProperties.persistExpectations(false);
            if (expectationFileSystemPersistence != null) {
                expectationFileSystemPersistence.stop();
            }
        }
    }

    @Test
    public void shouldPersistExpectationsToJsonOnUpdateAllFromFileWatcher() throws Exception {
        // given
        String persistedExpectationsPath = ConfigurationProperties.persistedExpectationsPath();
        ConfigurationProperties.persistExpectations(true);
        String initializationJsonPath = ConfigurationProperties.initializationJsonPath();
        ConfigurationProperties.watchInitializationJson(true);
        ExpectationFileSystemPersistence expectationFileSystemPersistence = null;
        try {
            File persistedExpectations = File.createTempFile("persistedExpectations", ".json");
            ConfigurationProperties.persistedExpectationsPath(persistedExpectations.getAbsolutePath());
            ConfigurationProperties.initializationJsonPath(persistedExpectations.getAbsolutePath());

            // when
            expectationFileSystemPersistence = new ExpectationFileSystemPersistence(mockServerLogger, requestMatchers);
            requestMatchers.add(new Expectation(
                request()
                    .withPath("/simpleFirst")
            )
                .withId("one")
                .thenRespond(
                    response()
                        .withBody("some first response")
                ), API);
            requestMatchers.add(new Expectation(
                request()
                    .withPath("/simpleSecond")
            )
                .withId("two")
                .thenRespond(
                    response()
                        .withBody("some second response")
                ), API);
            requestMatchers.add(new Expectation(
                request()
                    .withPath("/simpleThird")
            )
                .withId("three")
                .thenRespond(
                    response()
                        .withBody("some third response")
                ), API);
            MILLISECONDS.sleep(1500);
            requestMatchers.update(new Expectation[]{
                new Expectation(
                    request()
                        .withPath("/simpleFirst")
                )
                    .withId("one")
                    .thenRespond(
                    response()
                        .withBody("some first response")
                ),
                new Expectation(
                    request()
                        .withPath("/simpleSecondUpdated")
                )
                    .withId("two")
                    .thenRespond(
                    response()
                        .withBody("some second updated response")
                ),
                new Expectation(
                    request()
                        .withPath("/simpleFourth")
                )
                    .withId("four")
                    .thenRespond(
                    response()
                        .withBody("some fourth response")
                )
            }, new MockServerMatcherNotifier.Cause(persistedExpectations.getAbsolutePath(), MockServerMatcherNotifier.Cause.Type.FILE_WATCHER));
            MILLISECONDS.sleep(1500);

            // then
            String expectedFileContents = "[ {" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleFirst\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some first response\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"id\" : \"one\"," + NEW_LINE +
                "  \"priority\" : 0," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleSecond\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some second response\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"id\" : \"two\"," + NEW_LINE +
                "  \"priority\" : 0," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleThird\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some third response\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"id\" : \"three\"," + NEW_LINE +
                "  \"priority\" : 0," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }" + NEW_LINE +
                "} ]";
            assertThat(persistedExpectations.getAbsolutePath() + " does not match expected content", new String(Files.readAllBytes(persistedExpectations.toPath()), StandardCharsets.UTF_8), is(expectedFileContents));
        } finally {
            ConfigurationProperties.persistedExpectationsPath(persistedExpectationsPath);
            ConfigurationProperties.persistExpectations(false);
            ConfigurationProperties.initializationJsonPath(initializationJsonPath);
            ConfigurationProperties.watchInitializationJson(false);
            if (expectationFileSystemPersistence != null) {
                expectationFileSystemPersistence.stop();
            }
        }
    }

}