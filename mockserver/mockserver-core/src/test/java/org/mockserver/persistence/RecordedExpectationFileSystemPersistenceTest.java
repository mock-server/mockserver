package org.mockserver.persistence;

import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.MockServerEventLog;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpState;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.time.EpochService;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.FORWARDED_REQUEST;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class RecordedExpectationFileSystemPersistenceTest {

    @BeforeClass
    public static void fixTime() {
        EpochService.fixedTime = true;
    }

    @Test
    public void shouldPersistRecordedExpectationsOnForwardedRequest() throws Exception {
        // given
        File persistedFile = File.createTempFile("persistedRecordedExpectations", ".json");
        Configuration configuration = configuration()
            .persistRecordedExpectations(true)
            .persistedRecordedExpectationsPath(persistedFile.getAbsolutePath());
        MockServerLogger logger = new MockServerLogger(configuration, RecordedExpectationFileSystemPersistenceTest.class);
        Scheduler scheduler = new Scheduler(configuration, logger);
        HttpState httpState = new HttpState(configuration, logger, scheduler);
        MockServerEventLog mockServerEventLog = httpState.getMockServerLog();
        RecordedExpectationFileSystemPersistence persistence = null;
        try {
            // when
            persistence = new RecordedExpectationFileSystemPersistence(configuration, logger, mockServerEventLog);
            httpState.log(
                new LogEntry()
                    .setType(FORWARDED_REQUEST)
                    .setHttpRequest(request("/api/first").withMethod("GET"))
                    .setHttpResponse(response().withStatusCode(200).withReasonPhrase("OK").withBody("first response"))
                    .setExpectation(new Expectation(request("/api/first"), Times.once(), TimeToLive.unlimited(), 0).thenRespond(response().withStatusCode(200).withReasonPhrase("OK").withBody("first response")))
            );
            httpState.log(
                new LogEntry()
                    .setType(FORWARDED_REQUEST)
                    .setHttpRequest(request("/api/second").withMethod("POST"))
                    .setHttpResponse(response().withStatusCode(201).withReasonPhrase("Created").withBody("second response"))
                    .setExpectation(new Expectation(request("/api/second"), Times.once(), TimeToLive.unlimited(), 0).thenRespond(response().withStatusCode(201).withReasonPhrase("Created").withBody("second response")))
            );
            MILLISECONDS.sleep(3000);

            // then
            String fileContents = new String(Files.readAllBytes(persistedFile.toPath()), StandardCharsets.UTF_8);
            assertThat(fileContents, containsString("/api/first"));
            assertThat(fileContents, containsString("first response"));
            assertThat(fileContents, containsString("/api/second"));
            assertThat(fileContents, containsString("second response"));
        } finally {
            if (persistence != null) {
                persistence.stop();
            }
        }
    }

    @Test
    public void shouldNotPersistWhenDisabled() throws Exception {
        // given
        File persistedFile = File.createTempFile("persistedRecordedExpectations", ".json");
        Configuration configuration = configuration()
            .persistRecordedExpectations(false)
            .persistedRecordedExpectationsPath(persistedFile.getAbsolutePath());
        MockServerLogger logger = new MockServerLogger(configuration, RecordedExpectationFileSystemPersistenceTest.class);
        Scheduler scheduler = new Scheduler(configuration, logger);
        HttpState httpState = new HttpState(configuration, logger, scheduler);
        MockServerEventLog mockServerEventLog = httpState.getMockServerLog();
        RecordedExpectationFileSystemPersistence persistence = null;
        try {
            // when
            persistence = new RecordedExpectationFileSystemPersistence(configuration, logger, mockServerEventLog);
            httpState.log(
                new LogEntry()
                    .setType(FORWARDED_REQUEST)
                    .setHttpRequest(request("/api/test").withMethod("GET"))
                    .setHttpResponse(response().withStatusCode(200).withReasonPhrase("OK"))
                    .setExpectation(new Expectation(request("/api/test"), Times.once(), TimeToLive.unlimited(), 0).thenRespond(response().withStatusCode(200).withReasonPhrase("OK")))
            );
            MILLISECONDS.sleep(1500);

            // then
            String fileContents = new String(Files.readAllBytes(persistedFile.toPath()), StandardCharsets.UTF_8);
            assertThat(fileContents.trim().isEmpty(), is(true));
        } finally {
            if (persistence != null) {
                persistence.stop();
            }
        }
    }
}
