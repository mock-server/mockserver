package org.mockserver.persistence;

import org.junit.*;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.RequestMatchers;
import org.mockserver.mock.listeners.MockServerMatcherNotifier;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ExpectationSerializer;
import org.mockserver.test.Retries;
import org.mockserver.uuid.UUIDService;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.collection.IsEmptyCollection.emptyCollectionOf;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@Ignore
public class ExpectationFileWatcherTest {

    private final ExpectationSerializer expectationSerializer = new ExpectationSerializer(new MockServerLogger());
    private MockServerLogger mockServerLogger;
    private RequestMatchers requestMatchers;
    private static long originalPollPeriod;
    private static TimeUnit originalPollPeriodUnits;

    @BeforeClass
    public static void increasePollPeriod() {
        originalPollPeriod = FileWatcher.getPollPeriod();
        originalPollPeriodUnits = FileWatcher.getPollPeriodUnits();
        FileWatcher.setPollPeriod(500);
        FileWatcher.setPollPeriodUnits(MILLISECONDS);
    }

    @AfterClass
    public static void restorePollPeriod() {
        FileWatcher.setPollPeriod(originalPollPeriod);
        FileWatcher.setPollPeriodUnits(originalPollPeriodUnits);
    }

    @Before
    public void createMockServerMatcher() {
        mockServerLogger = new MockServerLogger();
        requestMatchers = new RequestMatchers(mockServerLogger, new Scheduler(mockServerLogger), new WebSocketClientRegistry(mockServerLogger));
    }

    @Test
    public void shouldDetectModifiedInitialiserJsonInWorkingDirectoryThatDoesNotInitiallyExist() throws Exception {
        String initializationJsonPath = ConfigurationProperties.initializationJsonPath();
        ConfigurationProperties.watchInitializationJson(true);
        ExpectationFileWatcher expectationFileWatcher = null;
        try {
            // given - configuration
            File mockserverInitialization = new File("mockserverInitialization" + UUIDService.getUUID() + ".json");
            mockserverInitialization.deleteOnExit();
            ConfigurationProperties.initializationJsonPath(mockserverInitialization.getPath());
            // and - expectation update notification
            CompletableFuture<String> expectationsUpdated = new CompletableFuture<>();
            requestMatchers.registerListener((requestMatchers, cause) -> expectationsUpdated.complete("updated"));
            // and - file watcher
            expectationFileWatcher = new ExpectationFileWatcher(mockServerLogger, requestMatchers);
            assertThat(mockserverInitialization.exists(), equalTo(false));

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
            List<Expectation> expectations = requestMatchers.retrieveActiveExpectations(null);
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
            requestMatchers.registerListener((requestMatchers, cause) -> expectationsUpdated.complete("updated"));
            // and - file watcher
            expectationFileWatcher = new ExpectationFileWatcher(mockServerLogger, requestMatchers);

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
            List<Expectation> expectations = requestMatchers.retrieveActiveExpectations(null);
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
    public void shouldDetectModifiedInitialiserJsonOnAddWithGlob() throws Exception {
        String initializationJsonPath = ConfigurationProperties.initializationJsonPath();
        ConfigurationProperties.watchInitializationJson(true);
        ExpectationFileWatcher expectationFileWatcher = null;
        try {
            // given - configuration
            String uniquePrefix = UUID.randomUUID().toString();
            File mockserverInitializer = File.createTempFile(uniquePrefix + "_mockserverInitialization", ".json");
            File mockserverInitializerOne = File.createTempFile(uniquePrefix + "_mockserverInitializationOne", ".json");
            File mockserverInitializerTwo = File.createTempFile(uniquePrefix + "_mockserverInitializationTwo", ".json");
            File mockserverInitializerThree = File.createTempFile(uniquePrefix + "_mockserverInitializationThree", ".json");
            ConfigurationProperties.initializationJsonPath(mockserverInitializer.getParentFile().getAbsolutePath() + "/" + uniquePrefix + "_mockserverInitialization{One,Two}*.json");
            // and - expectation update notification
            AtomicInteger expectationsUpdatedCount = new AtomicInteger();
            requestMatchers.registerListener((requestMatchers, cause) -> expectationsUpdatedCount.incrementAndGet());
            // and - file watcher
            expectationFileWatcher = new ExpectationFileWatcher(mockServerLogger, requestMatchers);

            // when
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
            Files.write(mockserverInitializerThree.toPath(), expectationSerializer.serialize(expectionsThree).getBytes(StandardCharsets.UTF_8));
            long updatedFileTime = System.currentTimeMillis();

            Retries.tryWaitForSuccess(() -> assertThat(expectationsUpdatedCount.get(), equalTo(2)), 150, 100, MILLISECONDS);
            System.out.println("update processed in: " + (System.currentTimeMillis() - updatedFileTime) + "ms");

            // then
            List<Expectation> expectations = requestMatchers.retrieveActiveExpectations(null);
            assertThat(expectations.size(), equalTo(4));
            assertThat(expectations, containsInAnyOrder(
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
            ));
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
            requestMatchers.update(new Expectation[]{
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
            }, new MockServerMatcherNotifier.Cause(mockserverInitialization.getAbsolutePath(), MockServerMatcherNotifier.Cause.Type.FILE_WATCHER));
            // and - expectation update notification
            CompletableFuture<String> expectationsUpdated = new CompletableFuture<>();
            requestMatchers.registerListener((requestMatchers, cause) -> expectationsUpdated.complete("updated"));
            // and - file watcher
            expectationFileWatcher = new ExpectationFileWatcher(mockServerLogger, requestMatchers);

            // when
            watchedFileContents = "[ ]";
            Files.write(mockserverInitialization.toPath(), watchedFileContents.getBytes(StandardCharsets.UTF_8));
            long updatedFileTime = System.currentTimeMillis();

            expectationsUpdated.get(30, SECONDS);
            System.out.println("update processed in: " + (System.currentTimeMillis() - updatedFileTime) + "ms");

            // then
            List<Expectation> expectations = requestMatchers.retrieveActiveExpectations(null);
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
            requestMatchers.registerListener((requestMatchers, cause) -> expectationsUpdated.complete("updated"));
            // and - file watcher
            expectationFileWatcher = new ExpectationFileWatcher(mockServerLogger, requestMatchers);

            // when
            Files.write(mockserverInitialization.toPath(), watchedFileContents.getBytes(StandardCharsets.UTF_8));
            long updatedFileTime = System.currentTimeMillis();

            expectationsUpdated.get(30, SECONDS);
            System.out.println("update processed in: " + (System.currentTimeMillis() - updatedFileTime) + "ms");

            // then
            List<Expectation> expectations = requestMatchers.retrieveActiveExpectations(null);
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
            requestMatchers.registerListener((requestMatchers, cause) -> expectationsUpdated.complete("updated"));
            // and - file watcher
            expectationFileWatcher = new ExpectationFileWatcher(mockServerLogger, requestMatchers);

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
            List<Expectation> expectations = requestMatchers.retrieveActiveExpectations(null);
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