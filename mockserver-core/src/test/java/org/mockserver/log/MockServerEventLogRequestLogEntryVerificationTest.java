package org.mockserver.log;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;

import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.TimeUnit.SECONDS;
import static junit.framework.TestCase.fail;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.verify.Verification.verification;
import static org.mockserver.verify.VerificationTimes.atLeast;
import static org.mockserver.verify.VerificationTimes.exactly;

/**
 * @author jamesdbloom
 */
public class MockServerEventLogRequestLogEntryVerificationTest {

    private static final Scheduler scheduler = new Scheduler(configuration(), new MockServerLogger());
    private MockServerEventLog mockServerEventLog;

    @Before
    public void setupTestFixture() {
        mockServerEventLog = new MockServerEventLog(configuration(), new MockServerLogger(), scheduler, true);
    }

    @AfterClass
    public static void stopScheduler() {
        scheduler.shutdown();
    }

    public String verify(Verification verification) {
        CompletableFuture<String> result = new CompletableFuture<>();
        mockServerEventLog.verify(verification, result::complete);
        try {
            return result.get(10, SECONDS);
        } catch (Exception e) {
            fail(e.getMessage());
            return null;
        }
    }

    public String verify(VerificationSequence verificationSequence) {
        CompletableFuture<String> result = new CompletableFuture<>();
        mockServerEventLog.verify(verificationSequence, result::complete);
        try {
            return result.get(10, SECONDS);
        } catch (Exception e) {
            fail(e.getMessage());
            return null;
        }
    }

    @Test
    public void shouldPassVerificationWithNullRequest() {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");

        // when
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(otherHttpRequest)
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );

