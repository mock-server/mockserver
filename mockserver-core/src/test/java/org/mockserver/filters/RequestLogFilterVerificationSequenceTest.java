package org.mockserver.filters;

import org.junit.Test;
import org.mockserver.verify.VerificationSequence;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class RequestLogFilterVerificationSequenceTest {

    @Test
    public void shouldPassVerificationWithNullRequest() {
        // given
        RequestLogFilter requestLogFilter = new RequestLogFilter();

        // when
        requestLogFilter.onRequest(request("one"));
        requestLogFilter.onRequest(request("multi"));
        requestLogFilter.onRequest(request("three"));
        requestLogFilter.onRequest(request("multi"));
        requestLogFilter.onRequest(request("four"));

        // then
        assertThat(requestLogFilter.verify((VerificationSequence) null), is(""));
    }

    @Test
    public void shouldPassVerificationSequenceWithNoRequest() {
        // given
        RequestLogFilter requestLogFilter = new RequestLogFilter();

        // when
        requestLogFilter.onRequest(request("one"));
        requestLogFilter.onRequest(request("multi"));
        requestLogFilter.onRequest(request("three"));
        requestLogFilter.onRequest(request("multi"));
        requestLogFilter.onRequest(request("four"));

        // then
        assertThat(requestLogFilter.verify(
                        new VerificationSequence()
                                .withRequests(

                                )
                ),
                is(""));
    }

    @Test
    public void shouldPassVerificationSequenceWithOneRequest() {
        // given
        RequestLogFilter requestLogFilter = new RequestLogFilter();

        // when
        requestLogFilter.onRequest(request("one"));
        requestLogFilter.onRequest(request("multi"));
        requestLogFilter.onRequest(request("three"));
        requestLogFilter.onRequest(request("multi"));
        requestLogFilter.onRequest(request("four"));

        // then
        assertThat(requestLogFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("one")
                                )
                ),
                is(""));
        assertThat(requestLogFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("multi")
                                )
                ),
                is(""));
        assertThat(requestLogFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("three")
                                )
                ),
                is(""));
        assertThat(requestLogFilter.verify(
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
        RequestLogFilter requestLogFilter = new RequestLogFilter();

        // when
        requestLogFilter.onRequest(request("one"));
        requestLogFilter.onRequest(request("multi"));
        requestLogFilter.onRequest(request("three"));
        requestLogFilter.onRequest(request("multi"));
        requestLogFilter.onRequest(request("four"));

        // then - next to each other
        assertThat(requestLogFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("one"),
                                        request("multi")
                                )
                ),
                is(""));
        assertThat(requestLogFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("multi"),
                                        request("three")
                                )
                ),
                is(""));
        assertThat(requestLogFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("three"),
                                        request("multi")
                                )
                ),
                is(""));
        assertThat(requestLogFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("multi"),
                                        request("four")
                                )
                ),
                is(""));
        // then - not next to each other
        assertThat(requestLogFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("one"),
                                        request("three")
                                )
                ),
                is(""));
        assertThat(requestLogFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("one"),
                                        request("four")
                                )
                ),
                is(""));
        assertThat(requestLogFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("multi"),
                                        request("multi")
                                )
                ),
                is(""));
        assertThat(requestLogFilter.verify(
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
        RequestLogFilter requestLogFilter = new RequestLogFilter();

        // when
        requestLogFilter.onRequest(request("one"));
        requestLogFilter.onRequest(request("multi"));
        requestLogFilter.onRequest(request("three"));
        requestLogFilter.onRequest(request("multi"));
        requestLogFilter.onRequest(request("four"));

        // then
        assertThat(requestLogFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("five")
                                )
                ),
                is("Request sequence not found, expected:<[ {" + System.getProperty("line.separator") +
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
        RequestLogFilter requestLogFilter = new RequestLogFilter();

        // when
        requestLogFilter.onRequest(request("one"));
        requestLogFilter.onRequest(request("multi"));
        requestLogFilter.onRequest(request("three"));
        requestLogFilter.onRequest(request("multi"));
        requestLogFilter.onRequest(request("four"));

        // then - next to each other
        assertThat(requestLogFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("multi"),
                                        request("one")
                                )
                ),
                is("Request sequence not found, expected:<[ {" + System.getProperty("line.separator") +
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
        assertThat(requestLogFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("four"),
                                        request("multi")
                                )
                ),
                is("Request sequence not found, expected:<[ {" + System.getProperty("line.separator") +
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
        assertThat(requestLogFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("three"),
                                        request("one")
                                )
                ),
                is("Request sequence not found, expected:<[ {" + System.getProperty("line.separator") +
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
        assertThat(requestLogFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("four"),
                                        request("one")
                                )
                ),
                is("Request sequence not found, expected:<[ {" + System.getProperty("line.separator") +
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
        assertThat(requestLogFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("four"),
                                        request("three")
                                )
                ),
                is("Request sequence not found, expected:<[ {" + System.getProperty("line.separator") +
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
        RequestLogFilter requestLogFilter = new RequestLogFilter();

        // when
        requestLogFilter.onRequest(request("one"));
        requestLogFilter.onRequest(request("multi"));
        requestLogFilter.onRequest(request("three"));
        requestLogFilter.onRequest(request("multi"));
        requestLogFilter.onRequest(request("four"));

        // then - next to each other
        assertThat(requestLogFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("zero"),
                                        request("multi")
                                )
                ),
                is("Request sequence not found, expected:<[ {" + System.getProperty("line.separator") +
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
        assertThat(requestLogFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("zero"),
                                        request("three")
                                )
                ),
                is("Request sequence not found, expected:<[ {" + System.getProperty("line.separator") +
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
        assertThat(requestLogFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("zero"),
                                        request("four")
                                )
                ),
                is("Request sequence not found, expected:<[ {" + System.getProperty("line.separator") +
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
        RequestLogFilter requestLogFilter = new RequestLogFilter();

        // when
        requestLogFilter.onRequest(request("one"));
        requestLogFilter.onRequest(request("multi"));
        requestLogFilter.onRequest(request("three"));
        requestLogFilter.onRequest(request("multi"));
        requestLogFilter.onRequest(request("four"));

        // then - next to each other
        assertThat(requestLogFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("one"),
                                        request("five")
                                )
                ),
                is("Request sequence not found, expected:<[ {" + System.getProperty("line.separator") +
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
        assertThat(requestLogFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("multi"),
                                        request("five")
                                )
                ),
                is("Request sequence not found, expected:<[ {" + System.getProperty("line.separator") +
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
        assertThat(requestLogFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("three"),
                                        request("five")
                                )
                ),
                is("Request sequence not found, expected:<[ {" + System.getProperty("line.separator") +
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
        RequestLogFilter requestLogFilter = new RequestLogFilter();

        // when
        requestLogFilter.onRequest(request("one"));
        requestLogFilter.onRequest(request("multi"));
        requestLogFilter.onRequest(request("three"));
        requestLogFilter.onRequest(request("multi"));
        requestLogFilter.onRequest(request("four"));

        // then - next to each other
        assertThat(requestLogFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("one"),
                                        request("four"),
                                        request("multi")
                                )
                ),
                is("Request sequence not found, expected:<[ {" + System.getProperty("line.separator") +
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
        assertThat(requestLogFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("one"),
                                        request("multi"),
                                        request("one")
                                )
                ),
                is("Request sequence not found, expected:<[ {" + System.getProperty("line.separator") +
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
        assertThat(requestLogFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("four"),
                                        request("one"),
                                        request("multi")
                                )
                ),
                is("Request sequence not found, expected:<[ {" + System.getProperty("line.separator") +
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
        assertThat(requestLogFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("multi"),
                                        request("three"),
                                        request("one")
                                )
                ),
                is("Request sequence not found, expected:<[ {" + System.getProperty("line.separator") +
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
        RequestLogFilter requestLogFilter = new RequestLogFilter();

        // when
        requestLogFilter.onRequest(request("one"));
        requestLogFilter.onRequest(request("multi"));
        requestLogFilter.onRequest(request("three"));
        requestLogFilter.onRequest(request("multi"));
        requestLogFilter.onRequest(request("four"));

        // then
        assertThat(requestLogFilter.verify(
                        new VerificationSequence()
                                .withRequests(
                                        request("multi"),
                                        request("multi"),
                                        request("multi")
                                )
                ),
                is("Request sequence not found, expected:<[ {" + System.getProperty("line.separator") +
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
