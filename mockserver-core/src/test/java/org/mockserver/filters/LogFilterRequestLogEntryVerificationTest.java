package org.mockserver.filters;

import org.junit.Test;
import org.mockserver.log.model.RequestLogEntry;
import org.mockserver.logging.LoggingFormatter;
import org.mockserver.model.HttpRequest;
import org.mockserver.verify.Verification;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.verify.Verification.verification;
import static org.mockserver.verify.VerificationTimes.atLeast;
import static org.mockserver.verify.VerificationTimes.exactly;

/**
 * @author jamesdbloom
 */
public class LogFilterRequestLogEntryVerificationTest {

    @Test
    public void shouldPassVerificationWithNullRequest() {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");
        MockServerLog logFilter = new MockServerLog(mock(LoggingFormatter.class));

        // when
        logFilter.add(new RequestLogEntry(httpRequest));
        logFilter.add(new RequestLogEntry(otherHttpRequest));
        logFilter.add(new RequestLogEntry(httpRequest));

        // then
        assertThat(logFilter.verify((Verification) null), is(""));
    }

    @Test
    public void shouldPassVerificationWithDefaultTimes() {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");
        MockServerLog logFilter = new MockServerLog(mock(LoggingFormatter.class));

        // when
        logFilter.add(new RequestLogEntry(httpRequest));
        logFilter.add(new RequestLogEntry(otherHttpRequest));
        logFilter.add(new RequestLogEntry(httpRequest));

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
    public void shouldPassVerificationWithAtLeastTwoTimes() {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");
        MockServerLog logFilter = new MockServerLog(mock(LoggingFormatter.class));

        // when
        logFilter.add(new RequestLogEntry(httpRequest));
        logFilter.add(new RequestLogEntry(otherHttpRequest));
        logFilter.add(new RequestLogEntry(httpRequest));

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
        MockServerLog logFilter = new MockServerLog(mock(LoggingFormatter.class));

        // when
        logFilter.add(new RequestLogEntry(httpRequest));
        logFilter.add(new RequestLogEntry(otherHttpRequest));
        logFilter.add(new RequestLogEntry(httpRequest));

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
    public void shouldPassVerificationWithExactlyTwoTimes() {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");
        MockServerLog logFilter = new MockServerLog(mock(LoggingFormatter.class));

        // when
        logFilter.add(new RequestLogEntry(httpRequest));
        logFilter.add(new RequestLogEntry(otherHttpRequest));
        logFilter.add(new RequestLogEntry(httpRequest));

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
        MockServerLog logFilter = new MockServerLog(mock(LoggingFormatter.class));

        // when
        logFilter.add(new RequestLogEntry(httpRequest));
        logFilter.add(new RequestLogEntry(otherHttpRequest));
        logFilter.add(new RequestLogEntry(httpRequest));

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
        MockServerLog logFilter = new MockServerLog(mock(LoggingFormatter.class));

        // then
        assertThat(logFilter.verify((Verification) null), is(""));
    }

    @Test
    public void shouldFailVerificationWithDefaultTimes() {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");
        MockServerLog logFilter = new MockServerLog(mock(LoggingFormatter.class));

        // when
        logFilter.add(new RequestLogEntry(httpRequest));
        logFilter.add(new RequestLogEntry(otherHttpRequest));
        logFilter.add(new RequestLogEntry(httpRequest));

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
    public void shouldFailVerificationWithAtLeastTwoTimes() {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");
        MockServerLog logFilter = new MockServerLog(mock(LoggingFormatter.class));

        // when
        logFilter.add(new RequestLogEntry(httpRequest));
        logFilter.add(new RequestLogEntry(otherHttpRequest));
        logFilter.add(new RequestLogEntry(httpRequest));

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
    public void shouldFailVerificationWithExactTwoTimes() {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");
        MockServerLog logFilter = new MockServerLog(mock(LoggingFormatter.class));

        // when
        logFilter.add(new RequestLogEntry(httpRequest));
        logFilter.add(new RequestLogEntry(otherHttpRequest));
        logFilter.add(new RequestLogEntry(httpRequest));

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
        MockServerLog logFilter = new MockServerLog(mock(LoggingFormatter.class));

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
    public void shouldFailVerificationWithExactZeroTimes() {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");
        MockServerLog logFilter = new MockServerLog(mock(LoggingFormatter.class));

        // when
        logFilter.add(new RequestLogEntry(httpRequest));
        logFilter.add(new RequestLogEntry(otherHttpRequest));
        logFilter.add(new RequestLogEntry(httpRequest));

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
    public void shouldFailVerificationWithNoInteractions() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        MockServerLog logFilter = new MockServerLog(mock(LoggingFormatter.class));

        // when
        logFilter.add(new RequestLogEntry(httpRequest));

        // then
        assertThat(logFilter.verify(
                verification()
                        .withRequest(request())
                        .withTimes(exactly(0))
                ),
                is("Request not found exactly 0 times, expected:<{ }> but was:<{ }>"));
    }
}