        // then
        assertThat(verify((Verification) null), is(""));
    }

    @Test
    public void shouldPassVerificationWithDefaultTimes() {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");

        // when
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(otherHttpRequest)
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );

        // then
        assertThat(verify(
                verification()
                    .withRequest(
                        new HttpRequest()
                            .withPath("some_path")
                    )
            ),
            is(""));
        assertThat(verify(
                verification()
                    .withRequest(
                        new HttpRequest()
                            .withPath("some_other_path")
                    )
            ),
            is(""));
    }

    @Test
    public void shouldPassVerificationWithAtLeastTwoTimes() {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");

        // when
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(otherHttpRequest)
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );

        // then
        assertThat(verify(
                verification()
                    .withRequest(
                        new HttpRequest().withPath("some_path")
                    )
                    .withTimes(atLeast(2))
            ),
            is(""));
    }

    @Test
    public void shouldPassVerificationWithAtLeastZeroTimes() {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");

        // when
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(otherHttpRequest)
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );

        // then
        assertThat(verify(
                verification()
                    .withRequest(
                        new HttpRequest().withPath("some_non_matching_path")
                    )
                    .withTimes(atLeast(0))
            ),
            is(""));
    }

    @Test
    public void shouldPassVerificationWithExactlyTwoTimes() {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");

        // when
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(otherHttpRequest)
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );

        // then
        assertThat(verify(
                verification()
                    .withRequest(
                        new HttpRequest()
                            .withPath("some_path")
                    )
                    .withTimes(exactly(2))
            ),
            is(""));
    }

    @Test
    public void shouldPassVerificationWithExactlyZeroTimes() {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");

        // when
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(otherHttpRequest)
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );

        // then
        assertThat(verify(
                verification()
                    .withRequest(
                        new HttpRequest()
                            .withPath("some_non_matching_path")
                    )
                    .withTimes(exactly(0))
            ),
            is(""));
    }

    @Test
    public void shouldFailVerificationWithNullRequest() {
        // given

        // then
        assertThat(verify((Verification) null), is(""));
    }

    @Test
    public void shouldFailVerificationWithDefaultTimes() {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");

        // when
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(otherHttpRequest)
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );

        // then
        assertThat(verify(
                verification()
                    .withRequest(
                        new HttpRequest().withPath("some_non_matching_path")
                    )
            ),
            is("Request not found at least once, expected:<{" + NEW_LINE +
                "  \"path\" : \"some_non_matching_path\"" + NEW_LINE +
                "}> but was:<[ {" + NEW_LINE +
                "  \"path\" : \"some_path\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"some_other_path\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"some_path\"" + NEW_LINE +
                "} ]>"));
    }

    @Test
    public void shouldFailVerificationWithAtLeastTwoTimes() {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");

        // when
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(otherHttpRequest)
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );

        // then
        assertThat(verify(
                verification()
                    .withRequest(
                        new HttpRequest().withPath("some_other_path")
                    )
                    .withTimes(atLeast(2))
            ),
            is("Request not found at least 2 times, expected:<{" + NEW_LINE +
                "  \"path\" : \"some_other_path\"" + NEW_LINE +
                "}> but was:<[ {" + NEW_LINE +
                "  \"path\" : \"some_path\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"some_other_path\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"some_path\"" + NEW_LINE +
                "} ]>"));
    }

    @Test
    public void shouldFailVerificationWithLimitedReturnedRequests() {
        Integer originalMaximumNumberOfRequestToReturnInVerificationFailure = ConfigurationProperties.maximumNumberOfRequestToReturnInVerificationFailure();
        try {
            // given
            HttpRequest httpRequest = new HttpRequest().withPath("some_path");
            HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");
            ConfigurationProperties.maximumNumberOfRequestToReturnInVerificationFailure(1);

            // when
            mockServerEventLog.add(
                new LogEntry()
                    .setHttpRequest(httpRequest)
                    .setType(RECEIVED_REQUEST)
            );
            mockServerEventLog.add(
                new LogEntry()
                    .setHttpRequest(otherHttpRequest)
                    .setType(RECEIVED_REQUEST)
            );
            mockServerEventLog.add(
                new LogEntry()
                    .setHttpRequest(httpRequest)
                    .setType(RECEIVED_REQUEST)
            );

            // then
            assertThat(verify(
                    verification()
                        .withRequest(
                            new HttpRequest().withPath("some_other_path")
                        )
                        .withTimes(atLeast(2))
                ),
                is("Request not found at least 2 times, expected:<{" + NEW_LINE +
                    "  \"path\" : \"some_other_path\"" + NEW_LINE +
                    "}> but was not found, found 3 other requests"));
        } finally {
            ConfigurationProperties.maximumNumberOfRequestToReturnInVerificationFailure(originalMaximumNumberOfRequestToReturnInVerificationFailure);
        }
    }

    @Test
    public void shouldFailVerificationWithExactTwoTimes() {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");

        // when
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(otherHttpRequest)
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );

        // then
        assertThat(verify(
                verification()
                    .withRequest(
                        new HttpRequest()
                            .withPath("some_other_path")
                    )
                    .withTimes(exactly(2))
            ),
            is("Request not found exactly 2 times, expected:<{" + NEW_LINE +
                "  \"path\" : \"some_other_path\"" + NEW_LINE +
                "}> but was:<[ {" + NEW_LINE +
                "  \"path\" : \"some_path\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"some_other_path\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"some_path\"" + NEW_LINE +
                "} ]>"));
    }

    @Test
    public void shouldFailVerificationWithExactOneTime() {
        // given

        // then
        assertThat(verify(
                verification()
                    .withRequest(
                        new HttpRequest()
                            .withPath("some_other_path")
                    )
                    .withTimes(exactly(1))
            ),
            is("Request not found exactly once, expected:<{" + NEW_LINE +
                "  \"path\" : \"some_other_path\"" + NEW_LINE +
                "}> but was:<[]>"));
    }

    @Test
    public void shouldFailVerificationWithExactZeroTimes() {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");

        // when
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(otherHttpRequest)
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );

        // then
        assertThat(verify(
                verification()
                    .withRequest(
                        new HttpRequest()
                            .withPath("some_other_path")
                    )
                    .withTimes(exactly(0))
            ),
            is("Request not found exactly 0 times, expected:<{" + NEW_LINE +
                "  \"path\" : \"some_other_path\"" + NEW_LINE +
                "}> but was:<[ {" + NEW_LINE +
                "  \"path\" : \"some_path\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"some_other_path\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"some_path\"" + NEW_LINE +
                "} ]>"));
    }

    @Test
    public void shouldFailVerificationWithNoInteractions() {
        // given
        HttpRequest httpRequest = new HttpRequest();

        // when
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );

        // then
        assertThat(verify(
                verification()
                    .withRequest(request())
                    .withTimes(exactly(0))
            ),
            is("Request not found exactly 0 times, expected:<{ }> but was:<{ }>"));
    }
}
