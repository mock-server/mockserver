package org.mockserver.log;

import org.junit.AfterClass;
import org.junit.Test;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.verify.Verification;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.verify.Verification.verification;
import static org.mockserver.verify.VerificationTimes.atLeast;
import static org.mockserver.verify.VerificationTimes.exactly;

/**
 * @author jamesdbloom
 */
public class LogFilterRequestLogEntryVerificationTest {

    private static Scheduler scheduler = new Scheduler();

    @AfterClass
    public static void stopScheduler() {
        scheduler.shutdown();
    }

    @Test
    public void shouldPassVerificationWithNullRequest() {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");
        MockServerEventLog logFilter = new MockServerEventLog(mock(MockServerLogger.class), scheduler, true);

        // when
        logFilter.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(otherHttpRequest)
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );

        // then
        assertThat(logFilter.verify((Verification) null), is(""));
    }

    @Test
    public void shouldPassVerificationWithDefaultTimes() throws InterruptedException {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");
        MockServerEventLog logFilter = new MockServerEventLog(mock(MockServerLogger.class), scheduler, true);

        // when
        logFilter.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(otherHttpRequest)
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );
        MILLISECONDS.sleep(100);

        // then
        assertThat(logFilter.verify(
            verification()
                .withRequest(
                    new HttpRequest()
                        .withPath("some_path")
                )
            ),
            is(""));
        assertThat(logFilter.verify(
            verification()
                .withRequest(
                    new HttpRequest()
                        .withPath("some_other_path")
                )
            ),
            is(""));
    }

    @Test
    public void shouldPassVerificationWithAtLeastTwoTimes() throws InterruptedException {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");
        MockServerEventLog logFilter = new MockServerEventLog(mock(MockServerLogger.class), scheduler, true);

        // when
        logFilter.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(otherHttpRequest)
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );
        MILLISECONDS.sleep(100);

        // then
        assertThat(logFilter.verify(
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
        MockServerEventLog logFilter = new MockServerEventLog(mock(MockServerLogger.class), scheduler, true);

        // when
        logFilter.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(otherHttpRequest)
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );

        // then
        assertThat(logFilter.verify(
            verification()
                .withRequest(
                    new HttpRequest().withPath("some_non_matching_path")
                )
                .withTimes(atLeast(0))
            ),
            is(""));
    }

    @Test
    public void shouldPassVerificationWithExactlyTwoTimes() throws InterruptedException {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");
        MockServerEventLog logFilter = new MockServerEventLog(mock(MockServerLogger.class), scheduler, true);

        // when
        logFilter.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(otherHttpRequest)
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );
        MILLISECONDS.sleep(100);

        // then
        assertThat(logFilter.verify(
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
        MockServerEventLog logFilter = new MockServerEventLog(mock(MockServerLogger.class), scheduler, true);

        // when
        logFilter.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(otherHttpRequest)
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );

        // then
        assertThat(logFilter.verify(
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
        MockServerEventLog logFilter = new MockServerEventLog(mock(MockServerLogger.class), scheduler, true);

        // then
        assertThat(logFilter.verify((Verification) null), is(""));
    }

    @Test
    public void shouldFailVerificationWithDefaultTimes() throws InterruptedException {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");
        MockServerEventLog logFilter = new MockServerEventLog(mock(MockServerLogger.class), scheduler, true);

        // when
        logFilter.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(otherHttpRequest)
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );
        MILLISECONDS.sleep(100);

        // then
        assertThat(logFilter.verify(
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
    public void shouldFailVerificationWithAtLeastTwoTimes() throws InterruptedException {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");
        MockServerEventLog logFilter = new MockServerEventLog(mock(MockServerLogger.class), scheduler, true);

        // when
        logFilter.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(otherHttpRequest)
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );
        MILLISECONDS.sleep(100);

        // then
        assertThat(logFilter.verify(
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
    public void shouldFailVerificationWithExactTwoTimes() throws InterruptedException {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");
        MockServerEventLog logFilter = new MockServerEventLog(mock(MockServerLogger.class), scheduler, true);

        // when
        logFilter.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(otherHttpRequest)
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );
        MILLISECONDS.sleep(100);

        // then
        assertThat(logFilter.verify(
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
        MockServerEventLog logFilter = new MockServerEventLog(mock(MockServerLogger.class), scheduler, true);

        // then
        assertThat(logFilter.verify(
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
    public void shouldFailVerificationWithExactZeroTimes() throws InterruptedException {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");
        MockServerEventLog logFilter = new MockServerEventLog(mock(MockServerLogger.class), scheduler, true);

        // when
        logFilter.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(otherHttpRequest)
                .setType(RECEIVED_REQUEST)
        );
        logFilter.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );
        MILLISECONDS.sleep(100);

        // then
        assertThat(logFilter.verify(
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
    public void shouldFailVerificationWithNoInteractions() throws InterruptedException {
        // given
        HttpRequest httpRequest = new HttpRequest();
        MockServerEventLog logFilter = new MockServerEventLog(mock(MockServerLogger.class), scheduler, true);

        // when
        logFilter.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );
        MILLISECONDS.sleep(100);

        // then
        assertThat(logFilter.verify(
            verification()
                .withRequest(request())
                .withTimes(exactly(0))
            ),
            is("Request not found exactly 0 times, expected:<{ }> but was:<{ }>"));
    }
}
