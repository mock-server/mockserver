package org.mockserver.log;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.verify.VerificationSequence;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class LogFilterRequestLogEntryVerificationSequenceTest {

    private MockServerLogger mockLogFormatter;
    private static Scheduler scheduler = new Scheduler();

    @Before
    public void setupTestFixture() {
        mockLogFormatter = mock(MockServerLogger.class);
    }

    @AfterClass
    public static void stopScheduler() {
        scheduler.shutdown();
    }

    @Test
    public void shouldPassVerificationWithNullRequest() {
        // given
        MockServerEventLog logFilter = new MockServerEventLog(mockLogFormatter, scheduler, true);

        // when
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("one"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("three"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("four"))
                .setType(RECEIVED_REQUEST)
        );

        // then
        assertThat(logFilter.verify((VerificationSequence) null), is(""));
    }

    @Test
    public void shouldPassVerificationSequenceWithNoRequest() {
        // given
        MockServerEventLog logFilter = new MockServerEventLog(mockLogFormatter, scheduler, true);

        // when
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("one"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("three"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("four"))
                .setType(RECEIVED_REQUEST)
        );

        // then
        assertThat(logFilter.verify(
            new VerificationSequence()
                .withRequests(

                )
            ),
            is(""));
    }

    @Test
    public void shouldPassVerificationSequenceWithOneRequest() throws InterruptedException {
        // given
        MockServerEventLog logFilter = new MockServerEventLog(mockLogFormatter, scheduler, true);

        // when
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("one"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("three"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("four"))
                .setType(RECEIVED_REQUEST)
        );
        MILLISECONDS.sleep(100);

        // then
        assertThat(logFilter.verify(
            new VerificationSequence()
                .withRequests(
                    request("one")
                )
            ),
            is(""));
        assertThat(logFilter.verify(
            new VerificationSequence()
                .withRequests(
                    request("multi")
                )
            ),
            is(""));
        assertThat(logFilter.verify(
            new VerificationSequence()
                .withRequests(
                    request("three")
                )
            ),
            is(""));
        assertThat(logFilter.verify(
            new VerificationSequence()
                .withRequests(
                    request("four")
                )
            ),
            is(""));
    }

    @Test
    public void shouldPassVerificationSequenceWithTwoRequests() throws InterruptedException {
        // given
        MockServerEventLog logFilter = new MockServerEventLog(mockLogFormatter, scheduler, true);

        // when
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("one"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("three"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("four"))
                .setType(RECEIVED_REQUEST)
        );
        MILLISECONDS.sleep(100);

        // then - next to each other
        assertThat(logFilter.verify(
            new VerificationSequence()
                .withRequests(
                    request("one"),
                    request("multi")
                )
            ),
            is(""));
        assertThat(logFilter.verify(
            new VerificationSequence()
                .withRequests(
                    request("multi"),
                    request("three")
                )
            ),
            is(""));
        assertThat(logFilter.verify(
            new VerificationSequence()
                .withRequests(
                    request("three"),
                    request("multi")
                )
            ),
            is(""));
        assertThat(logFilter.verify(
            new VerificationSequence()
                .withRequests(
                    request("multi"),
                    request("four")
                )
            ),
            is(""));
        // then - not next to each other
        assertThat(logFilter.verify(
            new VerificationSequence()
                .withRequests(
                    request("one"),
                    request("three")
                )
            ),
            is(""));
        assertThat(logFilter.verify(
            new VerificationSequence()
                .withRequests(
                    request("one"),
                    request("four")
                )
            ),
            is(""));
        assertThat(logFilter.verify(
            new VerificationSequence()
                .withRequests(
                    request("multi"),
                    request("multi")
                )
            ),
            is(""));
        assertThat(logFilter.verify(
            new VerificationSequence()
                .withRequests(
                    request("three"),
                    request("four")
                )
            ),
            is(""));
    }

    @Test
    public void shouldFailVerificationSequenceWithOneRequest() throws InterruptedException {
        // given
        MockServerEventLog logFilter = new MockServerEventLog(mockLogFormatter, scheduler, true);

        // when
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("one"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("three"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("four"))
                .setType(RECEIVED_REQUEST)
        );
        MILLISECONDS.sleep(100);

        // then
        assertThat(logFilter.verify(
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
    public void shouldFailVerificationSequenceWithTwoRequestsWrongOrder() throws InterruptedException {
        // given
        MockServerEventLog logFilter = new MockServerEventLog(mockLogFormatter, scheduler, true);

        // when
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("one"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("three"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("four"))
                .setType(RECEIVED_REQUEST)
        );
        MILLISECONDS.sleep(100);

        // then - next to each other
        assertThat(logFilter.verify(
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
        assertThat(logFilter.verify(
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
        assertThat(logFilter.verify(
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
        assertThat(logFilter.verify(
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
        assertThat(logFilter.verify(
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
    public void shouldFailVerificationSequenceWithTwoRequestsFirstIncorrect() throws InterruptedException {
        // given
        MockServerEventLog logFilter = new MockServerEventLog(mockLogFormatter, scheduler, true);

        // when
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("one"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("three"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("four"))
                .setType(RECEIVED_REQUEST)
        );
        MILLISECONDS.sleep(100);

        // then - next to each other
        assertThat(logFilter.verify(
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
        assertThat(logFilter.verify(
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
        assertThat(logFilter.verify(
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
    public void shouldFailVerificationSequenceWithTwoRequestsSecondIncorrect() throws InterruptedException {
        // given
        MockServerEventLog logFilter = new MockServerEventLog(mockLogFormatter, scheduler, true);

        // when
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("one"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("three"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("four"))
                .setType(RECEIVED_REQUEST)
        );
        MILLISECONDS.sleep(100);

        // then - next to each other
        assertThat(logFilter.verify(
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
        assertThat(logFilter.verify(
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
        assertThat(logFilter.verify(
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
    public void shouldFailVerificationSequenceWithThreeRequestsWrongOrder() throws InterruptedException {
        // given
        MockServerEventLog logFilter = new MockServerEventLog(mockLogFormatter, scheduler, true);

        // when
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("one"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("three"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("four"))
                .setType(RECEIVED_REQUEST)
        );
        MILLISECONDS.sleep(100);

        // then - next to each other
        assertThat(logFilter.verify(
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
        assertThat(logFilter.verify(
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
        assertThat(logFilter.verify(
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
        assertThat(logFilter.verify(
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
    public void shouldFailVerificationSequenceWithThreeRequestsDuplicateMissing() throws InterruptedException {
        // given
        MockServerEventLog logFilter = new MockServerEventLog(mockLogFormatter, scheduler, true);

        // when
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("one"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("three"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("multi"))
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(request("four"))
                .setType(RECEIVED_REQUEST)
        );
        MILLISECONDS.sleep(100);

        // then
        assertThat(logFilter.verify(
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
