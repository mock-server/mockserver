package org.mockserver.log;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
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

/**
 * @author jamesdbloom
 */
public class MockServerEventLogRequestLogEntryVerificationSequenceTest {

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
        // when
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("one"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("three"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("four"))
                .setType(RECEIVED_REQUEST)
        );

        // then
        assertThat(verify((VerificationSequence) null), is(""));
    }

    @Test
    public void shouldPassVerificationSequenceWithNoRequest() {
        // when
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("one"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("three"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("four"))
                .setType(RECEIVED_REQUEST)
        );

        // then
        assertThat(verify(
                new VerificationSequence()
                    .withRequests(

                    )
            ),
            is(""));
    }

    @Test
    public void shouldPassVerificationSequenceWithOneRequest() {
        // when
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("one"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("three"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("four"))
                .setType(RECEIVED_REQUEST)
        );

        // then
        assertThat(verify(
                new VerificationSequence()
                    .withRequests(
                        request("one")
                    )
            ),
            is(""));
        assertThat(verify(
                new VerificationSequence()
                    .withRequests(
                        request("multi")
                    )
            ),
            is(""));
        assertThat(verify(
                new VerificationSequence()
                    .withRequests(
                        request("three")
                    )
            ),
            is(""));
        assertThat(verify(
                new VerificationSequence()
                    .withRequests(
                        request("four")
                    )
            ),
            is(""));
    }

    @Test
    public void shouldPassVerificationSequenceWithTwoRequests() {
        // when
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("one"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("three"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("four"))
                .setType(RECEIVED_REQUEST)
        );

        // then - next to each other
        assertThat(verify(
                new VerificationSequence()
                    .withRequests(
                        request("one"),
                        request("multi")
                    )
            ),
            is(""));
        assertThat(verify(
                new VerificationSequence()
                    .withRequests(
                        request("multi"),
                        request("three")
                    )
            ),
            is(""));
        assertThat(verify(
                new VerificationSequence()
                    .withRequests(
                        request("three"),
                        request("multi")
                    )
            ),
            is(""));
        assertThat(verify(
                new VerificationSequence()
                    .withRequests(
                        request("multi"),
                        request("four")
                    )
            ),
            is(""));
        // then - not next to each other
        assertThat(verify(
                new VerificationSequence()
                    .withRequests(
                        request("one"),
                        request("three")
                    )
            ),
            is(""));
        assertThat(verify(
                new VerificationSequence()
                    .withRequests(
                        request("one"),
                        request("four")
                    )
            ),
            is(""));
        assertThat(verify(
                new VerificationSequence()
                    .withRequests(
                        request("multi"),
                        request("multi")
                    )
            ),
            is(""));
        assertThat(verify(
                new VerificationSequence()
                    .withRequests(
                        request("three"),
                        request("four")
                    )
            ),
            is(""));
    }

    @Test
    public void shouldFailVerificationSequenceWithOneRequest() {
        // when
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("one"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("three"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("four"))
                .setType(RECEIVED_REQUEST)
        );

        // then
        assertThat(verify(
                new VerificationSequence()
                    .withRequests(
                        request("five")
                    )
            ),
            is("Request sequence not found, expected:<[ {" + NEW_LINE +
                "  \"path\" : \"five\"" + NEW_LINE +
                "} ]> but was:<[ {" + NEW_LINE +
                "  \"path\" : \"one\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"three\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"four\"" + NEW_LINE +
                "} ]>"));
    }

    @Test
    public void shouldFailVerificationSequenceWithTwoRequestsWrongOrder() {
        // when
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("one"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("three"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("four"))
                .setType(RECEIVED_REQUEST)
        );

        // then - next to each other
        assertThat(verify(
                new VerificationSequence()
                    .withRequests(
                        request("multi"),
                        request("one")
                    )
            ),
            is("Request sequence not found, expected:<[ {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"one\"" + NEW_LINE +
                "} ]> but was:<[ {" + NEW_LINE +
                "  \"path\" : \"one\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"three\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"four\"" + NEW_LINE +
                "} ]>"));
        assertThat(verify(
                new VerificationSequence()
                    .withRequests(
                        request("four"),
                        request("multi")
                    )
            ),
            is("Request sequence not found, expected:<[ {" + NEW_LINE +
                "  \"path\" : \"four\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "} ]> but was:<[ {" + NEW_LINE +
                "  \"path\" : \"one\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"three\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"four\"" + NEW_LINE +
                "} ]>"));
        // then - not next to each other
        assertThat(verify(
                new VerificationSequence()
                    .withRequests(
                        request("three"),
                        request("one")
                    )
            ),
            is("Request sequence not found, expected:<[ {" + NEW_LINE +
                "  \"path\" : \"three\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"one\"" + NEW_LINE +
                "} ]> but was:<[ {" + NEW_LINE +
                "  \"path\" : \"one\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"three\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"four\"" + NEW_LINE +
                "} ]>"));
        assertThat(verify(
                new VerificationSequence()
                    .withRequests(
                        request("four"),
                        request("one")
                    )
            ),
            is("Request sequence not found, expected:<[ {" + NEW_LINE +
                "  \"path\" : \"four\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"one\"" + NEW_LINE +
                "} ]> but was:<[ {" + NEW_LINE +
                "  \"path\" : \"one\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"three\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"four\"" + NEW_LINE +
                "} ]>"));
        assertThat(verify(
                new VerificationSequence()
                    .withRequests(
                        request("four"),
                        request("three")
                    )
            ),
            is("Request sequence not found, expected:<[ {" + NEW_LINE +
                "  \"path\" : \"four\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"three\"" + NEW_LINE +
                "} ]> but was:<[ {" + NEW_LINE +
                "  \"path\" : \"one\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"three\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"four\"" + NEW_LINE +
                "} ]>"));
    }

    @Test
    public void shouldFailVerificationSequenceWithLimitedReturnedRequestsViaConfiguration() {
        Integer originalMaximumNumberOfRequestToReturnInVerificationFailure = ConfigurationProperties.maximumNumberOfRequestToReturnInVerificationFailure();
        try {
            // given
            ConfigurationProperties.maximumNumberOfRequestToReturnInVerificationFailure(1);

            // when
            mockServerEventLog.add(
                new LogEntry()
                    .setHttpRequest(request("one"))
                    .setType(RECEIVED_REQUEST)
            );
            mockServerEventLog.add(
                new LogEntry()
                    .setHttpRequest(request("multi"))
                    .setType(RECEIVED_REQUEST)
            );
            mockServerEventLog.add(
                new LogEntry()
                    .setHttpRequest(request("three"))
                    .setType(RECEIVED_REQUEST)
            );
            mockServerEventLog.add(
                new LogEntry()
                    .setHttpRequest(request("multi"))
                    .setType(RECEIVED_REQUEST)
            );
            mockServerEventLog.add(
                new LogEntry()
                    .setHttpRequest(request("four"))
                    .setType(RECEIVED_REQUEST)
            );

            // then - next to each other
            assertThat(verify(
                    new VerificationSequence()
                        .withRequests(
                            request("multi"),
                            request("one")
                        )
                ),
                is("Request sequence not found, expected:<[ {" + NEW_LINE +
                    "  \"path\" : \"multi\"" + NEW_LINE +
                    "}, {" + NEW_LINE +
                    "  \"path\" : \"one\"" + NEW_LINE +
                    "} ]> but was not found, found 5 other requests"));
            assertThat(verify(
                    new VerificationSequence()
                        .withRequests(
                            request("four"),
                            request("multi")
                        )
                ),
                is("Request sequence not found, expected:<[ {" + NEW_LINE +
                    "  \"path\" : \"four\"" + NEW_LINE +
                    "}, {" + NEW_LINE +
                    "  \"path\" : \"multi\"" + NEW_LINE +
                    "} ]> but was not found, found 5 other requests"));
            // then - not next to each other
            assertThat(verify(
                    new VerificationSequence()
                        .withRequests(
                            request("three"),
                            request("one")
                        )
                ),
                is("Request sequence not found, expected:<[ {" + NEW_LINE +
                    "  \"path\" : \"three\"" + NEW_LINE +
                    "}, {" + NEW_LINE +
                    "  \"path\" : \"one\"" + NEW_LINE +
                    "} ]> but was not found, found 5 other requests"));
            assertThat(verify(
                    new VerificationSequence()
                        .withRequests(
                            request("four"),
                            request("one")
                        )
                ),
                is("Request sequence not found, expected:<[ {" + NEW_LINE +
                    "  \"path\" : \"four\"" + NEW_LINE +
                    "}, {" + NEW_LINE +
                    "  \"path\" : \"one\"" + NEW_LINE +
                    "} ]> but was not found, found 5 other requests"));
            assertThat(verify(
                    new VerificationSequence()
                        .withRequests(
                            request("four"),
                            request("three")
                        )
                ),
                is("Request sequence not found, expected:<[ {" + NEW_LINE +
                    "  \"path\" : \"four\"" + NEW_LINE +
                    "}, {" + NEW_LINE +
                    "  \"path\" : \"three\"" + NEW_LINE +
                    "} ]> but was not found, found 5 other requests"));
        } finally {
            ConfigurationProperties.maximumNumberOfRequestToReturnInVerificationFailure(originalMaximumNumberOfRequestToReturnInVerificationFailure);
        }
    }

    @Test
    public void shouldFailVerificationSequenceWithLimitedReturnedRequestsViaVerificationSequence() {
        // when
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("one"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("three"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("four"))
                .setType(RECEIVED_REQUEST)
        );

        // then - next to each other
        assertThat(verify(
                new VerificationSequence()
                    .withRequests(
                        request("multi"),
                        request("one")
                    )
                    .withMaximumNumberOfRequestToReturnInVerificationFailure(1)
            ),
            is("Request sequence not found, expected:<[ {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"one\"" + NEW_LINE +
                "} ]> but was not found, found 5 other requests"));
        assertThat(verify(
                new VerificationSequence()
                    .withRequests(
                        request("four"),
                        request("multi")
                    )
                    .withMaximumNumberOfRequestToReturnInVerificationFailure(1)
            ),
            is("Request sequence not found, expected:<[ {" + NEW_LINE +
                "  \"path\" : \"four\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "} ]> but was not found, found 5 other requests"));
        // then - not next to each other
        assertThat(verify(
                new VerificationSequence()
                    .withRequests(
                        request("three"),
                        request("one")
                    )
                    .withMaximumNumberOfRequestToReturnInVerificationFailure(1)
            ),
            is("Request sequence not found, expected:<[ {" + NEW_LINE +
                "  \"path\" : \"three\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"one\"" + NEW_LINE +
                "} ]> but was not found, found 5 other requests"));
        assertThat(verify(
                new VerificationSequence()
                    .withRequests(
                        request("four"),
                        request("one")
                    )
                    .withMaximumNumberOfRequestToReturnInVerificationFailure(1)
            ),
            is("Request sequence not found, expected:<[ {" + NEW_LINE +
                "  \"path\" : \"four\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"one\"" + NEW_LINE +
                "} ]> but was not found, found 5 other requests"));
        assertThat(verify(
                new VerificationSequence()
                    .withRequests(
                        request("four"),
                        request("three")
                    )
                    .withMaximumNumberOfRequestToReturnInVerificationFailure(1)
            ),
            is("Request sequence not found, expected:<[ {" + NEW_LINE +
                "  \"path\" : \"four\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"three\"" + NEW_LINE +
                "} ]> but was not found, found 5 other requests"));
    }

    @Test
    public void shouldFailVerificationSequenceWithTwoRequestsFirstIncorrect() {
        // when
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("one"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("three"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("four"))
                .setType(RECEIVED_REQUEST)
        );

        // then - next to each other
        assertThat(verify(
                new VerificationSequence()
                    .withRequests(
                        request("zero"),
                        request("multi")
                    )
            ),
            is("Request sequence not found, expected:<[ {" + NEW_LINE +
                "  \"path\" : \"zero\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "} ]> but was:<[ {" + NEW_LINE +
                "  \"path\" : \"one\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"three\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"four\"" + NEW_LINE +
                "} ]>"));
        assertThat(verify(
                new VerificationSequence()
                    .withRequests(
                        request("zero"),
                        request("three")
                    )
            ),
            is("Request sequence not found, expected:<[ {" + NEW_LINE +
                "  \"path\" : \"zero\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"three\"" + NEW_LINE +
                "} ]> but was:<[ {" + NEW_LINE +
                "  \"path\" : \"one\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"three\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"four\"" + NEW_LINE +
                "} ]>"));
        assertThat(verify(
                new VerificationSequence()
                    .withRequests(
                        request("zero"),
                        request("four")
                    )
            ),
            is("Request sequence not found, expected:<[ {" + NEW_LINE +
                "  \"path\" : \"zero\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"four\"" + NEW_LINE +
                "} ]> but was:<[ {" + NEW_LINE +
                "  \"path\" : \"one\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"three\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"four\"" + NEW_LINE +
                "} ]>"));
    }

    @Test
    public void shouldFailVerificationSequenceWithTwoRequestsSecondIncorrect() {
        // when
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("one"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("three"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("four"))
                .setType(RECEIVED_REQUEST)
        );

        // then - next to each other
        assertThat(verify(
                new VerificationSequence()
                    .withRequests(
                        request("one"),
                        request("five")
                    )
            ),
            is("Request sequence not found, expected:<[ {" + NEW_LINE +
                "  \"path\" : \"one\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"five\"" + NEW_LINE +
                "} ]> but was:<[ {" + NEW_LINE +
                "  \"path\" : \"one\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"three\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"four\"" + NEW_LINE +
                "} ]>"));
        assertThat(verify(
                new VerificationSequence()
                    .withRequests(
                        request("multi"),
                        request("five")
                    )
            ),
            is("Request sequence not found, expected:<[ {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"five\"" + NEW_LINE +
                "} ]> but was:<[ {" + NEW_LINE +
                "  \"path\" : \"one\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"three\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"four\"" + NEW_LINE +
                "} ]>"));
        assertThat(verify(
                new VerificationSequence()
                    .withRequests(
                        request("three"),
                        request("five")
                    )
            ),
            is("Request sequence not found, expected:<[ {" + NEW_LINE +
                "  \"path\" : \"three\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"five\"" + NEW_LINE +
                "} ]> but was:<[ {" + NEW_LINE +
                "  \"path\" : \"one\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"three\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"four\"" + NEW_LINE +
                "} ]>"));
    }

    @Test
    public void shouldFailVerificationSequenceWithThreeRequestsWrongOrder() {
        // when
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("one"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("three"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("four"))
                .setType(RECEIVED_REQUEST)
        );

        // then - next to each other
        assertThat(verify(
                new VerificationSequence()
                    .withRequests(
                        request("one"),
                        request("four"),
                        request("multi")
                    )
            ),
            is("Request sequence not found, expected:<[ {" + NEW_LINE +
                "  \"path\" : \"one\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"four\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "} ]> but was:<[ {" + NEW_LINE +
                "  \"path\" : \"one\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"three\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"four\"" + NEW_LINE +
                "} ]>"));
        assertThat(verify(
                new VerificationSequence()
                    .withRequests(
                        request("one"),
                        request("multi"),
                        request("one")
                    )
            ),
            is("Request sequence not found, expected:<[ {" + NEW_LINE +
                "  \"path\" : \"one\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"one\"" + NEW_LINE +
                "} ]> but was:<[ {" + NEW_LINE +
                "  \"path\" : \"one\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"three\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"four\"" + NEW_LINE +
                "} ]>"));
        // then - not next to each other
        assertThat(verify(
                new VerificationSequence()
                    .withRequests(
                        request("four"),
                        request("one"),
                        request("multi")
                    )
            ),
            is("Request sequence not found, expected:<[ {" + NEW_LINE +
                "  \"path\" : \"four\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"one\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "} ]> but was:<[ {" + NEW_LINE +
                "  \"path\" : \"one\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"three\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"four\"" + NEW_LINE +
                "} ]>"));
        assertThat(verify(
                new VerificationSequence()
                    .withRequests(
                        request("multi"),
                        request("three"),
                        request("one")
                    )
            ),
            is("Request sequence not found, expected:<[ {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"three\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"one\"" + NEW_LINE +
                "} ]> but was:<[ {" + NEW_LINE +
                "  \"path\" : \"one\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"three\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"four\"" + NEW_LINE +
                "} ]>"));

    }

    @Test
    public void shouldFailVerificationSequenceWithThreeRequestsDuplicateMissing() {
        // when
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("one"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("three"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(request("four"))
                .setType(RECEIVED_REQUEST)
        );

        // then
        assertThat(verify(
                new VerificationSequence()
                    .withRequests(
                        request("multi"),
                        request("multi"),
                        request("multi")
                    )
            ),
            is("Request sequence not found, expected:<[ {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "} ]> but was:<[ {" + NEW_LINE +
                "  \"path\" : \"one\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"three\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"multi\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"four\"" + NEW_LINE +
                "} ]>"));
    }

}
