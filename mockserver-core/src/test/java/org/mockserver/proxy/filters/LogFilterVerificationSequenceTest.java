package org.mockserver.proxy.filters;

import org.junit.Test;
import org.mockserver.filters.LogFilter;
import org.mockserver.verify.VerificationSequence;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class LogFilterVerificationSequenceTest {

    @Test
    public void shouldPassVerificationWithNullRequest() {
        // given
        LogFilter logFilter = new LogFilter();

        // when
        logFilter.onRequest(request("one"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("three"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("four"));

        // then
        assertThat(logFilter.verify((VerificationSequence) null), is(""));
    }

    @Test
    public void shouldPassVerificationSequenceWithNoRequest() {
        // given
        LogFilter logFilter = new LogFilter();

        // when
        logFilter.onRequest(request("one"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("three"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("four"));

        // then
        assertThat(logFilter.verify(
                        new VerificationSequence()
                                .withRequests(

                                )
                ),
                is(""));
    }

    @Test
    public void shouldPassVerificationSequenceWithOneRequest() {
        // given
        LogFilter logFilter = new LogFilter();

        // when
        logFilter.onRequest(request("one"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("three"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("four"));

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
    public void shouldPassVerificationSequenceWithTwoRequests() {
        // given
        LogFilter logFilter = new LogFilter();

        // when
        logFilter.onRequest(request("one"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("three"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("four"));

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
    public void shouldFailVerificationSequenceWithOneRequest() {
        // given
        LogFilter logFilter = new LogFilter();

        // when
        logFilter.onRequest(request("one"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("three"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("four"));

        // then
        assertThat(logFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("five")
                                )
                ),
                is("Request not found {" + System.getProperty("line.separator") +
                        "  \"path\" : \"five\"" + System.getProperty("line.separator") +
                        "} expected:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"five\"" + System.getProperty("line.separator") +
                        "} ]> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "} ]>"));
    }

    @Test
    public void shouldFailVerificationSequenceWithTwoRequestsWrongOrder() {
        // given
        LogFilter logFilter = new LogFilter();

        // when
        logFilter.onRequest(request("one"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("three"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("four"));

        // then - next to each other
        assertThat(logFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("multi"),
                                        request("one")
                                )
                ),
                is("Request not found {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "} expected:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "} ]> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "} ]>"));
        assertThat(logFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("four"),
                                        request("multi")
                                )
                ),
                is("Request not found {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "} expected:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "} ]> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "} ]>"));
        // then - not next to each other
        assertThat(logFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("three"),
                                        request("one")
                                )
                ),
                is("Request not found {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "} expected:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "} ]> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "} ]>"));
        assertThat(logFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("four"),
                                        request("one")
                                )
                ),
                is("Request not found {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "} expected:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "} ]> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "} ]>"));
        assertThat(logFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("four"),
                                        request("three")
                                )
                ),
                is("Request not found {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "} expected:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "} ]> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "} ]>"));
    }

    @Test
    public void shouldFailVerificationSequenceWithTwoRequestsFirstIncorrect() {
        // given
        LogFilter logFilter = new LogFilter();

        // when
        logFilter.onRequest(request("one"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("three"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("four"));

        // then - next to each other
        assertThat(logFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("zero"),
                                        request("multi")
                                )
                ),
                is("Request not found {" + System.getProperty("line.separator") +
                        "  \"path\" : \"zero\"" + System.getProperty("line.separator") +
                        "} expected:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"zero\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "} ]> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "} ]>"));
        assertThat(logFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("zero"),
                                        request("three")
                                )
                ),
                is("Request not found {" + System.getProperty("line.separator") +
                        "  \"path\" : \"zero\"" + System.getProperty("line.separator") +
                        "} expected:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"zero\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "} ]> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "} ]>"));
        assertThat(logFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("zero"),
                                        request("four")
                                )
                ),
                is("Request not found {" + System.getProperty("line.separator") +
                        "  \"path\" : \"zero\"" + System.getProperty("line.separator") +
                        "} expected:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"zero\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "} ]> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "} ]>"));
    }

    @Test
    public void shouldFailVerificationSequenceWithTwoRequestsSecondIncorrect() {
        // given
        LogFilter logFilter = new LogFilter();

        // when
        logFilter.onRequest(request("one"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("three"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("four"));

        // then - next to each other
        assertThat(logFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("one"),
                                        request("five")
                                )
                ),
                is("Request not found {" + System.getProperty("line.separator") +
                        "  \"path\" : \"five\"" + System.getProperty("line.separator") +
                        "} expected:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"five\"" + System.getProperty("line.separator") +
                        "} ]> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "} ]>"));
        assertThat(logFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("multi"),
                                        request("five")
                                )
                ),
                is("Request not found {" + System.getProperty("line.separator") +
                        "  \"path\" : \"five\"" + System.getProperty("line.separator") +
                        "} expected:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"five\"" + System.getProperty("line.separator") +
                        "} ]> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "} ]>"));
        assertThat(logFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("three"),
                                        request("five")
                                )
                ),
                is("Request not found {" + System.getProperty("line.separator") +
                        "  \"path\" : \"five\"" + System.getProperty("line.separator") +
                        "} expected:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"five\"" + System.getProperty("line.separator") +
                        "} ]> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "} ]>"));
    }

    @Test
    public void shouldFailVerificationSequenceWithThreeRequestsWrongOrder() {
        // given
        LogFilter logFilter = new LogFilter();

        // when
        logFilter.onRequest(request("one"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("three"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("four"));

        // then - next to each other
        assertThat(logFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("one"),
                                        request("four"),
                                        request("multi")
                                )
                ),
                is("Request not found {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "} expected:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "} ]> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "} ]>"));
        assertThat(logFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("one"),
                                        request("multi"),
                                        request("one")
                                )
                ),
                is("Request not found {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "} expected:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "} ]> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
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
                is("Request not found {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "} expected:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "} ]> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "} ]>"));
        assertThat(logFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("multi"),
                                        request("three"),
                                        request("one")
                                )
                ),
                is("Request not found {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "} expected:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "} ]> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "} ]>"));

    }

    @Test
    public void shouldFailVerificationSequenceWithThreeRequestsDuplicateMissing() {
        // given
        LogFilter logFilter = new LogFilter();

        // when
        logFilter.onRequest(request("one"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("three"));
        logFilter.onRequest(request("multi"));
        logFilter.onRequest(request("four"));

        // then
        assertThat(logFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("multi"),
                                        request("multi"),
                                        request("multi")
                                )
                ),
                is("Request not found {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "} expected:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "} ]> but was:<[ {" + System.getProperty("line.separator") +
                        "  \"path\" : \"one\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"three\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"multi\"" + System.getProperty("line.separator") +
                        "}, {" + System.getProperty("line.separator") +
                        "  \"path\" : \"four\"" + System.getProperty("line.separator") +
                        "} ]>"));
    }

}
