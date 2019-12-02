package org.mockserver.persistence;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.callback.WebSocketClientRegistry;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.scheduler.Scheduler;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class ExpectationFileSystemPersistenceTest {

    private MockServerLogger mockServerLogger;
    private MockServerMatcher mockServerMatcher;

    @Before
    public void createMockServerMatcher() {
        mockServerLogger = new MockServerLogger();
        mockServerMatcher = new MockServerMatcher(mockServerLogger, new Scheduler(mockServerLogger), new WebSocketClientRegistry(mockServerLogger));
    }

    @Test
    public void shouldPersistExpectationsToJson() throws IOException, InterruptedException {
        // given
        String persistedExpectationsPath = ConfigurationProperties.persistedExpectationsPath();
        ConfigurationProperties.persistExpectations(true);
        try {
            File persistedExpectations = File.createTempFile("persistedExpectations", "json");
            ConfigurationProperties.persistedExpectationsPath(persistedExpectations.getAbsolutePath());

            // when
            new ExpectationFileSystemPersistence(mockServerLogger, mockServerMatcher);
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
            for (Expectation expectation : expectations) {
                mockServerMatcher.add(expectation);
            }
            MILLISECONDS.sleep(1500);

            // then
            String expectedFileContents = "[ {" + NEW_LINE +
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
                "} ]";
            assertThat(new String(Files.readAllBytes(persistedExpectations.toPath()), StandardCharsets.UTF_8), is(expectedFileContents));
        } finally {
            ConfigurationProperties.initializationJsonPath(persistedExpectationsPath);
            ConfigurationProperties.persistExpectations(false);
        }
    }

}