package org.mockserver.persistence;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.ui.MockServerMatcherNotifier;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.emptyCollectionOf;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class ExpectationFileWatcherTest {

    private MockServerLogger mockServerLogger;
    private MockServerMatcher mockServerMatcher;

    @Before
    public void createMockServerMatcher() {
        mockServerLogger = new MockServerLogger();
        mockServerMatcher = new MockServerMatcher(mockServerLogger, new Scheduler(mockServerLogger), new WebSocketClientRegistry(mockServerLogger));
    }

    @Test
    public void shouldDetectModifiedInitialiserJsonOnAdd() throws Exception {
        String initializationJsonPath = ConfigurationProperties.initializationJsonPath();
        ConfigurationProperties.watchInitializationJson(true);
        ExpectationFileWatcher expectationFileWatcher = null;
        try {
            // given - configuration
            File mockserverInitialization = File.createTempFile("mockserverInitialization", ".json");
            ConfigurationProperties.initializationJsonPath(mockserverInitialization.getAbsolutePath());
            // and - expectation update notification
            CompletableFuture<String> expectationsUpdated = new CompletableFuture<>();
            mockServerMatcher.registerListener((mockServerMatcher, cause) -> expectationsUpdated.complete("updated"));
            // and - file watcher
            expectationFileWatcher = new ExpectationFileWatcher(mockServerLogger, mockServerMatcher);
            MILLISECONDS.sleep(1500);

            // when
            String watchedFileContents = "[ {" + NEW_LINE +
                "  \"id\" : \"one\"," + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleFirst\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some first response\"" + NEW_LINE +
                "  }" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"id\" : \"two\"," + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleSecond\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some second response\"" + NEW_LINE +
                "  }" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"id\" : \"three\"," + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleThird\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some third response\"" + NEW_LINE +
                "  }" + NEW_LINE +
                "} ]";
            Files.write(mockserverInitialization.toPath(), watchedFileContents.getBytes(StandardCharsets.UTF_8));
            long updatedFileTime = System.currentTimeMillis();

            expectationsUpdated.get(30, SECONDS);
            System.out.println("update processed in: " + (System.currentTimeMillis() - updatedFileTime) + "ms");

            // then
            List<Expectation> expectations = mockServerMatcher.retrieveActiveExpectations(null);
            assertThat(
                expectations,
                contains(
                    new Expectation(
                        request()
                            .withPath("/simpleFirst")
                    )
                        .withId("one")
                        .thenRespond(
                            response()
                                .withBody("some first response")
                        )
                    ,
                    new Expectation(
                        request()
                            .withPath("/simpleSecond")
                    )
                        .withId("two")
                        .thenRespond(
                            response()
                                .withBody("some second response")
                        ),
                    new Expectation(
                        request()
                            .withPath("/simpleThird")
                    )
                        .withId("three")
                        .thenRespond(
                            response()
                                .withBody("some third response")
                        )
                )
            );
        } finally {
            ConfigurationProperties.initializationJsonPath(initializationJsonPath);
            ConfigurationProperties.watchInitializationJson(false);
            if (expectationFileWatcher != null) {
                expectationFileWatcher.stop();
            }
        }
    }

    @Test
    public void shouldDetectModifiedInitialiserJsonOnDeletion() throws Exception {
        String initializationJsonPath = ConfigurationProperties.initializationJsonPath();
        ConfigurationProperties.watchInitializationJson(true);
        ExpectationFileWatcher expectationFileWatcher = null;
        try {
            // given - configuration
            File mockserverInitialization = File.createTempFile("mockserverInitialization", ".json");
            ConfigurationProperties.initializationJsonPath(mockserverInitialization.getAbsolutePath());
            // and - existing file contents
            String watchedFileContents = "[ {" + NEW_LINE +
                "  \"id\" : \"one\"," + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleFirst\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some first response\"" + NEW_LINE +
                "  }" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"id\" : \"two\"," + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleSecond\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some second response\"" + NEW_LINE +
                "  }" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"id\" : \"three\"," + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleThird\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some third response\"" + NEW_LINE +
                "  }" + NEW_LINE +
                "} ]";
            Files.write(mockserverInitialization.toPath(), watchedFileContents.getBytes(StandardCharsets.UTF_8));
            // and - matching existing expectations
            mockServerMatcher.update(new Expectation[]{
                new Expectation(
                    request()
                        .withPath("/simpleFirst")
                )
                    .withId("one")
                    .thenRespond(
                    response()
                        .withBody("some first response")
                )
                ,
                new Expectation(
                    request()
                        .withPath("/simpleSecond")
                )
                    .withId("two")
                    .thenRespond(
                    response()
                        .withBody("some second response")
                ),
                new Expectation(
                    request()
                        .withPath("/simpleThird")
                )
                    .withId("three")
                    .thenRespond(
                    response()
                        .withBody("some third response")
                )
            }, MockServerMatcherNotifier.Cause.FILE_WATCHER);
            // and - expectation update notification
            CompletableFuture<String> expectationsUpdated = new CompletableFuture<>();
            mockServerMatcher.registerListener((mockServerMatcher, cause) -> expectationsUpdated.complete("updated"));
            // and - file watcher
            expectationFileWatcher = new ExpectationFileWatcher(mockServerLogger, mockServerMatcher);
            MILLISECONDS.sleep(1500);

            // when
            watchedFileContents = "[ ]";
            Files.write(mockserverInitialization.toPath(), watchedFileContents.getBytes(StandardCharsets.UTF_8));
            long updatedFileTime = System.currentTimeMillis();

            expectationsUpdated.get(30, SECONDS);
            System.out.println("update processed in: " + (System.currentTimeMillis() - updatedFileTime) + "ms");

            // then
            List<Expectation> expectations = mockServerMatcher.retrieveActiveExpectations(null);
            assertThat(
                expectations,
                emptyCollectionOf(Expectation.class)
            );
        } finally {
            ConfigurationProperties.initializationJsonPath(initializationJsonPath);
            ConfigurationProperties.watchInitializationJson(false);
            if (expectationFileWatcher != null) {
                expectationFileWatcher.stop();
            }
        }
    }

    @Test
    public void shouldDetectModifiedInitialiserJsonOnUpdateNoChange() throws Exception {
        String initializationJsonPath = ConfigurationProperties.initializationJsonPath();
        ConfigurationProperties.watchInitializationJson(true);
        ExpectationFileWatcher expectationFileWatcher = null;
        try {
            // given - configuration
            File mockserverInitialization = File.createTempFile("mockserverInitialization", ".json");
            ConfigurationProperties.initializationJsonPath(mockserverInitialization.getAbsolutePath());
            // and - existing file contents
            String watchedFileContents = "[ {" + NEW_LINE +
                "  \"id\" : \"one\"," + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleFirst\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some first response\"" + NEW_LINE +
                "  }" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"id\" : \"two\"," + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleSecond\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some second response\"" + NEW_LINE +
                "  }" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"id\" : \"three\"," + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleThird\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some third response\"" + NEW_LINE +
                "  }" + NEW_LINE +
                "} ]";
            Files.write(mockserverInitialization.toPath(), watchedFileContents.getBytes(StandardCharsets.UTF_8));
            // and - expectation update notification
            CompletableFuture<String> expectationsUpdated = new CompletableFuture<>();
            mockServerMatcher.registerListener((mockServerMatcher, cause) -> expectationsUpdated.complete("updated"));
            // and - file watcher
            expectationFileWatcher = new ExpectationFileWatcher(mockServerLogger, mockServerMatcher);
            MILLISECONDS.sleep(1500);

            // when
            Files.write(mockserverInitialization.toPath(), watchedFileContents.getBytes(StandardCharsets.UTF_8));
            long updatedFileTime = System.currentTimeMillis();

            expectationsUpdated.get(30, SECONDS);
            System.out.println("update processed in: " + (System.currentTimeMillis() - updatedFileTime) + "ms");

            // then
            List<Expectation> expectations = mockServerMatcher.retrieveActiveExpectations(null);
            assertThat(
                expectations,
                contains(
                    new Expectation(
                        request()
                            .withPath("/simpleFirst")
                    )
                        .withId("one")
                        .thenRespond(
                            response()
                                .withBody("some first response")
                        )
                    ,
                    new Expectation(
                        request()
                            .withPath("/simpleSecond")
                    )
                        .withId("two")
                        .thenRespond(
                            response()
                                .withBody("some second response")
                        ),
                    new Expectation(
                        request()
                            .withPath("/simpleThird")
                    )
                        .withId("three")
                        .thenRespond(
                            response()
                                .withBody("some third response")
                        )
                )
            );
        } finally {
            ConfigurationProperties.initializationJsonPath(initializationJsonPath);
            ConfigurationProperties.watchInitializationJson(false);
            if (expectationFileWatcher != null) {
                expectationFileWatcher.stop();
            }
        }
    }

    @Test
    public void shouldDetectModifiedInitialiserJsonOnUpdateFileChanged() throws Exception {
        String initializationJsonPath = ConfigurationProperties.initializationJsonPath();
        ConfigurationProperties.watchInitializationJson(true);
        ExpectationFileWatcher expectationFileWatcher = null;
        try {
            // given - configuration
            File mockserverInitialization = File.createTempFile("mockserverInitialization", ".json");
            ConfigurationProperties.initializationJsonPath(mockserverInitialization.getAbsolutePath());
            // and - existing file contents
            String watchedFileContents = "[ {" + NEW_LINE +
                "  \"id\" : \"one\"," + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleFirst\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some first response\"" + NEW_LINE +
                "  }" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"id\" : \"two\"," + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleSecond\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some second response\"" + NEW_LINE +
                "  }" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"id\" : \"three\"," + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleThird\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some third response\"" + NEW_LINE +
                "  }" + NEW_LINE +
                "} ]";
            Files.write(mockserverInitialization.toPath(), watchedFileContents.getBytes(StandardCharsets.UTF_8));
            // and - expectation update notification
            CompletableFuture<String> expectationsUpdated = new CompletableFuture<>();
            mockServerMatcher.registerListener((mockServerMatcher, cause) -> expectationsUpdated.complete("updated"));
            // and - file watcher
            expectationFileWatcher = new ExpectationFileWatcher(mockServerLogger, mockServerMatcher);
            MILLISECONDS.sleep(1500);

            // when
            watchedFileContents = "[ {" + NEW_LINE +
                "  \"id\" : \"one\"," + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleFirst\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some first response\"" + NEW_LINE +
                "  }" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"id\" : \"two\"," + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleSecondUpdated\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some second updated response\"" + NEW_LINE +
                "  }" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"id\" : \"four\"," + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"/simpleFourth\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some fourth response\"" + NEW_LINE +
                "  }" + NEW_LINE +
                "} ]";
            Files.write(mockserverInitialization.toPath(), watchedFileContents.getBytes(StandardCharsets.UTF_8));
            long updatedFileTime = System.currentTimeMillis();

            expectationsUpdated.get(30, SECONDS);
            System.out.println("update processed in: " + (System.currentTimeMillis() - updatedFileTime) + "ms");

            // then
            List<Expectation> expectations = mockServerMatcher.retrieveActiveExpectations(null);
            assertThat(
                expectations,
                contains(
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
                )
            );
        } finally {
            ConfigurationProperties.initializationJsonPath(initializationJsonPath);
            ConfigurationProperties.watchInitializationJson(false);
            if (expectationFileWatcher != null) {
                expectationFileWatcher.stop();
            }
        }
    }

}