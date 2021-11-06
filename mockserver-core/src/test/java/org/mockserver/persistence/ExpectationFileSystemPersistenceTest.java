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
import java.util.*;

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
                "  \"id\" : \"one\"," + NEW_LINE +
                "  \"priority\" : 0," + NEW_LINE +
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
                "  \"priority\" : 0," + NEW_LINE +
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
                "  \"priority\" : 0," + NEW_LINE +
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
                "  \"id\" : \"one\"," + NEW_LINE +
                "  \"priority\" : 0," + NEW_LINE +
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
                "  \"id\" : \"three\"," + NEW_LINE +
                "  \"priority\" : 0," + NEW_LINE +
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
            assertThat(persistedExpectations.getAbsolutePath() + " does not match expected content", match(new String(Files.readAllBytes(persistedExpectations.toPath()), StandardCharsets.UTF_8)));
        } finally {
            ConfigurationProperties.persistedExpectationsPath(persistedExpectationsPath);
            ConfigurationProperties.persistExpectations(false);
            if (expectationFileSystemPersistence != null) {
                expectationFileSystemPersistence.stop();
            }
        }
    }

    private boolean match(String actualFileContent) {
        String prefix = "[ {" + NEW_LINE;
        String middle = "}, {" + NEW_LINE;
        String suffix = "} ]";

        String id1 = "  \"id\" : \"one\"";
        String priority1 = "  \"priority\" : 0";
        String httpRequest1 = "  \"httpRequest\" : {" + NEW_LINE +
        "    \"path\" : \"/simpleFirst\"" + NEW_LINE +
        "  }";
        String times1 = "  \"times\" : {" + NEW_LINE +
        "    \"unlimited\" : true" + NEW_LINE +
        "  }";
        String timeToLive1 = "  \"timeToLive\" : {" + NEW_LINE +
        "    \"unlimited\" : true" + NEW_LINE +
        "  }";
        String httpResponse1 = "  \"httpResponse\" : {" + NEW_LINE +
        "    \"body\" : \"some first response\"" + NEW_LINE +
        "  }";

        String id2 = "  \"id\" : \"two\"";
        String priority2 = "  \"priority\" : 0";
        String httpRequest2 = "  \"httpRequest\" : {" + NEW_LINE +
        "    \"path\" : \"/simpleSecondUpdated\"" + NEW_LINE +
        "  }";
        String times2 = "  \"times\" : {" + NEW_LINE +
        "    \"unlimited\" : true" + NEW_LINE +
        "  }";
        String timeToLive2 = "  \"timeToLive\" : {" + NEW_LINE +
        "    \"unlimited\" : true" + NEW_LINE +
        "  }";
        String httpResponse2 = "  \"httpResponse\" : {" + NEW_LINE +
        "    \"body\" : \"some second updated response\"" + NEW_LINE +
        "  }";

        String id3 = "  \"id\" : \"three\"";
        String priority3 = "  \"priority\" : 0";
        String httpRequest3 = "  \"httpRequest\" : {" + NEW_LINE +
        "    \"path\" : \"/simpleThird\"" + NEW_LINE +
        "  }";
        String times3 = "  \"times\" : {" + NEW_LINE +
        "    \"unlimited\" : true" + NEW_LINE +
        "  }";
        String timeToLive3 = "  \"timeToLive\" : {" + NEW_LINE +
        "    \"unlimited\" : true" + NEW_LINE +
        "  }";
        String httpResponse3 = "  \"httpResponse\" : {" + NEW_LINE +
        "    \"body\" : \"some third response\"" + NEW_LINE +
        "  }";

        String[] arr1 = {id1, priority1, httpRequest1, times1, timeToLive1, httpResponse1};
        String[] arr2 = {id2, priority2, httpRequest2, times2, timeToLive2, httpResponse2};
        String[] arr3 = {id3, priority3, httpRequest3, times3, timeToLive3, httpResponse3};
        Set<String> expectedFileContents1 = getAllPossibleFileContents(arr1);
        Set<String> expectedFileContents2 = getAllPossibleFileContents(arr2);
        Set<String> expectedFileContents3 = getAllPossibleFileContents(arr3);
        for (String content1 : expectedFileContents1) {
            for (String content2 : expectedFileContents2) {
                for (String content3 : expectedFileContents3) {
                    if (actualFileContent.equals(prefix + content1 + middle + content2 + middle + content3 + suffix)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private Set<String> getAllPossibleFileContents(String[] arr) {
        List<List<String>> allPermutations = new ArrayList<>();
        List<String> currPermutation = new ArrayList<>();
        dfs(arr, allPermutations, currPermutation, 0);
        return buildAllPossibleFileContents(allPermutations);
    }

    private Set<String> buildAllPossibleFileContents(List<List<String>> allPermutations) {
        Set<String> set = new HashSet<>();
        for (List<String> list : allPermutations) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                sb.append(list.get(i));
                if (i != list.size() - 1) {
                    sb.append("," + NEW_LINE);
                } else {
                    sb.append(NEW_LINE);
                }
            }
            set.add(sb.toString());
        }
        return set;
    }

    private void dfs(String[] arr, List<List<String>> res, List<String> list, int index) {
        if (index == arr.length) {
            res.add(new ArrayList<>(list));
            return;
        }
        for (int i = index; i < arr.length; i++) {
            swap(arr, index, i);
            list.add(arr[index]);
            dfs(arr, res, list, index + 1);
            list.remove(list.size() - 1);
            swap(arr, index, i);
        }
    }

    private void swap(String[] arr, int i, int j) {
        String tmp = arr[i];
        arr[i] = arr[j];
        arr[j] = tmp;
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
                "  \"id\" : \"one\"," + NEW_LINE +
                "  \"priority\" : 0," + NEW_LINE +
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
                "  \"priority\" : 0," + NEW_LINE +
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
                "  \"priority\" : 0," + NEW_LINE +
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
            }, MockServerMatcherNotifier.Cause.FILE_WATCHER);
            MILLISECONDS.sleep(1500);

            // then
            String expectedFileContents = "[ {" + NEW_LINE +
                "  \"id\" : \"one\"," + NEW_LINE +
                "  \"priority\" : 0," + NEW_LINE +
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
                "  \"priority\" : 0," + NEW_LINE +
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
                "  \"priority\" : 0," + NEW_LINE +
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